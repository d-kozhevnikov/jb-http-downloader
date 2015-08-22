package jb.test;

import java.net.MalformedURLException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

import static org.junit.Assert.*;

public class SingleThreadDownloaderTest {

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
                new TestTask("http://example.com/"),
                new TestTask("http://jetbrains.com/"));

        new SingleThreadDownloader().run(tasks, new Downloader.ProgressCallback() {
            @Override
            public void onProgress(double value) {
            }

            @Override
            public void onFinish() {
            }
        });

        assertTrue(tasks.stream()
                .allMatch((task) -> task.getResult() != null && task.getResult().contains("</html>")));
    }
}