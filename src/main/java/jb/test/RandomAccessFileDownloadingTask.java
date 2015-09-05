package jb.test;

import java.io.Closeable;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedByInterruptException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

public class RandomAccessFileDownloadingTask implements Closeable, DownloadingTask {

    private final URI uri;

    private final Path path;
    private RandomAccessFile f;
    private FileChannel channel;
    private long fileLength = 0L;
    private long writtenLength = 0L;

    public RandomAccessFileDownloadingTask(URI uri, Path path) {
        this.uri = uri;
        this.path = path;
    }

    public long getWrittenLength() {
        return writtenLength;
    }

    @Override
    public void close() throws IOException {
        f.close();
    }

    @Override
    public URI getURI() {
        return uri;
    }

    @Override
    public void onStart(Optional<Long> contentLength) throws IOException {
        fileLength = contentLength.orElse(0xFFFFL);
        open();
    }

    @Override
    public void onChunkReceived(ByteBuffer chunk) throws IOException {
        if (writtenLength + chunk.limit() > fileLength) {
            fileLength += 0xFFFFL;
            f.setLength(fileLength);
        }
        writtenLength += channel.write(chunk);
    }

    @Override
    public void onSuccess() throws IOException {
        close();
    }

    @Override
    public void onFailure(Throwable cause) {
        try {
            close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onCancel() throws IOException {
        try {
            channel.position(0);
        } catch (ClosedByInterruptException e) {
            open();
        }
    }

    @Override
    public void onDiscard() throws IOException {
        channel.close();
        try {
            Files.delete(path);
        } catch (IOException e) {
            e.printStackTrace();
        }
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
