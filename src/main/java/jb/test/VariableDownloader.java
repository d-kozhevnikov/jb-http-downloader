package jb.test;

import jb.test.util.Event;
import org.apache.http.client.fluent.Async;
import org.apache.http.client.fluent.Content;
import org.apache.http.client.fluent.Request;
import org.apache.http.concurrent.FutureCallback;

import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.HashSet;
import java.util.concurrent.*;

public class VariableDownloader implements Downloader {
    private class ProgressEvent {
        public final Runnable process;
        public final boolean incrementTotalProgress;

        private ProgressEvent(Runnable process, boolean incrementTotalProgress) {
            this.incrementTotalProgress = incrementTotalProgress;
            this.process = process;
        }
    }

    private class FutureRequest {
        public Future<Content> future;
    }

    // parts of synchronized state
    private final HashSet<FutureRequest> activeRequests = new HashSet<>();
    private final Deque<URLTask> idleTasks = new ArrayDeque<>();
    private int nThreads;
    private final Event changedEvent = new Event();
    private final ThreadPoolExecutor threadPoolExecutor;

    private final BlockingDeque<ProgressEvent> progressEvents = new LinkedBlockingDeque<>();
    private int doneTasksCount = 0;

    public VariableDownloader(int nThreads) {
        this.nThreads = nThreads;
        threadPoolExecutor = new ThreadPoolExecutor(this.nThreads, this.nThreads, Integer.MAX_VALUE, TimeUnit.SECONDS, new LinkedBlockingDeque<>());
    }

    @Override
    public void run(Collection<? extends URLTask> tasks, ProgressCallback progressCallback) throws InterruptedException {
        idleTasks.addAll(tasks);

        while (true) {
            processProgress(progressCallback, tasks.size());

            System.out.format("nThreads=%s, active=%s, idle=%s\n", nThreads, activeRequests.size(), idleTasks.size());
            if (!update(tasks, progressCallback))
                break;

            changedEvent.waitFor();
        }

        processProgress(progressCallback, tasks.size());
        progressCallback.onFinish();
    }

    @Override
    public synchronized void setThreadsCount(int nThreads) {
        if (nThreads < 1)
            throw new IllegalArgumentException("nThreads < 1");

        this.nThreads = nThreads;
        threadPoolExecutor.setCorePoolSize(nThreads);
        threadPoolExecutor.setMaximumPoolSize(nThreads);
        changedEvent.fire();
    }

    private synchronized boolean update(Collection<? extends URLTask> tasks, ProgressCallback progressCallback) {
        if (idleTasks.isEmpty() && activeRequests.isEmpty())
            return false;

        while (activeRequests.size() > nThreads)
            cancelRequest();

        while (activeRequests.size() < nThreads && !idleTasks.isEmpty()) {
            URLTask nextTask = idleTasks.remove();
            addRequest(nextTask);
        }

        return true;
    }

    private void addRequest(URLTask task) {
        System.out.format("Adding request, active=%s\n", activeRequests.size());

        FutureRequest req = new FutureRequest();
        activeRequests.add(req);

        req.future = Async.newInstance().use(threadPoolExecutor).execute(Request.Get(task.getURI()),
                new FutureCallback<Content>() {
                    @Override
                    public void completed(Content content) {
                        progressEvents.addFirst(new ProgressEvent(
                                () -> task.onSuccess(ByteBuffer.wrap(content.asBytes()).asReadOnlyBuffer()),
                                true));
                        onTaskFinished(task, req, false);
                    }

                    @Override
                    public void failed(Exception e) {
                        progressEvents.addFirst(new ProgressEvent(() -> task.onFailure(e), false));
                        onTaskFinished(task, req, false);
                    }

                    @Override
                    public void cancelled() {
                        progressEvents.addFirst(new ProgressEvent(task::onCancel, false));
                        onTaskFinished(task, req, true);
                    }
                }
        );
    }

    private void cancelRequest() {
        System.out.format("Cancelling request, active=%s\n", activeRequests.size());
        FutureRequest res;
        res = activeRequests.iterator().next();
        if (res != null)
            res.future.cancel(true);
    }

    private synchronized void onTaskFinished(URLTask task, FutureRequest req, boolean cancelled) {
        if (cancelled)
            idleTasks.add(task);
        activeRequests.remove(req);
        changedEvent.fire();
    }

    private void processProgress(ProgressCallback progressCallback, int totalTasks) {
        ProgressEvent e;
        while ((e = progressEvents.poll()) != null) {
            e.process.run();
            if (e.incrementTotalProgress) {
                doneTasksCount++;
                progressCallback.onProgress((double) doneTasksCount / totalTasks);
            }
        }
        progressEvents.clear();
    }
}
