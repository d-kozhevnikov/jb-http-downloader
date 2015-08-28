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

public class DownloaderImpl implements Downloader {
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
    private final Deque<URITask> idleTasks = new ArrayDeque<>();
    private int nThreads;
    private final Event changedEvent = new Event();

    private final BlockingDeque<ProgressEvent> progressEvents = new LinkedBlockingDeque<>();
    private int tasksCount;
    private int doneTasksCount = 0;

    @Override
    public void run(Collection<? extends URITask> tasks, int nThreads) throws InterruptedException {
        this.nThreads = nThreads;
        tasksCount = tasks.size();
        idleTasks.addAll(tasks);

        while (true) {
            processProgress();

            //System.out.format("nThreads=%s, active=%s, idle=%s\n", nThreads, activeRequests.size(), idleTasks.size());
            if (!update())
                break;

            changedEvent.waitFor();
        }

        processProgress();
    }

    @Override
    public double getProgress() {
        return (double)doneTasksCount / tasksCount;
    }

    @Override
    public synchronized void setThreadsCount(int nThreads) {
        if (nThreads < 1)
            throw new IllegalArgumentException("nThreads < 1");

        this.nThreads = nThreads;
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
        //System.out.format("Adding request, active=%s\n", activeRequests.size());

        FutureRequest req = new FutureRequest();
        activeRequests.add(req);

        // todo: AIFAK, it provides no API to close connections. Does it really do it? (maybe use non-Fluent interface).
        req.future = Async.newInstance().execute(Request.Get(task.getURI()),
                new FutureCallback<Content>() {
                    @Override
                    public void completed(Content content) {
                        //System.out.format("--- Downloaded in thread %s\n", Thread.currentThread());
                        progressEvents.addFirst(new ProgressEvent(
                                () -> task.onSuccess(ByteBuffer.wrap(content.asBytes()).asReadOnlyBuffer()),
                                true));
                        onTaskFinished(task, req, false);
                    }

                    @Override
                    public void failed(Exception e) {
                        progressEvents.addFirst(new ProgressEvent(() -> task.onFailure(e), true));
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
        //System.out.format("Cancelling request, active=%s\n", activeRequests.size());
        FutureRequest res;
        res = activeRequests.iterator().next();
        if (res != null)
            res.future.cancel(true);
    }

    private synchronized void onTaskFinished(URITask task, FutureRequest req, boolean cancelled) {
        if (cancelled)
            idleTasks.add(task);
        activeRequests.remove(req);
        changedEvent.fire();
    }

    private void processProgress() {
        ProgressEvent e;
        while ((e = progressEvents.poll()) != null) {
            if (e.incrementTotalProgress)
                doneTasksCount++;

            e.process.run();
        }
        progressEvents.clear();
    }
}
