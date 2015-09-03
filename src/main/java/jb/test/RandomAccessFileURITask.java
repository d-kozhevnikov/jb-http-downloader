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
    private final Path path;
    private RandomAccessFile f;
    private FileChannel channel;
    private long fileLength = 0L;
    private long writtenLength = 0L;

    public RandomAccessFileURITask(URI uri, Path path) throws IOException {
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
        f = new RandomAccessFile(path.toFile(), "rw");
        channel = f.getChannel();
        fileLength = contentLength.orElse(0xFFFFL);
        f.setLength(fileLength);
        writtenLength = 0;
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
        channel.position(0);
        writtenLength = 0;
    }
}
