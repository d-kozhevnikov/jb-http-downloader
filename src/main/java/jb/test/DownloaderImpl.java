package jb.test;

import jb.test.util.Event;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URLConnection;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class DownloaderImpl implements Downloader {

    private static final int BUFFER_SIZE = 1024;

    private enum State {
        NOT_STARTED, RUNNING, STOPPED
    }

    private ThreadPoolExecutor executor;

    private volatile State runningState = State.NOT_STARTED;
    private final Object stateLock = new Object();

    // parts of synchronized state
    private final HashMap<Runnable, Future<?>> activeRequests = new HashMap<>();
    private final Deque<DownloadingTask> idleTasks = new ArrayDeque<>();
    private int nThreads;

    private class TaskData {
        public final ProgressData progress = new ProgressData();
        public final boolean acceptsRanges;
        public final Optional<Long> length;
        public final TreeSet<Integer> downloadedChunksStart = new TreeSet<>();

        private TaskData(boolean acceptsRanges, Optional<Long> length) {
            this.acceptsRanges = acceptsRanges;
            this.length = length;
        }
    }

    private final HashMap<DownloadingTask, TaskData> taskDatas = new HashMap<>();

    private final Event changedEvent = new Event();

    @Override
    public void close() {
        synchronized (stateLock) {
            runningState = State.STOPPED;
            awaitTermination();
        }
    }

    @Override
    public void run(Collection<? extends DownloadingTask> tasks, int nThreads) throws InterruptedException {
        synchronized (stateLock) {
            if (runningState != State.NOT_STARTED)
                throw new IllegalStateException("Can only be ran once"); // todo
            runningState = State.RUNNING;
        }

        executor = new ThreadPoolExecutor(nThreads, nThreads, Long.MAX_VALUE, TimeUnit.NANOSECONDS, new LinkedBlockingDeque<>());
        this.nThreads = nThreads;

        // todo: it should be tasks too
        for (DownloadingTask task : tasks) {
            try {
                HttpURLConnection conn = (HttpURLConnection) task.getURL().openConnection();
                conn.setRequestMethod("HEAD");
                int respCode = conn.getResponseCode();
                if (respCode / 100 != 2)
                    throw new IOException(String.format("Can't reach %s (HTTP response code %d)", task.getURL(), respCode));

                // Hangs using https(?) (http://bugs.java.com/bugdatabase/view_bug.do?bug_id=6680192)
                // boolean acceptsRanges = conn.getHeaderField("Accept-Ranges").equals("bytes");

                Optional<Long> contentLength = Optional.empty();
                long length = conn.getContentLengthLong();
                if (length >= 0)
                    contentLength = Optional.of(length);

                taskDatas.put(task, new TaskData(isAcceptingRanges(conn), contentLength));

                idleTasks.add(task);
            } catch (IOException e) {
                task.onFailure(e);
            }
        }

        while (runningState != State.STOPPED && update()) {
            changedEvent.waitFor();
        }

        synchronized (stateLock) {
            awaitTermination();
        }
    }

    private boolean isAcceptingRanges(HttpURLConnection conn) {
        Map<String, List<String>> fields = conn.getHeaderFields();
        List<String> values = fields.get("Accept-Ranges");
        return values != null && !values.isEmpty() && values.iterator().next().equals("bytes");
    }

    @Override
    public Progress getProgress() {
        // Note: don't care about any changes in progress while collecting it
        // it would represent the valid state at some point in time anyways

        long downloaded = 0;
        Optional<Long> sum = Optional.of(0L);
        for (TaskData d : taskDatas.values()) {
            downloaded += d.progress.getDownloadedBytes();

            if (sum.isPresent()) {
                Optional<Long> total = d.progress.getTotalBytes();
                if (total.isPresent())
                    sum = Optional.of(sum.get() + total.get());
                else
                    sum = Optional.empty();
            }
        }

        return new Progress(downloaded, sum);
    }

    @Override
    public synchronized void setThreadsCount(int nThreads) {
        if (nThreads < 1)
            throw new IllegalArgumentException("nThreads < 1");

        this.nThreads = nThreads;
        if (executor != null) {
            executor.setCorePoolSize(nThreads);
            executor.setMaximumPoolSize(nThreads);
        }
        changedEvent.fire();
    }

    private synchronized boolean update() {
        if (idleTasks.isEmpty() && activeRequests.isEmpty())
            return false;

        while (activeRequests.size() > nThreads)
            cancelRequest();

        while (activeRequests.size() < nThreads) {
            Runnable r = getNextRequest();
            if (r == null)
                break;
            addRequest(r);
        }

        return true;
    }

    private void processTask(DownloadingTask task, ProgressData progressData, Runnable req) {
        try {
            URLConnection conn = task.getURL().openConnection();

            long length = conn.getContentLengthLong();
            Optional<Long> lengthOpt = Optional.empty();
            if (length >= 0) {
                progressData.setTotalBytes(length);
                lengthOpt = Optional.of(length);
            }
            task.onStart(lengthOpt);

            InputStream remoteContentStream = conn.getInputStream();
            byte[] buffer = new byte[BUFFER_SIZE];
            int bytesRead;
            while ((bytesRead = remoteContentStream.read(buffer)) != -1) {
                if (runningState != State.RUNNING) {
                    remoteContentStream.close();
                    progressData.resetDownloadedBytes();
                    task.onDiscard();
                    onTaskFinished(task, req, false);
                    return;
                } else if (Thread.interrupted()) {
                    remoteContentStream.close();
                    progressData.resetDownloadedBytes();
                    task.onCancel();
                    onTaskFinished(task, req, true);
                    return;
                }
                progressData.addDownloadedBytes(bytesRead);
                task.onChunkReceived(ByteBuffer.wrap(buffer, 0, bytesRead).asReadOnlyBuffer(),
                        progressData.getDownloadedBytes() - bytesRead);
                Thread.yield();
            }

            progressData.setTotalBytes(progressData.getDownloadedBytes());
            task.onSuccess();
            onTaskFinished(task, req, false);
        } catch (IOException e) {
            task.onFailure(e);
            onTaskFinished(task, req, false);
        }
    }

    private void addRequest(Runnable r) {
        activeRequests.put(r, executor.submit(r));
    }

    private Runnable getNextRequest() {
        if (idleTasks.isEmpty())
            return null;

        DownloadingTask task = idleTasks.remove();
        TaskData taskData = taskDatas.get(task);
        return new Runnable() {
            @Override
            public void run() {
                processTask(task, taskData.progress, this);
            }
        };
    }

    private void cancelRequest() {
        Map.Entry<Runnable, Future<?>> res = activeRequests.entrySet().iterator().next();
        if (res != null) {
            res.getValue().cancel(true);
            activeRequests.remove(res.getKey());
        }
    }

    private synchronized void onTaskFinished(DownloadingTask task, Runnable req, boolean cancelled) {
        if (cancelled)
            idleTasks.add(task);
        activeRequests.remove(req);
        changedEvent.fire();
    }

    private void awaitTermination() { // fixme: throw InterruptedException
        if (executor == null)
            return;

        executor.shutdownNow();
        try {
            executor.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
