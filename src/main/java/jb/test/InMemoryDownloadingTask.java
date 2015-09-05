package jb.test;

import java.io.IOException;
import java.net.URL;
import java.net.URL;
import java.nio.ByteBuffer;
import java.util.Optional;

public class InMemoryDownloadingTask implements DownloadingTask {
    private final URL url;
    private ByteBuffer result;

    public InMemoryDownloadingTask(URL url) {
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
    }

    @Override
    public void onFailure(Throwable cause) {
    }

    @Override
    public void onDiscard() throws IOException {
        onCancel();
    }

    @Override
    public void onCancel() {
        result.clear();
    }

    public ByteBuffer getResult() {
        return result;
    }
}
