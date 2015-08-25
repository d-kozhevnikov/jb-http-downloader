package jb.test;

import java.util.Collection;

public class SingleThreadDownloader implements Downloader {

    @Override
    public void run(Collection<? extends URLTask> tasks, ProgressCallback progressCallback) {
        int total = tasks.size();
        int current = 0;
        for (URLTask task : tasks) {
            new DownloadJob(task).run();
            progressCallback.onProgress((double)(current + 1) / total);
            ++current;
        }
        progressCallback.onFinish();
    }
}
