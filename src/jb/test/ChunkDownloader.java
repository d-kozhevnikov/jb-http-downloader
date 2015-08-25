package jb.test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

public class ChunkDownloader implements Downloader {

    private BlockingQueue<DownloadingTask> idleTasks;

    class DownloadingTask implements Runnable {

        private final URLTask task;
        private final ReadableByteChannel rbc;

        private final ByteArrayOutputStream out = new ByteArrayOutputStream();
        private  ByteBuffer buffer = ByteBuffer.allocate(0xFFF);

        private boolean finished = false;

        DownloadingTask(URLTask task) {;
            this.task = task;
            ReadableByteChannel rbc_tmp;
            try {
                rbc_tmp = Channels.newChannel(task.getURL().openStream());
            } catch (IOException e) {
                task.onFailure(e);
                rbc_tmp = null;
                finished = true;
            }
            rbc = rbc_tmp;
        }

        // download single chunk
        @Override
        public void run() {
            if (!finished) {
                try {
                    int bytesRead = rbc.read(buffer);
                    finished = bytesRead < 0;
                    if (finished)
                        task.onSuccess(ByteBuffer.wrap(out.toByteArray()).asReadOnlyBuffer());
                        out.write(buffer.array());
                    buffer.clear();
                } catch (IOException e) {
                    task.onFailure(e);
                    finished = true;
                }
            }

            idleTasks.add(this);
        }

        public boolean isFinished() {
            return finished;
        }
    }

    @Override
    public void run(Collection<? extends URLTask> tasks, ProgressCallback progressCallback) throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(3);

        idleTasks = new ArrayBlockingQueue<>(tasks.size());
        idleTasks.addAll(tasks.stream().map(DownloadingTask::new).collect(Collectors.toList()));

        Set<DownloadingTask> finished = new HashSet<>();
        while (finished.size() != tasks.size()) {
            DownloadingTask t = idleTasks.take();
            if (t.isFinished()) {
                finished.add(t);
                progressCallback.onProgress((double)finished.size() / tasks.size());
            }
            else
                executor.submit(t);
        }
        progressCallback.onFinish();
    }
}
