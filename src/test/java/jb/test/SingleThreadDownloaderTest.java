package jb.test;

import jb.test.Downloader;
import jb.test.SingleThreadDownloader;
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

public class SingleThreadDownloaderTest {

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