package jb.test;

import java.net.URI;
import java.nio.ByteBuffer;
import java.util.Optional;

public class InMemoryURITask implements URITask {
    private final URI uri;
    private ByteBuffer result;

    public InMemoryURITask(URI uri) {
        this.uri = uri;
    }

    @Override
    public URI getURI() {
        return uri;
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
    public void onCancel() {
    }

    public ByteBuffer getResult() {
        return result;
    }
}
