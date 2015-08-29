package jb.test;

import java.net.URI;
import java.nio.ByteBuffer;

public interface URITask {
    URI getURI();

    void onStart(long contentLength);
    void onChunkReceived(ByteBuffer chunk);

    void onSuccess();
    void onFailure(Throwable cause);
    void onCancel();
}
