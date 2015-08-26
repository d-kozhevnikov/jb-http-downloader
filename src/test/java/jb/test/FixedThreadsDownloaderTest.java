package jb.test;

import jb.test.Downloader;
import jb.test.FixedThreadsDownloader;
import jb.test.URLTask;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collection;

import static org.junit.Assert.*;

public class FixedThreadsDownloaderTest {

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
                new TestTask("http://google.com/"),
                new TestTask("http://ya.ru/"),
                new TestTask("http://jetbrains.com/"));
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
        new FixedThreadsDownloader(2).run(tasks, cb);
        assertTrue(cb.isFinishCalled());

        assertTrue(tasks.stream()
                .allMatch((task) -> task.getResult() != null && task.getResult().contains("</html>")));
    }
}