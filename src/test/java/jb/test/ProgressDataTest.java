package jb.test;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

public class ProgressDataTest {

    private ProgressData data;

    @Test
    public void testInitial() throws Exception {
        assertEquals(data.getDownloadedBytes(), 0L);
        assertFalse(data.getTotalBytes().isPresent());
    }

    @Test
    public void testAddDownloadedBytes() throws Exception {
        data.addDownloadedBytes(0L);
        assertEquals(data.getDownloadedBytes(), 0L);
        data.addDownloadedBytes(5L);
        assertEquals(data.getDownloadedBytes(), 5L);
        data.addDownloadedBytes(10000000000L);
        assertEquals(data.getDownloadedBytes(), 10000000005L);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testAddDownloadedBytesNegative() throws Exception {
        data.addDownloadedBytes(-1L);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testAddDownloadedBytesTooBig() throws Exception {
        data.setTotalBytes(5L);
        data.addDownloadedBytes(10L);
    }

    @Test
    public void testResetDownloadedBytes() throws Exception {
        data.addDownloadedBytes(10);
        data.resetDownloadedBytes();
        assertEquals(data.getDownloadedBytes(), 0L);
    }

    @Test
    public void testSetTotalBytes() throws Exception {
        data.addDownloadedBytes(5L);

        data.setTotalBytes(10L);
        assertTrue(data.getTotalBytes().isPresent());
        assertEquals(data.getTotalBytes().get(), Long.valueOf(10L));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testSetTotalBytesTooSmall() throws Exception {
        data.setTotalBytes(15L);
        data.addDownloadedBytes(10L);
        data.setTotalBytes(5L);
    }

    @Before
    public void setUp() throws Exception {
        data = new ProgressData();
    }
}