package jb.test;

import jb.test.util.Event;
import org.apache.http.*;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.concurrent.FutureCallback;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
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
import java.util.concurrent.atomic.AtomicLong;

public class DownloaderImpl implements Downloader {

    private final CloseableHttpAsyncClient httpClient;

    private class FutureRequest {
        public Future<HttpResponse> future;
    }

    // parts of synchronized state
    private final HashSet<FutureRequest> activeRequests = new HashSet<>();
    private final Deque<URITask> idleTasks = new ArrayDeque<>();
    private int nThreads;
    private final Event changedEvent = new Event();

    private long totalBytes;
    private AtomicLong downloadedBytes = new AtomicLong(0);

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

        for (URITask task : tasks) {
            try (CloseableHttpClient headersClient = HttpClients.createDefault()) {
                HttpGet request = new HttpGet(task.getURI());
                HttpResponse response = headersClient.execute(request);
                long length = response.getEntity().getContentLength();
                if (length < 0)
                    throw new UnsupportedResourceException();
                totalBytes += length;
                idleTasks.add(task);
            } catch (Exception e) {
                task.onFailure(e);
            }
        }

        while (true) {
            //System.out.format("nThreads=%s, active=%s, idle=%s\n", nThreads, activeRequests.size(), idleTasks.size());
            if (!update())
                break;

            changedEvent.waitFor();
        }
    }

    @Override
    public double getProgress() {
        return (double) downloadedBytes.get() / totalBytes;
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
        req.future = httpClient.execute(producer,
                new AsyncByteConsumer<HttpResponse>() {
                    @Override
                    protected void onByteReceived(ByteBuffer buf, IOControl ioctl) throws IOException {
                        assert buf.position() == 0; // I believe so but it is not clear from docs
                        downloadedBytes.addAndGet(buf.limit());
                        task.onChunkReceived(buf);
                        //System.out.format("Received %d from %s in %s thread\n", buf.limit(), task, Thread.currentThread());
                    }

                    @Override
                    protected void onResponseReceived(HttpResponse response) throws HttpException, IOException {
                        if (response.getEntity().getContentLength() < 0)
                            throw new UnsupportedResourceException();
                        task.onStart(response.getEntity().getContentLength());
                    }

                    @Override
                    protected HttpResponse buildResult(HttpContext context) throws Exception {
                        return null;
                    }
                },

                new FutureCallback<HttpResponse>() {
                    @Override
                    public void completed(HttpResponse httpResponse) {
                        //System.out.format("--- Downloaded in thread %s\n", Thread.currentThread());
                        try {
                            task.onSuccess();
                        } finally {
                            onTaskFinished(task, req, false);
                        }
                    }

                    @Override
                    public void failed(Exception e) {
                        try {
                            task.onFailure(e);
                        } finally {
                            onTaskFinished(task, req, false);
                        }
                    }

                    @Override
                    public void cancelled() {
                        try {
                            task.onCancel();
                        } finally {
                            onTaskFinished(task, req, true);
                        }
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
}
