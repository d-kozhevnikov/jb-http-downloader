package jb.test.cli;

import java.net.URI;
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
    public void testParseCommandLineUris() throws Exception {
        String[] invalidUri = {"-u", "invalid|uri", "file"};
        assertNull(CmdLineInput.parseCommandLine(invalidUri));

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

        assertEquals(result.getUris().size(), 2);

        List<URI> expectedURIs = Arrays.asList(new URI("http://jetbrains.com/"), new URI("https://www.jetbrains.com/clion/"));
        List<Path> expectedPaths = Arrays.asList(Paths.get("ok"), Paths.get("okok"));
        Iterator<URIAndFile> itResult = result.getUris().iterator();
        Iterator<URI> itURIs = expectedURIs.iterator();
        Iterator<Path> itPaths = expectedPaths.iterator();

        while (itResult.hasNext() && itURIs.hasNext() && itPaths.hasNext()) {
            URIAndFile res = itResult.next();
            assertEquals(res.getPath(), itPaths.next());
            assertEquals(res.getUri(), itURIs.next());
        }
    }
}