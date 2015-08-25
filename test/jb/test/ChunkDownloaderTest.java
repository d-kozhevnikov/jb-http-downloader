package jb.test;

import java.net.MalformedURLException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collection;

import static org.junit.Assert.*;

public class ChunkDownloaderTest {

    @org.junit.Test
    public void testRun() throws Exception {
        class TestTask implements URLTask {
            private String result = null;
            private final URL url;

            public TestTask(String url) throws MalformedURLException {
                this.url = new URL(url);
            }

            @Override
            public URL getURL() {
                return url;
            }

            @Override
            public void onSuccess(ByteBuffer result) {
                System.out.format("Downloaded %s in thread %s\n", url, Thread.currentThread());
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
                new TestTask("http://google.com/"),
                new TestTask("http://ya.ru/"),
                new TestTask("http://jetbrains.com/"),
                new TestTask("http://yandex.ru/"),
                new TestTask("http://example.com")
        );
        int cnt = tasks.size();

        class ProgressCallback implements Downloader.ProgressCallback {
            private int progressCount = 0;
            private final Thread mainThread = Thread.currentThread();
            private boolean finishCalled = false;

            @Override
            public void onProgress(double value) {
                assertSame(mainThread, Thread.currentThread());
                progressCount++;
                assertEquals((double)progressCount / cnt, value, 0.01);
            }

            @Override
            public void onFinish() {
                finishCalled = true;
                assertSame(mainThread, Thread.currentThread());
                assertEquals(progressCount, cnt);
            }

            public boolean isFinishCalled() {
                return finishCalled;
            }
        };

        ProgressCallback cb = new ProgressCallback();
        new ChunkDownloader().run(tasks, cb);
        assertTrue(cb.isFinishCalled());

        assertTrue(tasks.stream()
                .allMatch((task) -> task.getResult() != null && task.getResult().contains("</html>")));
    }
}