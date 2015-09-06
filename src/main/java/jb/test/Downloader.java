package jb.test;

import java.io.Closeable;
import java.util.Collection;


/**
 * Downloader is the interface for multithreaded HTTP downloader.
 *
 * @author   Dmitriy Kozhevnikov
 */
public interface Downloader extends Closeable {

    /**<p>
     * Downloads URLs specifies via {@code tasks}.
     * Clients can process downloaded data via callbacks in {@link DownloadingTask}.
     * </p><p>
     * This method blocks until finish, but other methods can be called from other threads to
     * affect the downloading progress and monitor the execution.
     * </p><p>
     * This method can be called only once, otherwise {@code IllegalStateException} is thrown
     * </p>
     * @param tasks    specifies URL to download and provides callbacks to
     *                 notify the client about downloading process.
     *                 @see DownloadingTask
     *
     * @param nThreads    initial downloading threads count.
     *                    @see #setThreadsCount(int)
     *
     * @throws InterruptedException if any thread has interrupted the current thread
     * @throws IllegalStateException if called more that once
     */
    void run(Collection<? extends DownloadingTask> tasks, int nThreads) throws InterruptedException;

    /**<p>
     * Reports current downloading progress.
     * </p><p>
     * No global synchronization is performed for performance reasons,
     * so returned data may be slightly obsolete as downloading continues
     * as total progress is calculated.
     * </p><p>
     * Return valid is guaranteed to be actual after the downloading is complete.
     * </p>
     * @return total downloading progress (sum of downloaded data and sum of
     * all file lengths if known)
     */
    Progress getProgress();


    /**
     * Changes downloading threads count.
     *
     * @param nThreads new threads count
     */
    void setThreadsCount(int nThreads);

    /**
     * Stops all downloading and closes all connections. Does nothing if downloading already finished.
     * Blocks till everything is stopped.
     */
    void close();
}
