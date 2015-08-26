package jb.test;

import java.net.URI;
import java.net.URL;
import java.nio.ByteBuffer;

public interface URLTask {
    URI getURI();

    void onSuccess(ByteBuffer result);
    void onFailure(Throwable cause);
}
