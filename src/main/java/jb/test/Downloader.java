package jb.test;

import java.io.Closeable;
import java.util.Collection;
import java.util.Optional;

public interface Downloader extends Closeable {
    void run(Collection<? extends URITask> tasks, int nThreads) throws InterruptedException;
    Progress getProgress();
    void setThreadsCount(int nThreads);
}
