package jb.test;

import java.util.Optional;

class ProgressData {
    private volatile long downloadedBytes = 0;
    private volatile Optional<Long> totalBytes = Optional.empty();

    public long getDownloadedBytes() {
        return downloadedBytes;
    }

    public Optional<Long> getTotalBytes() {
        return totalBytes;
    }

    public void addDownloadedBytes(long add) {
        if (add < 0 || (totalBytes.isPresent() && totalBytes.get() < downloadedBytes + add))
            throw new IllegalArgumentException();

        downloadedBytes += add;
    }

    public void resetDownloadedBytes() {
        downloadedBytes = 0;
    }

    public void setTotalBytes(long val) {
        if (val < downloadedBytes)
            throw new IllegalArgumentException();

        totalBytes = Optional.of(val);
    }
}
