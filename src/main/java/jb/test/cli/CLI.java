package jb.test.cli;

import jb.test.*;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Path;
import java.util.Collection;
import java.util.stream.Collectors;

interface CLITaskOwner {
    void processSuccess(URL url, Path path);

    void processError(URL url, Path path, Throwable e);
}

class CLITask extends RandomAccessFileDownloadingTask {
    private final CLITaskOwner owner;

    CLITask(URL url, Path path, CLITaskOwner owner) {
        super(url, path);
        this.owner = owner;
    }

    @Override
    public void onSuccess() {
        try {
            super.onSuccess();
            owner.processSuccess(getURL(), getPath());

        } catch (IOException e) {
            owner.processError(getURL(), getPath(), e);
        }
    }

    @Override
    public void onFailure(Throwable cause) {
        super.onFailure(cause);
        owner.processError(getURL(), getPath(), cause);
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
            Collection<DownloadingTask> tasks =
                    input.getURLs().stream()
                            .map(urlAndFile -> new CLITask(urlAndFile.getURL(), urlAndFile.getPath(), this))
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
            percentStr = String.format("%.0f", (double) progress.getDownloaded() / progress.getTotal().get() * 100.);
        }
        return String.format("%6sKB/%6sKB (%3s%%)", progress.getDownloaded() / 1024, totalStr, percentStr);
    }

    @Override
    public synchronized void processSuccess(URL url, Path path) {
        System.out.format("[%s] Downloaded %s to %s\n", getProgressStr(), url, path);
    }

    @Override
    public synchronized void processError(URL url, Path path, Throwable e) {
        System.out.format("[%s] Downloading %s to %s failed (%s)\n", getProgressStr(), url, path, e);
    }
}
