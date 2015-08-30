package jb.test;

import java.net.URI;
import java.nio.ByteBuffer;
import java.util.Optional;

public interface URITask {
    URI getURI();

    void onStart(Optional<Long> contentLength);
    void onChunkReceived(ByteBuffer chunk);

    void onSuccess();
    void onFailure(Throwable cause);
    void onCancel();
}
