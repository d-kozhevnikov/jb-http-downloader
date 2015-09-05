package jb.test;

import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.Optional;

public interface DownloadingTask {
    URI getURI();

    void onStart(Optional<Long> contentLength) throws IOException;

    void onChunkReceived(ByteBuffer chunk) throws IOException;

    void onSuccess() throws IOException;

    void onCancel() throws IOException;

    void onFailure(Throwable cause);

    void onDiscard() throws IOException;
}
