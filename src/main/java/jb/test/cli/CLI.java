package jb.test.cli;

import jb.test.Downloader;
import jb.test.InMemoryURITask;
import jb.test.URITask;
import jb.test.DownloaderImpl;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;


class URIAndFile {
    private final URI uri;
    private final Path path;

    URIAndFile(URI uri, Path path) {
        this.uri = uri;
        this.path = path;
    }

    public URI getUri() {
        return uri;
    }

    public Path getPath() {
        return path;
    }
}

class CmdLineInput {
    private final List<URIAndFile> uris;
    private final int nThreads;

    CmdLineInput(List<URIAndFile> uris, int nThreads) {
        this.uris = uris;
        this.nThreads = nThreads;
    }

    public List<URIAndFile> getUris() {
        return uris;
    }

    public int getNThreads() {
        return nThreads;
    }

    static CmdLineInput parseCommandLine(String[] args) {
        if (args.length < 3)
            return null;

        List<URIAndFile> uris = new ArrayList<>();
        int nThreads = 1;
        for (int i = 0; i < args.length;) {
            String command = args[i++];

            switch (command) {
                case "-u":
                    if (i >= args.length - 1)
                        return null;

                    String uriStr = args[i++];
                    String filename = args[i++];
                    URI uri;
                    try {
                        uri = new URI(uriStr);
                    } catch (URISyntaxException e) {
                        return null;
                    }

                    Path path;
                    try {
                        path = Paths.get(filename);
                    } catch (InvalidPathException e) {
                        return null;
                    }

                    uris.add(new URIAndFile(uri, path));
                    break;
                case "-t":
                    if (i >= args.length)
                        return null;

                    try {
                        nThreads = Integer.parseInt(args[i++]);
                    } catch (NumberFormatException e) {
                        return null;
                    }

                    if (nThreads < 1)
                        return null;
                    break;
                default:
                    return null;
            }
        }

        return new CmdLineInput(uris, nThreads);
    }
}

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
                System.out.format("[%3.0f%%] Downloaded %s to %s\n", downloader.getProgress() * 100, getURI(), path);
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
        System.out.format("[%3.0f%%] Downloading %s to %s failed (%s)\n", downloader.getProgress() * 100, getURI(), path, e);
    }
}

public class CLI {

    public static void main(String[] args) {
        CmdLineInput input = CmdLineInput.parseCommandLine(args);
        if (input == null) {
            printHelp();
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

    private static void printHelp() {
        System.out.println(
                "Usage:\n" +
                        "    -t <count> -u <URI1> <filename1> -u <URI2> <filename2>...\n" +
                        "        saves URIs to corresponding files using <count> threads (count >= 1)"
        );
    }
}
