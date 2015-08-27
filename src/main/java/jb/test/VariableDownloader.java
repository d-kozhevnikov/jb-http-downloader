package jb.test;

import com.google.common.collect.Sets;
import org.apache.http.client.fluent.Async;
import org.apache.http.client.fluent.Content;
import org.apache.http.client.fluent.Request;
import org.apache.http.concurrent.FutureCallback;

import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.Set;
import java.util.concurrent.*;


class Event {
    private boolean signal = false;

    public synchronized void fire() {
        this.signal = true;
        this.notify();
    }

    public synchronized void waitFor() throws InterruptedException {
        while(!this.signal) wait();
        this.signal = false;
    }
}
public class VariableDownloader implements Downloader {

    private final Set<FutureRequest> activeRequests = Sets.newConcurrentHashSet();
    private final BlockingQueue<URLTask> idleTasks = new LinkedBlockingDeque<>();
    private final Event changedEvent = new Event();
    private int nThreads;
    private final ThreadPoolExecutor threadPoolExecutor;

    public VariableDownloader(int nThreads) {
        this.nThreads = nThreads;
        threadPoolExecutor = new ThreadPoolExecutor(nThreads, nThreads, Integer.MAX_VALUE, TimeUnit.SECONDS, new LinkedBlockingDeque<>());
    }

    private class FutureRequest {
        private Future<Content> future;

        public Future<Content> getFuture() {
            return future;
        }

        public void setFuture(Future<Content> future) {
            this.future = future;
        }
    }

    private void cancelRequest() {
        System.out.format("Cancelling request, active=%s\n", activeRequests.size());
        FutureRequest res = activeRequests.iterator().next();
        if (res != null)
            res.getFuture().cancel(true);
    }

    private void addRequest() {
        System.out.format("Adding request, active=%s\n", activeRequests.size());
        URLTask task = idleTasks.poll();
        if (task == null)
            return;

        FutureRequest req = new FutureRequest();
        activeRequests.add(req);

        FutureCallback<Content> futureCallback = new FutureCallback<Content>() {
            @Override
            public void completed(Content content) {
                task.onSuccess(ByteBuffer.wrap(content.asBytes()).asReadOnlyBuffer());
                onFinish();
            }

            @Override
            public void failed(Exception e) {
                task.onFailure(e);
                onFinish();
            }

            @Override
            public void cancelled() {
                idleTasks.add(task);
                onFinish();
            }

            private void onFinish() {
                activeRequests.remove(req);
                changedEvent.fire();
            }
        };

        req.setFuture(Async.newInstance().use(threadPoolExecutor).execute(Request.Get(task.getURI()), futureCallback));
    }

    @Override
    public void run(Collection<? extends URLTask> tasks, ProgressCallback progressCallback) throws InterruptedException {
        idleTasks.addAll(tasks);

        while (true) {
            long time = System.currentTimeMillis();
            System.out.format("====== Loop nThreads=%s, active=%s, idle=%s\n", nThreads, activeRequests.size(), idleTasks.size());
            if (idleTasks.isEmpty() && activeRequests.isEmpty())
                break;
            else if (activeRequests.size() > nThreads)
                cancelRequest();
            else if (activeRequests.size() < nThreads && !idleTasks.isEmpty())
                addRequest();
            else
                changedEvent.waitFor();
            System.out.format("====== Loop end, in loop: %s\n\n", System.currentTimeMillis() - time);

        }

        progressCallback.onFinish();
    }

    @Override
    public void setThreadsCount(int nThreads) {
        this.nThreads = nThreads;
        threadPoolExecutor.setCorePoolSize(nThreads);
        threadPoolExecutor.setMaximumPoolSize(nThreads);
        changedEvent.fire();
    }
}
