package jb.test;

import jb.test.util.Event;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLConnection;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.*;

class ProgressData {
    private volatile long downloadedBytes = 0;
    private volatile Optional<Long> totalBytes = Optional.empty();

    public long getDownloadedBytes() {
        return downloadedBytes;
    }

    public Optional<Long> getTotalBytes() {
        return totalBytes;
    }

    public void addDownloadedBytes(long add) {
        downloadedBytes += add;
        assert !totalBytes.isPresent() || totalBytes.get() >= downloadedBytes;
    }

    public void resetDownloadedBytes() {
        downloadedBytes = 0;
        assert !totalBytes.isPresent() || totalBytes.get() >= downloadedBytes;
    }

    public void setTotalBytes(long val) {
        totalBytes = Optional.of(val);
        assert totalBytes.get() >= downloadedBytes;
    }
}


public class DownloaderImpl implements Downloader {

    private static final int BUFFER_SIZE = 1024;

    private class FutureRequest {
        public Future<?> future;
    }

    private enum State {
        NOT_STARTED, RUNNING, STOPPED
    }

    private ThreadPoolExecutor executor;

    private volatile State runningState = State.NOT_STARTED;
    private final Object stateLock = new Object();

    // parts of synchronized state
    private final HashSet<FutureRequest> activeRequests = new HashSet<>();
    private final Deque<URITask> idleTasks = new ArrayDeque<>();
    private int nThreads;

    private final HashMap<URITask, ProgressData> progress = new HashMap<>();

    private final Event changedEvent = new Event();


    @Override
    public void close() throws IOException {
        synchronized (stateLock) {
            runningState = State.STOPPED;
        }
    }

    @Override
    public void run(Collection<? extends URITask> tasks, int nThreads) throws InterruptedException {
        synchronized (stateLock) {
            if (runningState != State.NOT_STARTED)
                throw new IllegalStateException("Can only be ran once"); // todo
            runningState = State.RUNNING;
        }

        executor = new ThreadPoolExecutor(nThreads, nThreads, Long.MAX_VALUE, TimeUnit.NANOSECONDS, new LinkedBlockingDeque<>());
        this.nThreads = nThreads;

        // todo: it should be tasks too
        for (URITask task : tasks) {
            ProgressData progressData = new ProgressData();
            progress.put(task, progressData);
            try {
                URLConnection conn = task.getURI().toURL().openConnection();
                long length = conn.getContentLengthLong();
                if (length >= 0)
                    progressData.setTotalBytes(length);


                idleTasks.add(task);
            } catch (IOException e) {
                task.onFailure(e);
            }
        }

        while (runningState != State.STOPPED && update()) {
            changedEvent.waitFor();
        }

        executor.shutdownNow();
        try {
            executor.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public Progress getProgress() {
        // Note: don't care about any changes in progress while collecting it
        // it would represent the valid state at some point in time anyways

        long downloaded = 0;
        Optional<Long> sum = Optional.of(0L);
        for (ProgressData d : progress.values()) {
            downloaded += d.getDownloadedBytes();

            if (sum.isPresent()) {
                Optional<Long> total = d.getTotalBytes();
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

        while (activeRequests.size() < nThreads && !idleTasks.isEmpty()) {
            URITask nextTask = idleTasks.remove();
            addRequest(nextTask);
        }

        return true;
    }

    private void addRequest(URITask task) {
        FutureRequest req = new FutureRequest();
        activeRequests.add(req);

        ProgressData progressData = progress.get(task);
        req.future = executor.submit(() -> {
            try {
                URLConnection conn = task.getURI().toURL().openConnection();

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
                    }
                    else if (Thread.interrupted()) {
                        remoteContentStream.close();
                        progressData.resetDownloadedBytes();
                        task.onCancel();
                        onTaskFinished(task, req, true);
                        return;
                    }
                    progressData.addDownloadedBytes(bytesRead);
                    task.onChunkReceived(ByteBuffer.wrap(buffer, 0, bytesRead).asReadOnlyBuffer());
                    Thread.yield();
                }

                progressData.setTotalBytes(progressData.getDownloadedBytes());
                task.onSuccess();
                onTaskFinished(task, req, false);
            } catch (IOException e) {
                task.onFailure(e);
                onTaskFinished(task, req, false);
            }
        });
    }

    private void cancelRequest() {
        FutureRequest res;
        res = activeRequests.iterator().next();
        if (res != null)
            res.future.cancel(true);
        activeRequests.remove(res);
    }

    private synchronized void onTaskFinished(URITask task, FutureRequest req, boolean cancelled) {
        if (cancelled)
            idleTasks.add(task);
        activeRequests.remove(req);
        changedEvent.fire();
    }
}
