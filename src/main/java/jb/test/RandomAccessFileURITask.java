package jb.test;

import java.io.Closeable;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.util.Optional;

public class RandomAccessFileURITask implements Closeable, URITask {

    private final URI uri;
    private RandomAccessFile f;
    private FileChannel channel;
    private long fileLength = 0L;

    public long getWrittenLength() {
        return writtenLength;
    }

    private long writtenLength = 0L;


    public RandomAccessFileURITask(URI uri, Path path) throws IOException {
        f = new RandomAccessFile(path.toFile(), "rw");
        channel = f.getChannel();
        this.uri = uri;
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
        f.setLength(fileLength);
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
    }

    @Override
    public void onCancel() throws IOException {
        channel.position(0);
        writtenLength = 0;
    }
}
