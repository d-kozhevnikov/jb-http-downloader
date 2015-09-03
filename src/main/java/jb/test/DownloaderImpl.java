package jb.test;

import jb.test.util.Event;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLConnection;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.*;

public class DownloaderImpl implements Downloader {

    private static final int BUFFER_SIZE = 1024;

    private class FutureRequest {
        public Future<?> future;
    }

    private class ProgressData {
        public long downloadedBytes = 0;
        public Optional<Long> totalBytes = Optional.empty();
    }

    private ThreadPoolExecutor executor;

    // parts of synchronized state
    private final HashSet<FutureRequest> activeRequests = new HashSet<>();
    private final Deque<URITask> idleTasks = new ArrayDeque<>();
    private final HashMap<URITask, ProgressData> progress = new HashMap<>();
    private int nThreads;
    private final Event changedEvent = new Event();
    private volatile boolean stopped = true;

    public DownloaderImpl() {
    }

    @Override
    public void close() throws IOException {
        stopped = true;
        idleTasks.clear();
        changedEvent.fire();

        if (executor != null) {
            executor.shutdownNow();
            try {
                executor.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        progress.clear();
        executor = null;
    }

    @Override
    public void run(Collection<? extends URITask> tasks, int nThreads) throws InterruptedException {
        stopped = false;
        executor = new ThreadPoolExecutor(nThreads, nThreads, Long.MAX_VALUE, TimeUnit.NANOSECONDS, new LinkedBlockingDeque<>());
        this.nThreads = nThreads;

        for (URITask task : tasks) {
            progress.put(task, new ProgressData());
            try {
                URLConnection conn = task.getURI().toURL().openConnection();
                long length = conn.getContentLengthLong();
                if (length >= 0)
                    setTotalBytes(task, length);
                idleTasks.add(task);
            } catch (Exception e) {
                setTotalBytes(task, 0);
                task.onFailure(e);
            }
        }

        while (true) {
            if (!update())
                break;

            changedEvent.waitFor();
        }
    }

    @Override
    public synchronized Progress getProgress() {
        long downloaded = 0;
        Optional<Long> total = Optional.of(0L);
        for (ProgressData d : progress.values()) {
            downloaded += d.downloadedBytes;

            if (total.isPresent()) {
                if (d.totalBytes.isPresent())
                    total = Optional.of(total.get() + d.totalBytes.get());
                else
                    total = Optional.empty();
            }
        }

        return new Progress(downloaded, total);
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

        req.future = executor.submit(() -> {

            try {
                URLConnection conn = task.getURI().toURL().openConnection();

                long length = conn.getContentLengthLong();
                Optional<Long> lengthOpt = Optional.empty();
                if (length >= 0) {
                    setTotalBytes(task, length);
                    lengthOpt = Optional.of(length);
                }
                task.onStart(lengthOpt);

                InputStream remoteContentStream = conn.getInputStream();
                byte[] buffer = new byte[BUFFER_SIZE];
                int bytesRead;
                while ((bytesRead = remoteContentStream.read(buffer)) != -1) {
                    if (Thread.interrupted()) {
                        remoteContentStream.close();
                        resetProgress(task);
                        task.onCancel();
                        onTaskFinished(task, req, true);
                        return;
                    }

                    addProgress(task, bytesRead);
                    task.onChunkReceived(ByteBuffer.wrap(buffer, 0, bytesRead).asReadOnlyBuffer());
                    Thread.yield();
                }

                completeProgress(task);
                task.onSuccess();
                onTaskFinished(task, req, false);
            } catch (IOException e) {
                setTotalBytes(task, 0);
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

    private synchronized void setTotalBytes(URITask task, long nBytes) {
        progress.get(task).totalBytes = Optional.of(nBytes);
    }

    private synchronized void addProgress(URITask task, long nBytes) {
        progress.get(task).downloadedBytes += nBytes;
    }

    private synchronized void resetProgress(URITask task) {
        progress.get(task).downloadedBytes = 0;
    }

    private synchronized void completeProgress(URITask task) {
        ProgressData p = progress.get(task);
        p.totalBytes = Optional.of(p.downloadedBytes);
    }

    private synchronized void onTaskFinished(URITask task, FutureRequest req, boolean cancelled) {
        if (cancelled && !stopped)
            idleTasks.add(task);
        activeRequests.remove(req);
        changedEvent.fire();
    }
}
