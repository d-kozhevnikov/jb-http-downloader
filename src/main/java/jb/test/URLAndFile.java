package jb.test;

import java.net.URL;
import java.nio.file.Path;

public class URLAndFile {
    private final URL url;
    private final Path path;

    URLAndFile(URL url, Path path) {
        this.url = url;
        this.path = path;
    }

    public URL getURL() {
        return url;
    }

    public Path getPath() {
        return path;
    }
}
