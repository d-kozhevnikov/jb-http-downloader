package jb.test.cli;

import jb.test.*;

import java.io.IOException;
import java.net.URI;
import java.nio.file.*;
import java.util.Collection;
import java.util.stream.Collectors;

interface CLITaskOwner {
    void processSuccess(URI uri, Path path);
    void processError(URI uri, Path path, Throwable e);
}

class CLITask extends RandomAccessFileURITask {
    private final CLITaskOwner owner;

    CLITask(URI uri, Path path, CLITaskOwner owner) {
        super(uri, path);
        this.owner = owner;
    }

    @Override
    public void onSuccess() {
        try {
            super.onSuccess();
            owner.processSuccess(getURI(), getPath());

        } catch (IOException e) {
            owner.processError(getURI(), getPath(), e);
        }
    }

    @Override
    public void onFailure(Throwable cause) {
        super.onFailure(cause);
        owner.processError(getURI(), getPath(), cause);
    }
}

public class CLI implements CLITaskOwner {
    private Downloader downloader = null;

    public static void main(String[] args) {
        CmdLineInput input = CmdLineInput.parseCommandLine(args);
        if (input == null) {
            System.out.println(CmdLineInput.getUsage());
            return;
        }
        new CLI().process(input);
    }

    private void process(CmdLineInput input) {
        downloader = new DownloaderImpl();
        try {
            Collection<URITask> tasks =
                    input.getUris().stream()
                            .map(uriAndFile -> new CLITask(uriAndFile.getUri(), uriAndFile.getPath(), this))
                            .collect(Collectors.toList());

            try {
                downloader.run(tasks, input.getNThreads());
            } catch (InterruptedException e) {
                System.out.println("Interrupted");
                Thread.currentThread().interrupt();
            }
        } finally {
            downloader.close();
        }
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

    @Override
    public synchronized void processSuccess(URI uri, Path path) {
        System.out.format("[%s] Downloaded %s to %s\n", getProgressStr(), uri, path);
    }

    @Override
    public synchronized void processError(URI uri, Path path, Throwable e) {
        System.out.format("[%s] Downloading %s to %s failed (%s)\n", getProgressStr(), uri, path, e);
    }
}
