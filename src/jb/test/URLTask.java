package jb.test;

import java.net.URL;
import java.nio.ByteBuffer;

public interface URLTask {
    URL getURL();

    void onSuccess(ByteBuffer result);
    void onFailure(Throwable cause);
}
