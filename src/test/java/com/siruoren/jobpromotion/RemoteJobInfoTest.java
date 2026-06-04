package com.siruoren.jobpromotion;

import org.junit.Test;

import static org.junit.Assert.*;

public class RemoteJobInfoTest {

    @Test
    public void testJobInfo() {
        RemoteJobInfo job = new RemoteJobInfo("my-job", "folder/my-job", false, "blue");
        assertEquals("my-job", job.getName());
        assertEquals("folder/my-job", job.getFullDisplayName());
        assertFalse(job.isFolder());
        assertEquals("blue", job.getColor());
    }

    @Test
    public void testFolderInfo() {
        RemoteJobInfo folder = new RemoteJobInfo("my-folder", "my-folder", true, null);
        assertEquals("my-folder", folder.getName());
        assertEquals("my-folder", folder.getFullDisplayName());
        assertTrue(folder.isFolder());
        assertNull(folder.getColor());
    }

    @Test
    public void testToString() {
        RemoteJobInfo job = new RemoteJobInfo("job1", "path/job1", false, "red");
        String str = job.toString();
        assertTrue(str.contains("job1"));
        assertTrue(str.contains("path/job1"));
        assertTrue(str.contains("folder=false"));
    }

    @Test
    public void testNestedPath() {
        RemoteJobInfo job = new RemoteJobInfo("deep-job", "a/b/c/deep-job", false, "blue");
        assertEquals("deep-job", job.getName());
        assertEquals("a/b/c/deep-job", job.getFullDisplayName());
    }
}
