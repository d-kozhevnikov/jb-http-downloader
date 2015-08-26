package jb.test;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.Assert.*;

public class VariableDownloaderTest {

    @org.junit.Test
    public void testRun() throws Exception {
        class TestTask implements URLTask {
            private String result = null;
            private final URI uri;

            public TestTask(String url) throws URISyntaxException {
                this.uri = new URI(url);
            }

            @Override
            public URI getURI() {
                return uri;
            }

            @Override
            public void onSuccess(ByteBuffer result) {
                System.out.format("Downloaded %s in thread %s\n", uri, Thread.currentThread());
                this.result = StandardCharsets.UTF_8.decode(result).toString();
            }

            @Override
            public void onFailure(Throwable cause) {
                fail();
            }

            public String getResult() {
                return result;
            }
        }

        Collection<TestTask> tasks = Arrays.asList(
                new TestTask("http://vhost2.hansenet.de/1_mb_file.bin"),
                new TestTask("http://vhost2.hansenet.de/1_mb_file.bin"),
                new TestTask("http://vhost2.hansenet.de/1_mb_file.bin"),
                new TestTask("http://vhost2.hansenet.de/1_mb_file.bin"),
                new TestTask("http://vhost2.hansenet.de/1_mb_file.bin"),
                new TestTask("http://vhost2.hansenet.de/1_mb_file.bin"),
                new TestTask("http://vhost2.hansenet.de/1_mb_file.bin"),
                new TestTask("http://vhost2.hansenet.de/1_mb_file.bin"),
                new TestTask("http://vhost2.hansenet.de/1_mb_file.bin"),
                new TestTask("http://vhost2.hansenet.de/1_mb_file.bin"),
                new TestTask("http://vhost2.hansenet.de/1_mb_file.bin"),
                new TestTask("http://vhost2.hansenet.de/1_mb_file.bin"),
                new TestTask("http://vhost2.hansenet.de/1_mb_file.bin"),
                new TestTask("http://vhost2.hansenet.de/1_mb_file.bin"),
                new TestTask("http://vhost2.hansenet.de/1_mb_file.bin"),
                new TestTask("http://vhost2.hansenet.de/1_mb_file.bin"),
                new TestTask("http://vhost2.hansenet.de/1_mb_file.bin"),
                new TestTask("http://vhost2.hansenet.de/1_mb_file.bin"),
                new TestTask("http://vhost2.hansenet.de/1_mb_file.bin"),
                new TestTask("http://vhost2.hansenet.de/1_mb_file.bin"),
                new TestTask("http://vhost2.hansenet.de/1_mb_file.bin"),
                new TestTask("http://vhost2.hansenet.de/1_mb_file.bin"),
                new TestTask("http://vhost2.hansenet.de/1_mb_file.bin"),
                new TestTask("http://vhost2.hansenet.de/1_mb_file.bin"),
                new TestTask("http://vhost2.hansenet.de/1_mb_file.bin")
        );

//        Collection<TestTask> tasks = Arrays.asList(
//                new TestTask("http://google.com/"),
//                new TestTask("http://ya.ru/"),
//                new TestTask("http://jetbrains.com/"),
//                new TestTask("http://yandex.ru/"),
//                new TestTask("http://example.com")
//        );
//        int cnt = tasks.size();

        class ProgressCallback implements Downloader.ProgressCallback {
            private int progressCount = 0;
            private final Thread mainThread = Thread.currentThread();
            private boolean finishCalled = false;

            @Override
            public void onProgress(double value) {
//                assertSame(mainThread, Thread.currentThread());
//                progressCount++;
//                assertEquals((double)progressCount / cnt, value, 0.01);
            }

            @Override
            public void onFinish() {
                finishCalled = true;
//                assertSame(mainThread, Thread.currentThread());
//                assertEquals(progressCount, cnt);
            }

            public boolean isFinishCalled() {
                return finishCalled;
            }
        };

        Downloader downloader = new VariableDownloader(3);
        ProgressCallback cb = new ProgressCallback();

        ExecutorService service = Executors.newSingleThreadExecutor();
        Future<?> f = service.submit(() -> {
            try {
                downloader.run(tasks, cb);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        Thread.sleep(1000);
        System.out.println("Setting 1 thread");
        downloader.setThreadsCount(1);
        f.get();

        assertTrue(cb.isFinishCalled());

//        assertTrue(tasks.stream()
//                .allMatch((task) -> task.getResult() != null && task.getResult().contains("</html>")));

        assertTrue(tasks.stream()
                .allMatch((task) -> task.getResult() != null && task.getResult().length() == 1048576));
    }
}