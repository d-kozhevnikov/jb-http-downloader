package jb.test;

import java.io.ByteArrayOutputStream;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;

public class DownloadJob implements Runnable {

    private final URLTask task;

    DownloadJob(URLTask task) {
        this.task = task;
    }

    @Override
    public void run() {
        try {
            ReadableByteChannel rbc = Channels.newChannel(new URL(task.getURI().toString()).openStream());
            ByteArrayOutputStream out = new ByteArrayOutputStream(); // todo: get content-length and preallocate

            ByteBuffer buffer = ByteBuffer.allocate(0xFFF);
            while (rbc.read(buffer) >= 0) {
                out.write(buffer.array());
                buffer.clear();
            }

            task.onSuccess(ByteBuffer.wrap(out.toByteArray()).asReadOnlyBuffer());

        } catch (Exception e) {
            task.onFailure(e);
        }
    }
}
