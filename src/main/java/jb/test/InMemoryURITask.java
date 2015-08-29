package jb.test;

import java.net.URI;
import java.nio.ByteBuffer;

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
    public void onStart(long contentLength) {
        result = ByteBuffer.allocate((int) contentLength);
    }

    @Override
    public void onChunkReceived(ByteBuffer chunk) {
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
