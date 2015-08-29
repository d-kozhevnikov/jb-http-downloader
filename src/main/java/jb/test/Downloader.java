package jb.test;

import java.io.Closeable;
import java.util.Collection;

public interface Downloader extends Closeable {
    void run(Collection<? extends URITask> tasks, int nThreads) throws InterruptedException;
    double getProgress();
    void setThreadsCount(int nThreads);
}
