package jb.test;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

import static org.junit.Assert.*;

public class VariableDownloaderTest {

    @org.junit.Test
    public void testRun() throws Exception {
        Collection<String> urls = Collections.nCopies(20, "http://vhost2.hansenet.de/1_mb_file.bin");
//        Collection<String> urls = Arrays.asList(
//                "http://google.com/",
//                "http://ya.ru/",
//                "http://jetbrains.com/",
//                "http://yandex.ru/",
//                "http://example.com"
//        );


        class ProgressCallback implements Downloader.ProgressCallback {
            private int progressCount = 0;
            private Thread thread;
            private boolean finishCalled = false;

            @Override
            public void onProgress(double value) {
                assertSame(thread, Thread.currentThread());
                progressCount++;
                assertEquals((double) progressCount / urls.size(), value, 0.01);
            }

            @Override
            public void onFinish() {
                finishCalled = true;
                assertSame(thread, Thread.currentThread());
                assertEquals(progressCount, urls.size());
            }

            public boolean isFinishCalled() {
                return finishCalled;
            }

            public void setThread(Thread Thread) {
                this.thread = Thread;
            }

            public Thread getThread() {
                return thread;
            }
        }

        ProgressCallback cb = new ProgressCallback();

        class TestTask implements URLTask {
            private String result = null;
            private final URI uri;

            public TestTask(String url) {
                try {
                    this.uri = new URI(url);
                } catch (URISyntaxException e) {
                    throw new RuntimeException(e);
                }
            }

            @Override
            public URI getURI() {
                return uri;
            }

            @Override
            public void onSuccess(ByteBuffer result) {
                assertSame(cb.getThread(), Thread.currentThread());
                System.out.format("Downloaded %s in thread %s\n", uri, Thread.currentThread());
                this.result = StandardCharsets.UTF_8.decode(result).toString();
            }

            @Override
            public void onFailure(Throwable cause) {
                assertSame(cb.getThread(), Thread.currentThread());
                fail();
            }

            @Override
            public void onCancel() {
                assertSame(cb.getThread(), Thread.currentThread());
                System.out.format("Cancelled %s in thread %s\n", uri, Thread.currentThread());
            }

            public String getResult() {
                return result;
            }
        }

        //noinspection Convert2MethodRef
        Collection<TestTask> tasks = urls.stream().map(url -> new TestTask(url)).collect(Collectors.toList());

//        Collection<TestTask> tasks = Arrays.asList(
//                new TestTask("http://google.com/"),
//                new TestTask("http://ya.ru/"),
//                new TestTask("http://jetbrains.com/"),
//                new TestTask("http://yandex.ru/"),
//                new TestTask("http://example.com")
//        );

        Downloader downloader = new VariableDownloader(1);

        ExecutorService service = Executors.newSingleThreadExecutor();
        Future<?> f = service.submit(() -> {
            try {
                cb.setThread(Thread.currentThread());
                downloader.run(tasks, cb);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        Thread.sleep(1000);
        System.out.println("Setting 5 threads");
        downloader.setThreadsCount(5);
        Thread.sleep(2000);
        System.out.println("Setting 3 thread");
        downloader.setThreadsCount(3);
        f.get();

        assertTrue(cb.isFinishCalled());

//        assertTrue(tasks.stream()
//                .allMatch((task) -> task.getResult() != null && task.getResult().contains("</html>")));

        assertTrue(tasks.stream()
                .allMatch((task) -> task.getResult() != null && task.getResult().length() == 1048576));
    }
}