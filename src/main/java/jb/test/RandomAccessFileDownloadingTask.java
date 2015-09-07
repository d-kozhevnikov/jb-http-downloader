package jb.test;

import java.io.Closeable;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedByInterruptException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

public class RandomAccessFileDownloadingTask implements Closeable, DownloadingTask {

    private final URL url;

    private final Path path;
    private RandomAccessFile f;
    private FileChannel channel;
    private long fileLength = 0L;
    private long writtenLength = 0L;

    public RandomAccessFileDownloadingTask(URL url, Path path) {
        this.url = url;
        this.path = path;
    }

    public long getFileLength() {
        return fileLength;
    }
    public long getWrittenLength() {
        return writtenLength;
    }

    @Override
    public void close() throws IOException {
        if (f != null)
            f.close();
    }

    @Override
    public URL getURL() {
        return url;
    }

    @Override
    public void onStart(Optional<Long> contentLength) throws IOException {
        fileLength = contentLength.orElse(0xFFFFL);
        open();
    }

    @Override
    public void onChunkReceived(ByteBuffer chunk, long offset) throws IOException {
        if (offset + chunk.limit() > fileLength) {
            fileLength = offset + chunk.limit() + 0xFFFFL;
            f.setLength(fileLength);
        }
        writtenLength += channel.write(chunk, offset);
    }

    @Override
    public void onSuccess() throws IOException {
        fileLength = writtenLength;
        f.setLength(fileLength);
        close();
    }

    @Override
    public void onFailure(Throwable cause) {
        try {
            close();
            Files.delete(path);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onCancel() throws IOException {
        try {
            channel.position(0);
            writtenLength = 0;
        } catch (ClosedByInterruptException e) {
            open();
        }
    }

    @Override
    public void onDiscard() throws IOException {
        close();
        Files.delete(path);
    }

    public Path getPath() {
        return path;
    }

    private void open() throws IOException {
        f = new RandomAccessFile(path.toFile(), "rw");
        channel = f.getChannel();
        f.setLength(fileLength);
        writtenLength = 0;
    }
}
