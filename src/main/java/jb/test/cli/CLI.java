package jb.test.cli;

import jb.test.*;

import java.io.IOException;
import java.net.URI;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.Collection;
import java.util.stream.Collectors;

class WriteToFileTask extends InMemoryURITask {
    private final Path path;
    private final Downloader downloader;

    WriteToFileTask(URI uri, Path path, Downloader downloader) {
        super(uri);
        this.path = path;
        this.downloader = downloader;
    }

    @Override
    public void onSuccess() {
        super.onSuccess();
        try {
            try (FileChannel out = FileChannel.open(path, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
                out.write(getResult());
                out.force(true);
                System.out.format("[%s] Downloaded %s to %s\n", getProgressStr(), getURI(), path);
            }
        } catch (IOException e) {
            processError(e);
        }
    }

    @Override
    public void onFailure(Throwable cause) {
        super.onFailure(cause);
        processError(cause);
    }

    private void processError(Throwable e) {
        System.out.format("[%s] Downloading %s to %s failed (%s)\n", getProgressStr(), getURI(), path, e);
    }

    private String getProgressStr() {
        Progress progress = downloader.getProgress();

        String totalStr = "???";
        String percentStr = "??";
        if (progress.getTotal().isPresent()) {
            totalStr = Long.toString(progress.getTotal().get() / 1024);
            percentStr = String.format("%.0f", (double)progress.getDownloaded() / progress.getTotal().get() * 100.);
        }
        return String.format("%6sKB/%6sKB (%3s%%)", progress.getDownloaded() / 1024, totalStr, percentStr);
    }
}

public class CLI {

    public static void main(String[] args) {
        CmdLineInput input = CmdLineInput.parseCommandLine(args);
        if (input == null) {
            System.out.println(CmdLineInput.getUsage());
            return;
        }
        new CLI().process(input);
    }

    private void process(CmdLineInput input) {
        try (Downloader downloader = new DownloaderImpl()) {
            Collection<URITask> tasks =
                    input.getUris().stream()
                            .map(uriAndFile -> new WriteToFileTask(uriAndFile.getUri(), uriAndFile.getPath(), downloader))
                            .collect(Collectors.toList());

            try {
                downloader.run(tasks, input.getNThreads());
            } catch (InterruptedException e) {
                System.out.println("Interrupted");
            }
        } catch (IOException e) {
            System.err.println("An error occurred: " + e);
        }
    }
}
