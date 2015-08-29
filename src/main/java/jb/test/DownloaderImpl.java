package jb.test;

import jb.test.util.Event;
import org.apache.http.HttpException;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.concurrent.FutureCallback;
import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;
import org.apache.http.impl.nio.client.HttpAsyncClients;
import org.apache.http.nio.IOControl;
import org.apache.http.nio.client.methods.AsyncByteConsumer;
import org.apache.http.nio.client.methods.HttpAsyncMethods;
import org.apache.http.nio.protocol.HttpAsyncRequestProducer;
import org.apache.http.protocol.HttpContext;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.*;

public class DownloaderImpl implements Downloader {

    private final CloseableHttpAsyncClient httpClient;

    private class ProgressEvent {
        public final Runnable process;
        public final boolean incrementTotalProgress;

        private ProgressEvent(Runnable process, boolean incrementTotalProgress) {
            this.incrementTotalProgress = incrementTotalProgress;
            this.process = process;
        }
    }

    private class FutureRequest {
        public Future<HttpResponse> future;
    }

    private class AsyncConsumer extends AsyncByteConsumer<HttpResponse> {

        private final URITask task;
        private final ArrayList<Byte> data = new ArrayList<>();

        private AsyncConsumer(URITask task) {
            this.task = task;
        }

        public ByteBuffer result() {
            byte[] bytes = new byte[data.size()];
            for (int i = 0; i != data.size(); ++i)
                bytes[i] = data.get(i);

            return ByteBuffer.wrap(bytes).asReadOnlyBuffer();
        }

        @Override
        protected void onByteReceived(ByteBuffer byteBuffer, IOControl ioControl) throws IOException {
//            System.out.format("Received %d-%d from %s in %s thread\n",
//                    byteBuffer.position(),
//                    byteBuffer.limit(),
//                    task,
//                    Thread.currentThread()
//            );
            for (int i = byteBuffer.position(); i < byteBuffer.limit(); ++i) // todo: there must be a better way
                data.add(byteBuffer.get(i));
        }

        @Override
        protected void onResponseReceived(HttpResponse httpResponse) throws HttpException, IOException {

        }

        @Override
        protected HttpResponse buildResult(HttpContext httpContext) throws Exception {
            return null;
        }
    }

    // parts of synchronized state
    private final HashSet<FutureRequest> activeRequests = new HashSet<>();
    private final Deque<URITask> idleTasks = new ArrayDeque<>();
    private int nThreads;
    private final Event changedEvent = new Event();

    private final BlockingDeque<ProgressEvent> progressEvents = new LinkedBlockingDeque<>();
    private int tasksCount;
    private int doneTasksCount = 0;

    public DownloaderImpl() {
        httpClient = HttpAsyncClients.createDefault();
    }

    @Override
    public void close() throws IOException {
        httpClient.close();
    }

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

        httpClient.start();
        final HttpGet request = new HttpGet(task.getURI());
        HttpAsyncRequestProducer producer = HttpAsyncMethods.create(request);
        AsyncConsumer consumer = new AsyncConsumer(task);
        req.future = httpClient.execute(producer, consumer, new FutureCallback<HttpResponse>() {
            @Override
            public void completed(HttpResponse httpResponse) {
                //System.out.format("--- Downloaded in thread %s\n", Thread.currentThread());
                progressEvents.addFirst(new ProgressEvent(() -> task.onSuccess(consumer.result()), true));
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
        });
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
