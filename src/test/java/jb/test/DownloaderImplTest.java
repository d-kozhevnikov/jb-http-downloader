package jb.test;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

import static org.junit.Assert.*;

public class DownloaderImplTest {

    @org.junit.Test
    public void testRun() throws Exception {
        Collection<String> uris = Collections.nCopies(20, "http://vhost2.hansenet.de/1_mb_file.bin");
//        Collection<String> uris = Arrays.asList(
//                "http://google.com/",
//                "http://ya.ru/",
//                "http://jetbrains.com/",
//                "http://yandex.ru/",
//                "http://example.com"
//        );

        class Helper {
            public Thread thread;
            public int successCount = 0;
        }
        Helper helper = new Helper();

        Downloader downloader = new DownloaderImpl(1);

        class TestTask implements URITask {
            private String result = null;
            private final URI uri;

            public TestTask(String uri) {
                try {
                    this.uri = new URI(uri);
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
                assertSame(helper.thread, Thread.currentThread());
                helper.successCount++;
                assertEquals((double) helper.successCount / uris.size(), downloader.getProgress(), 0.01);
                System.out.format("Downloaded %s\n", uri);
                this.result = StandardCharsets.UTF_8.decode(result).toString();
            }

            @Override
            public void onFailure(Throwable cause) {
                assertSame(helper.thread, Thread.currentThread());
                fail();
            }

            @Override
            public void onCancel() {
                assertSame(helper.thread, Thread.currentThread());
                System.out.format("Cancelled %s\n", uri);
            }

            public String getResult() {
                return result;
            }
        }

        //noinspection Convert2MethodRef
        Collection<TestTask> tasks = uris.stream().map((uri) -> new TestTask(uri)).collect(Collectors.toList());

        ExecutorService service = Executors.newSingleThreadExecutor();
        Future<?> f = service.submit(() -> {
            try {
                helper.thread = Thread.currentThread();
                downloader.run(tasks);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        Thread.sleep(1000);
        System.out.println("Setting 5 threads");
        downloader.setThreadsCount(5);
        Thread.sleep(1000);
        System.out.println("Setting 3 thread");
        downloader.setThreadsCount(3);
        f.get();

        assertTrue(helper.successCount == uris.size());

        assertTrue(tasks.stream()
                .allMatch((task) -> task.getResult() != null && task.getResult().length() == 1048576));
    }

    @org.junit.Test
    public void testRunFail() throws Exception {
        Collection<String> uris = Arrays.asList(
            "http://a52accf5d22443b28ae680de498de9c6.com/",
            "http://jetbrains.com/"
        );

        Downloader downloader = new DownloaderImpl(2);
        class Helper {
            public Thread thread;
            public int finishCount = 0;
        }
        Helper helper = new Helper();

        class TestTask implements URITask {
            private final URI uri;
            private final boolean ok;

            public TestTask(String uri, boolean ok) {
                try {
                    this.uri = new URI(uri);
                    this.ok = ok;
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
                System.out.format("Downloaded %s\n", uri);
                onFinish();

                if (!ok)
                    fail();
                else
                    assertTrue(StandardCharsets.UTF_8.decode(result).toString().contains("</html>"));
            }

            @Override
            public void onFailure(Throwable cause) {
                System.out.format("Failed %s (%s)\n", uri, cause);
                onFinish();

                if (ok)
                    fail();
            }

            @Override
            public void onCancel() {
                assertSame(helper.thread, Thread.currentThread());
                System.out.format("Cancelled %s\n", uri);
            }

            private void onFinish() {
                assertSame(helper.thread, Thread.currentThread());
                helper.finishCount++;
                assertEquals((double) helper.finishCount / uris.size(), downloader.getProgress(), 0.01);
            }
        }

        Iterator<String> it = uris.iterator();
        Collection<TestTask> tasks = Arrays.asList(
                new TestTask(it.next(), false),
                new TestTask(it.next(), true)
        );

        ExecutorService service = Executors.newSingleThreadExecutor();
        service.submit(() -> {
            try {
                helper.thread = Thread.currentThread();
                downloader.run(tasks);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }).get();

        assertTrue(helper.finishCount == uris.size());
    }

}