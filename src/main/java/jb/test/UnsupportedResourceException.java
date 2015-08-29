package jb.test;

import java.io.IOException;

public class UnsupportedResourceException extends IOException {
    public UnsupportedResourceException() {
        super("Resources with unknown content-length are not supported yet");
    }
}
