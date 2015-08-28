package jb.test;

import java.net.URI;
import java.nio.ByteBuffer;

public interface URITask {
    URI getURI();

    void onSuccess(ByteBuffer result);
    void onFailure(Throwable cause);
    void onCancel();
}
