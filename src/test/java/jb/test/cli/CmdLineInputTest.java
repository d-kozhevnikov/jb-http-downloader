package jb.test.cli;

import jb.test.CmdLineInput;
import jb.test.URLAndFile;

import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import static org.junit.Assert.*;

public class CmdLineInputTest {

    @org.junit.Test
    public void testParseCommandLineEmpty() throws Exception {
        String[] args = {};
        assertNull(CmdLineInput.parseCommandLine(args));
    }

    @org.junit.Test
    public void testParseCommandLineInvalidThreads() throws Exception {
        String[] noCount = {"-t"};
        assertNull(CmdLineInput.parseCommandLine(noCount));

        String[] notInteger = {"-t", "hi"};
        assertNull(CmdLineInput.parseCommandLine(notInteger));

        String[] zero = {"-t", "0"};
        assertNull(CmdLineInput.parseCommandLine(zero));
    }

    @org.junit.Test
    public void testParseCommandLineURLs() throws Exception {
        String[] invalidURL = {"-u", "invalid|url", "file"};
        assertNull(CmdLineInput.parseCommandLine(invalidURL));

        String[] noFile = {"-u", "http://jetbrains.com/"};
        assertNull(CmdLineInput.parseCommandLine(noFile));

        String[] noFile2 = {"-u", "http://jetbrains.com/", "ok", "-u", "http://eclipse.org/"};
        assertNull(CmdLineInput.parseCommandLine(noFile2));

        String[] tooManyArguments = {"-u", "http://eclipse.org/", "fail", "fail"};
        assertNull(CmdLineInput.parseCommandLine(tooManyArguments));

        String[] zero = {"-u"};
        assertNull(CmdLineInput.parseCommandLine(zero));
    }

    @org.junit.Test
    public void testParseCommandLineFull() throws Exception {
        String[] ok = {"-u", "http://jetbrains.com/", "ok", "-u", "https://www.jetbrains.com/clion/", "okok", "-t", "3"};
        CmdLineInput result = CmdLineInput.parseCommandLine(ok);
        assertNotNull(result);

        assertEquals(result.getNThreads(), 3);

        assertEquals(result.getURLs().size(), 2);

        List<URL> expectedURLs = Arrays.asList(new URL("http://jetbrains.com/"), new URL("https://www.jetbrains.com/clion/"));
        List<Path> expectedPaths = Arrays.asList(Paths.get("ok"), Paths.get("okok"));
        Iterator<URLAndFile> itResult = result.getURLs().iterator();
        Iterator<URL> itURLs = expectedURLs.iterator();
        Iterator<Path> itPaths = expectedPaths.iterator();

        while (itResult.hasNext() && itURLs.hasNext() && itPaths.hasNext()) {
            URLAndFile res = itResult.next();
            assertEquals(res.getPath(), itPaths.next());
            assertEquals(res.getURL(), itURLs.next());
        }
    }
}