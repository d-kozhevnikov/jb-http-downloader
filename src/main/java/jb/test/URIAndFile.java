package jb.test;

import java.net.URI;
import java.nio.file.Path;

public class URIAndFile {
    private final URI uri;
    private final Path path;

    URIAndFile(URI uri, Path path) {
        this.uri = uri;
        this.path = path;
    }

    public URI getUri() {
        return uri;
    }

    public Path getPath() {
        return path;
    }
}
