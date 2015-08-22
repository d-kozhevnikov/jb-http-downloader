package jb.test;

import java.util.Collection;

public interface Downloader {
    interface ProgressCallback {
        void onProgress(double value);
        void onFinish();
    }

    void run(Collection<? extends URLTask> tasks, ProgressCallback progressCallback);
}
