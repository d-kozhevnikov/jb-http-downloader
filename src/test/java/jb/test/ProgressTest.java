package jb.test;

import org.junit.Before;
import org.junit.Test;

import java.util.Optional;

import static org.junit.Assert.*;

public class ProgressTest {
    private Progress progress;

    @Before
    public void setUp() throws Exception {
        progress = new Progress(5, Optional.empty());
    }

    @Test
    public void testGetDownloaded() throws Exception {
        assertEquals(progress.getDownloaded(), 5);
    }

    @Test
    public void testGetTotal() throws Exception {
        assertTrue(!progress.getTotal().isPresent());
    }
}