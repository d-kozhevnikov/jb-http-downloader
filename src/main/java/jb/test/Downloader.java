package jb.test;

import java.util.Collection;

public interface Downloader {
    void run(Collection<? extends URITask> tasks) throws InterruptedException;
    double getProgress();
    void setThreadsCount(int nThreads);
}
