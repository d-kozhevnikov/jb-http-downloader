package jb.test;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class CmdLineInput {
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

    public static CmdLineInput parseCommandLine(String[] args) {
        if (args.length < 3)
            return null;

        List<URIAndFile> uris = new ArrayList<>();
        int nThreads = 1;
        for (int i = 0; i < args.length; ) {
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

    public static String getUsage() {
        return "Usage:\n" +
                "    -t <count> -u <URI1> <filename1> -u <URI2> <filename2>...\n" +
                "        saves URIs to corresponding files using <count> threads (count >= 1)";
    }
}
