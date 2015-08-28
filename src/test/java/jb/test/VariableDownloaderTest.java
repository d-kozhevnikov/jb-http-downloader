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

        Downloader downloader = new VariableDownloader(1);

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
                System.out.format("Downloaded %s in thread %s\n", uri, Thread.currentThread());
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
                System.out.format("Cancelled %s in thread %s\n", uri, Thread.currentThread());
            }

            public String getResult() {
                return result;
            }
        }

        //noinspection Convert2MethodRef
        Collection<TestTask> tasks = uris.stream().map((uri) -> new TestTask(uri)).collect(Collectors.toList());

//        Collection<TestTask> tasks = Arrays.asList(
//                new TestTask("http://google.com/"),
//                new TestTask("http://ya.ru/"),
//                new TestTask("http://jetbrains.com/"),
//                new TestTask("http://yandex.ru/"),
//                new TestTask("http://example.com")
//        );

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
        Thread.sleep(2000);
        System.out.println("Setting 3 thread");
        downloader.setThreadsCount(3);
        f.get();



//        assertTrue(tasks.stream()
//                .allMatch((task) -> task.getResult() != null && task.getResult().contains("</html>")));

        assertTrue(helper.successCount == uris.size());

        assertTrue(tasks.stream()
                .allMatch((task) -> task.getResult() != null && task.getResult().length() == 1048576));
    }
}