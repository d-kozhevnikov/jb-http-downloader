package jb.test;

import java.util.Collection;
import java.util.concurrent.*;

public class FixedThreadsDownloader implements Downloader {

    private final int nThreads;

    FixedThreadsDownloader(int nThreads) {
        this.nThreads = nThreads;
    }

    @Override
    public void run(Collection<? extends URLTask> tasks, ProgressCallback progressCallback) throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(nThreads);

        int total = tasks.size();
        Semaphore semaphore = new Semaphore(0);

        for (URLTask task : tasks) {
            executor.submit(() -> {
                new DownloadJob(task).run();
                semaphore.release();
            });
        }

        for (int i = 0; i < total; ++i) {
            semaphore.acquire();
            progressCallback.onProgress((double) (i + 1) / total);
        }
        progressCallback.onFinish();
    }
}
