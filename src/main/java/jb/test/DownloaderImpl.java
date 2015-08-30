package jb.test;

import jb.test.util.Event;
import org.apache.http.*;
import org.apache.http.client.HttpClient;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.concurrent.FutureCallback;
import org.apache.http.conn.HttpClientConnectionManager;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;
import org.apache.http.impl.nio.client.HttpAsyncClients;
import org.apache.http.impl.nio.conn.PoolingNHttpClientConnectionManager;
import org.apache.http.impl.nio.reactor.DefaultConnectingIOReactor;
import org.apache.http.nio.IOControl;
import org.apache.http.nio.client.methods.AsyncByteConsumer;
import org.apache.http.nio.client.methods.HttpAsyncMethods;
import org.apache.http.nio.protocol.HttpAsyncRequestProducer;
import org.apache.http.nio.reactor.IOReactorException;
import org.apache.http.protocol.HttpContext;

import javax.swing.text.html.Option;
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

    private class ProgressData {
        public long downloadedBytes = 0;
        public Optional<Long> totalBytes = Optional.empty();
    }

    // parts of synchronized state
    private final HashSet<FutureRequest> activeRequests = new HashSet<>();
    private final Deque<URITask> idleTasks = new ArrayDeque<>();
    private final HashMap<URITask, ProgressData> progress = new HashMap<>();
    private int nThreads;
    private final Event changedEvent = new Event();

    public DownloaderImpl() {
        PoolingNHttpClientConnectionManager poolingConnManager = null;
        try {
            poolingConnManager = new PoolingNHttpClientConnectionManager(new DefaultConnectingIOReactor());
            poolingConnManager.setDefaultMaxPerRoute(20);
            httpClient = HttpAsyncClients.custom().setConnectionManager(poolingConnManager).build();
        } catch (IOReactorException e) {
            throw new RuntimeException(e); // todo
        }
    }

    @Override
    public void close() throws IOException {
        httpClient.close();
    }

    @Override
    public void run(Collection<? extends URITask> tasks, int nThreads) throws InterruptedException {
        this.nThreads = nThreads;

        for (URITask task : tasks) {
            progress.put(task, new ProgressData());
            try (CloseableHttpClient headersClient = HttpClients.createDefault()) {
                HttpGet request = new HttpGet(task.getURI());
                HttpResponse response = headersClient.execute(request);
                long length = response.getEntity().getContentLength();
                if (length >= 0)
                    setTotalBytes(task, length);
                idleTasks.add(task);
            } catch (Exception e) {
                setTotalBytes(task, 0);
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
        System.out.println("Executing for " + task);
        req.future = httpClient.execute(producer,
                new AsyncByteConsumer<HttpResponse>() {
                    @Override
                    protected void onByteReceived(ByteBuffer buf, IOControl ioctl) throws IOException {
                        assert buf.position() == 0; // I believe so but it is not clear from docs
                        addProgress(task, buf.limit());
                        task.onChunkReceived(buf);
                        //System.out.format("Received %d from %s in %s thread\n", buf.limit(), task, Thread.currentThread());
                    }

                    @Override
                    protected void onResponseReceived(HttpResponse response) throws HttpException, IOException {
                        long length = response.getEntity().getContentLength();
                        Optional<Long> lengthOpt = Optional.empty();
                        if (length >= 0) {
                            setTotalBytes(task, length);
                            lengthOpt = Optional.of(length);
                        }

                        task.onStart(lengthOpt);
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
                            completeProgress(task);
                            task.onSuccess();
                        } finally {
                            onTaskFinished(task, req, false);
                        }
                    }

                    @Override
                    public void failed(Exception e) {
                        try {
                            setTotalBytes(task, 0);
                            task.onFailure(e);
                        } finally {
                            onTaskFinished(task, req, false);
                        }
                    }

                    @Override
                    public void cancelled() {
                        try {
                            resetProgress(task);
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
        if (cancelled)
            idleTasks.add(task);
        activeRequests.remove(req);
        changedEvent.fire();
    }
}
