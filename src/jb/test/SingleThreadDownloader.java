package jb.test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.util.Collection;

public class SingleThreadDownloader implements Downloader {

    @Override
    public void run(Collection<? extends URLTask> tasks, ProgressCallback progressCallback) {
        int total = tasks.size();
        int current = 0;
        for (URLTask task : tasks) {
            try {
                ReadableByteChannel rbc = Channels.newChannel(task.getURL().openStream());
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
            progressCallback.onProgress((double)current / total);
            ++current;
        }
        progressCallback.onFinish();
    }
}
