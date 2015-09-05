package jb.test;

import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
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

    abstract class TestTask extends InMemoryDownloadingTask {
        private final SuccessCounter counter;
        private final boolean ok;

        public TestTask(URL url, boolean ok, SuccessCounter counter) {
            super(url);
            this.ok = ok;
            this.counter = counter;
        }

        @Override
        public void onSuccess() {
            super.onSuccess();
            counter.incrementSuccess();
            if (ok)
                assertTrue(testOk());
            else
                fail();
            System.out.format("Downloaded %s\n", getURL());
        }

        @Override
        public void onFailure(Throwable cause) {
            super.onFailure(cause);
            counter.incrementFailure();
            System.out.format("Failed %s: %s\n", getURL(), cause);
            if (ok)
                fail();
        }

        @Override
        public void onCancel() {
            super.onCancel();
            System.out.format("Cancelled %s\n", getURL());
        }

        abstract boolean testOk();
    }

    @org.junit.Test
    public void testRun() throws Exception {
        ArrayList<URL> urls = new ArrayList<>();
        for (int i = 0; i < 10; ++i)
            urls.add(new URL("http://vhost2.hansenet.de/1_mb_file.bin?" + i));

        try (Downloader downloader = new DownloaderImpl()) {
            SuccessCounter counter = new SuccessCounter();

            Collection<TestTask> tasks = urls.stream().map((url) -> new TestTask(url, true, counter) {
                @Override
                boolean testOk() {
                    return getResult().limit() == 1048576;
                }
            }).collect(Collectors.toList());

            ExecutorService service = Executors.newSingleThreadExecutor();
            Future<?> f = service.submit(() -> {
                try {
                    downloader.run(tasks, 3);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
            Thread.sleep(1500);
            System.out.println("Setting 5 threads");
            downloader.setThreadsCount(5);
            Thread.sleep(1500);
            System.out.println("Setting 3 thread");
            downloader.setThreadsCount(3);
            f.get();

            assertTrue(counter.getSuccessCount() == urls.size() && counter.getFailureCount() == 0);
        }
    }

    @org.junit.Test
    public void testRunFail() throws Exception {
        Collection<URL> urls = Arrays.asList(
                new URL("http://a52accf5d22443b28ae680de498de9c6.com/"),
                new URL("http://textfiles.com/")
        );

        try (Downloader downloader = new DownloaderImpl()) {
            SuccessCounter counter = new SuccessCounter();

            Iterator<URL> it = urls.iterator();
            Collection<TestTask> tasks = Arrays.asList(
                    new TestTask(it.next(), false, counter) {
                        @Override
                        boolean testOk() {
                            return false;
                        }
                    },
                    new TestTask(it.next(), true, counter) {
                        @Override
                        boolean testOk() {
                            String result = StandardCharsets.UTF_8.decode(getResult()).toString();
                            return result.toLowerCase().contains("</html>");
                        }
                    }
            );

            ExecutorService service = Executors.newSingleThreadExecutor();
            service.submit(() -> {
                try {
                    downloader.run(tasks, 2);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }).get();
            assertTrue(counter.getSuccessCount() == 1 && counter.getFailureCount() == 1);
        }
    }
}