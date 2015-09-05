package jb.test;

import java.io.IOException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.junit.Assert.*;

public class DownloaderImplTest {

    private class SuccessCounter {
        private volatile int successCount = 0;
        private volatile int failureCount = 0;

        public void incrementSuccess() {
            ++successCount;
        }

        public int getSuccessCount() {
            return successCount;
        }

        public void incrementFailure() {
            ++failureCount;
        }

        public int getFailureCount() {
            return failureCount;
        }
    }

    private SuccessCounter counter;
    ExecutorService service;
    Downloader downloader;


    class TestTask implements DownloadingTask {
        private final URL url;
        private ByteBuffer result;

        public TestTask(URL url) {
            this.url = url;
        }

        @Override
        public URL getURL() {
            return url;
        }

        @Override
        public void onStart(Optional<Long> contentLength) {
            result = ByteBuffer.allocate(contentLength.orElse(0xFFFFFL).intValue());
        }

        @Override
        public void onChunkReceived(ByteBuffer chunk) {
            if (result.remaining() < chunk.limit()) {
                result.flip();
                result = ByteBuffer.allocate(result.capacity() * 2).put(result);
            }
            result.put(chunk);
        }

        @Override
        public void onSuccess() {
            result.flip();
            if (testOk())
                counter.incrementSuccess();
            else
                counter.incrementFailure();
            System.out.format("Downloaded %s\n", getURL());
        }

        @Override
        public void onDiscard() throws IOException {
            onCancel();
        }

        @Override
        public void onFailure(Throwable cause) {
            counter.incrementFailure();
            System.out.format("Failed %s: %s\n", getURL(), cause);
        }

        @Override
        public void onCancel() {
            result.clear();
            System.out.format("Cancelled %s\n", getURL());
        }

        public ByteBuffer getResult() {
            return result;
        }

        protected boolean testOk() {
            if (url.toString().contains("vhost2"))
                return getResult().limit() == 1048576;
            else if (url.toString().contains("textfiles") || url.toString().contains("jetbrains")) {
                String result = StandardCharsets.UTF_8.decode(getResult()).toString();
                return result.toLowerCase().contains("</html>");
            } else
                return false;
        }
    }

    private Collection<TestTask> createTasks(Collection<URL> urls) {
        return urls.stream().map(TestTask::new).collect(Collectors.toList());
    }

    @org.junit.Before
    public void setUp() {
        counter = new SuccessCounter();
        service = Executors.newSingleThreadExecutor();
        downloader = new DownloaderImpl();

    }

    @org.junit.After
    public void tearDown() {
        downloader.close();
    }

    @org.junit.Test
    public void testRun() throws Exception {
        ArrayList<URL> urls = new ArrayList<>();
        for (int i = 0; i < 5; ++i)
            urls.add(new URL("http://vhost2.hansenet.de/1_mb_file.bin?" + i));
        Collection<TestTask> tasks = createTasks(urls);

        Future<?> f = service.submit(() -> {
            try {
                downloader.run(tasks, 2);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        Thread.sleep(2000);
        System.out.println("Setting 1 thread");
        downloader.setThreadsCount(1);
        Thread.sleep(2000);
        System.out.println("Setting 3 threads");
        downloader.setThreadsCount(3);
        f.get();

        assertTrue(counter.getSuccessCount() == urls.size() && counter.getFailureCount() == 0);
    }

    @org.junit.Test
    public void testRunFail() throws Exception {
        Collection<TestTask> tasks = createTasks(Arrays.asList(
                new URL("http://a52accf5d22443b28ae680de498de9c6.com/"),
                new URL("http://google.com/qwertyuio"),
                new URL("http://textfiles.com/")));

        service.submit(() -> {
            try {
                downloader.run(tasks, 2);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }).get();
        assertTrue(counter.getSuccessCount() == 1 && counter.getFailureCount() == 2);
    }

    @org.junit.Test
    public void testCloseEmpty() {
        downloader.close();
    }

    @org.junit.Test(expected = IllegalStateException.class)
    public void testReRun() throws Exception {
        Collection<TestTask> tasks = createTasks(Arrays.asList(
                new URL("http://a52accf5d22443b28ae680de498de9c6.com/"),
                new URL("http://textfiles.com/")));
        Semaphore s = new Semaphore(0);
        service.submit(() -> {
            try {
                downloader.run(tasks, 1);
                s.release();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }).get();
        s.acquire();
        downloader.run(tasks, 1);
    }

    @org.junit.Test
    public void testProgress() throws Exception {
        Semaphore sem1 = new Semaphore(0);
        Semaphore sem2 = new Semaphore(0);
        Collection<TestTask> tasks = Collections.nCopies(1,
                new TestTask(new URL("http://jetbrains.com")) {
                    @Override
                    public void onStart(Optional<Long> contentLength) {
                        sem1.release();
                        try {
                            sem2.acquire();
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                        super.onStart(contentLength);
                    }
                });
        Future<?> f = service.submit(() -> {
            try {
                downloader.run(tasks, 1);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        sem1.acquire();
        Progress p = downloader.getProgress();
        assertTrue(!p.getTotal().isPresent());
        sem2.release();
        f.get();
        p = downloader.getProgress();
        assertTrue(p.getTotal().isPresent());
        assertTrue(p.getTotal().get() == p.getDownloaded());
    }

    @org.junit.Test(expected = IllegalArgumentException.class)
    public void testSetThreads() throws Exception {
        downloader.setThreadsCount(2);
        downloader.setThreadsCount(-1);
    }

    @org.junit.Test
    public void testInterrupt() throws Exception {
        Semaphore sem1 = new Semaphore(0);
        Semaphore sem2 = new Semaphore(0);
        AtomicBoolean discarded = new AtomicBoolean(false);
        Collection<TestTask> tasks = Collections.nCopies(1,
                new TestTask(new URL("http://jetbrains.com")) {
                    @Override
                    public void onStart(Optional<Long> contentLength) {
                        sem1.release();
                        super.onStart(contentLength);
                    }

                    @Override
                    public void onDiscard() throws IOException {
                        discarded.set(true);
                        super.onDiscard();
                    }
                });

        service.submit(() -> {
            try {
                downloader.run(tasks, 1);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        sem1.acquire();
        downloader.close();
        assertTrue(discarded.get());
    }
}