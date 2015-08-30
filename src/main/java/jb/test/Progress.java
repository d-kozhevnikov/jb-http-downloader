package jb.test;

import java.util.Optional;

public class Progress {
    private final long downloaded;
    private final Optional<Long> total;

    public Progress(long downloaded, Optional<Long> total) {
        this.downloaded = downloaded;
        this.total = total;
    }

    public long getDownloaded() {
        return downloaded;
    }

    public Optional<Long> getTotal() {
        return total;
    }
}
