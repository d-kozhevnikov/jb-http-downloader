package jb.test;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

import static org.junit.Assert.*;

public class RandomAccessFileDownloadingTaskTest {
    private Path path;
    private URL url;
    private RandomAccessFileDownloadingTask task;

    @Before
    public void setUp() throws MalformedURLException {
        path = Paths.get("out/testfile");
        url = new URL("http://google.com/");
        task = new RandomAccessFileDownloadingTask(url, path);
    }

    @After
    public void tearDown() throws Exception {
        task.close();
        if (Files.exists(path))
            Files.delete(path);
    }

    @Test
    public void testGet() throws Exception {
        assertEquals(task.getURL(), url);
        assertEquals(task.getPath(), path);
    }

    @Test
    public void testOnStartEmpty() throws Exception {
        task.onStart(Optional.empty());
        assertNotEquals(task.getFileLength(), 0L);
        assertEquals(task.getFileLength(), Files.size(path));
    }

    @Test
    public void testOnStartNonEmpty() throws Exception {
        task.onStart(Optional.of(5L));
        assertEquals(task.getFileLength(), 5L);
        assertEquals(task.getFileLength(), Files.size(path));
    }

    @Test
    public void testOnChunkReceived() throws Exception {
        task.onStart(Optional.of(5L));
        task.onChunkReceived(ByteBuffer.wrap(new byte[]{0, 1, 2}));
        assertEquals(task.getWrittenLength(), 3);
        task.onChunkReceived(ByteBuffer.wrap(new byte[]{3, 4, 5}));
        assertEquals(task.getWrittenLength(), 6);
        assertTrue(task.getFileLength() > 5L);
        assertEquals(task.getFileLength(), Files.size(path));
        task.onSuccess();

        byte[] res = Files.readAllBytes(path);
        assertEquals(6, res.length);
        for (int i = 0; i < res.length; ++i)
            assertEquals(res[i], i);
    }

    @Test
    public void testOnFailure() throws Exception {
        task.onFailure(new IOException());
    }

    @Test
    public void testOnCancel() throws Exception {
        task.onStart(Optional.of(5L));
        task.onChunkReceived(ByteBuffer.wrap(new byte[]{0, 1, 2}));
        task.onCancel();
        assertEquals(task.getWrittenLength(), 0);
    }

    @Test
    public void testOnDiscard() throws Exception {
        task.onStart(Optional.of(5L));
        task.onChunkReceived(ByteBuffer.wrap(new byte[]{0, 1, 2}));
        task.onDiscard();
        assertTrue(Files.notExists(path));
    }
}