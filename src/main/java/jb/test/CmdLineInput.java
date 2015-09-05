package jb.test;

import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class CmdLineInput {
    private final List<URLAndFile> urls;
    private final int nThreads;

    CmdLineInput(List<URLAndFile> urls, int nThreads) {
        this.urls = urls;
        this.nThreads = nThreads;
    }

    public List<URLAndFile> getURLs() {
        return urls;
    }

    public int getNThreads() {
        return nThreads;
    }

    public static CmdLineInput parseCommandLine(String[] args) {
        if (args.length < 3)
            return null;

        List<URLAndFile> urls = new ArrayList<>();
        int nThreads = 1;
        for (int i = 0; i < args.length; ) {
            String command = args[i++];

            switch (command) {
                case "-u":
                    if (i >= args.length - 1)
                        return null;

                    String urlStr = args[i++];
                    String filename = args[i++];
                    URL url;
                    try {
                        url = new URL(urlStr);
                    } catch (MalformedURLException e) {
                        return null;
                    }

                    Path path;
                    try {
                        path = Paths.get(filename);
                    } catch (InvalidPathException e) {
                        return null;
                    }

                    urls.add(new URLAndFile(url, path));
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

        return new CmdLineInput(urls, nThreads);
    }

    public static String getUsage() {
        return "Usage:\n" +
                "    -t <count> -u <URL1> <filename1> -u <URL2> <filename2>...\n" +
                "        saves URLs to corresponding files using <count> threads (count >= 1)";
    }
}
