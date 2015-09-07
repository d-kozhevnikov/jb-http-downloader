package jb.test;

import java.io.IOException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.util.Optional;

/**<p>
 * Specifies URL to download and provides callback to process downloaded data.
 * All callbacks can be called from arbitrary threads,
 * but no two callbacks are called synchronously.
 * </p><p>
 * <b>Error handling strategy:</b> any errors reported by callbacks
 * are passed to {@link #onFailure(Throwable)} and no further
 * downloading is performed.
 * </p>
 *
 * @author Dmitriy Kozhevnikov
 */
public interface DownloadingTask {
    /**
     * @return URL to download
     */
    URL getURL();

    /**
     * Is called exactly once before actual downloading begins.
     *
     * @param contentLength expected content length,
     *                      can be {@code empty} if server didn't send Content-Length
     * @throws IOException if any error is occurred (see "Error handling strategy")
     */
    void onStart(Optional<Long> contentLength) throws IOException;

    /**
     * Is called when the next data chunk is ready for processing.
     *
     * @param chunk read-only data chunk
     * @param offset offset from the beginning of the file, bytes
     * @throws IOException if any error is occurred (see "Error handling strategy")
     */
    void onChunkReceived(ByteBuffer chunk, long offset) throws IOException;

    /**
     * Is called when the downloading is finished.
     * 
     * @throws IOException if any error is occurred (see "Error handling strategy")
     */
    void onSuccess() throws IOException;

    /**
     * Is called when the task is cancelled due to decrease of the thread count.
     * Client should drop all processed data, the following
     * {@link #onChunkReceived(ByteBuffer, long)} would represent the chunk with zero offset again
     *
     * @throws IOException if any error is occurred (see "Error handling strategy")
     */
    void onCancel() throws IOException;

    /**
     * Is called when downloading is failed because of any reason (including
     * exception in any other callback). No further downloading will be performed. 
     * 
     * @param cause an exception caused the failure.
     */
    void onFailure(Throwable cause);

    /**
     * Is called when downloading is interrupted because of downloader shutdown or other client
     * actions. No further downloading will be performed.
     * @throws IOException if any error is occurred (see "Error handling strategy")
     */
    void onDiscard() throws IOException;
}
