package com.siruoren.jobpromotion.util;

import org.junit.Test;

import java.util.LinkedHashSet;
import java.util.Set;

import static org.junit.Assert.*;

public class PathUtilTest {

    @Test
    public void testExtractName() {
        assertEquals("jobName", PathUtil.extractName("a/b/c/jobName"));
        assertEquals("jobName", PathUtil.extractName("jobName"));
        assertEquals("unknown", PathUtil.extractName(""));
        assertEquals("unknown", PathUtil.extractName(null));
    }

    @Test
    public void testGetParentPath() {
        assertEquals("a/b/c", PathUtil.getParentPath("a/b/c/jobName"));
        assertNull(PathUtil.getParentPath("jobName"));
        assertNull(PathUtil.getParentPath(null));
    }

    @Test
    public void testAddParentFolders() {
        Set<String> folders = new LinkedHashSet<>();
        PathUtil.addParentFolders(folders, "a/b/c");
        assertTrue(folders.contains("a"));
        assertTrue(folders.contains("a/b"));
        assertTrue(folders.contains("a/b/c"));
        assertEquals(3, folders.size());
    }

    @Test
    public void testBuildSkipFolders() {
        Set<String> skipFolders = PathUtil.buildSkipFolders("a/b/c");
        assertTrue(skipFolders.contains("a"));
        assertTrue(skipFolders.contains("a/b"));
        assertTrue(skipFolders.contains("a/b/c"));

        assertTrue(PathUtil.buildSkipFolders(null).isEmpty());
        assertTrue(PathUtil.buildSkipFolders("").isEmpty());
    }

    @Test
    public void testParseJobEntry() {
        PathUtil.JobEntry entry = PathUtil.parseJobEntry("a/b/job1|true");
        assertEquals("a/b/job1", entry.fullPath);
        assertTrue(entry.isFolder);

        PathUtil.JobEntry entry2 = PathUtil.parseJobEntry("a/b/job1|false");
        assertFalse(entry2.isFolder);

        PathUtil.JobEntry entry3 = PathUtil.parseJobEntry("a/b/job1");
        assertFalse(entry3.isFolder);
    }

    @Test
    public void testSanitizePathValid() {
        assertEquals("a/b/c", PathUtil.sanitizePath("a/b/c"));
        assertEquals("job1", PathUtil.sanitizePath("job1"));
        assertNull(PathUtil.sanitizePath(null));
        assertEquals("", PathUtil.sanitizePath(""));
    }

    @Test
    public void testSanitizePathTraversal() {
        assertNull("Path traversal with .. should be rejected", PathUtil.sanitizePath("../etc/passwd"));
        assertNull("Path traversal with .. should be rejected", PathUtil.sanitizePath("a/../b"));
        assertNull("Path traversal with . should be rejected", PathUtil.sanitizePath("a/./b"));
    }

    @Test
    public void testSanitizePathNullBytes() {
        assertNull("Null bytes should be rejected", PathUtil.sanitizePath("a/b\0/c"));
    }

    @Test
    public void testSanitizeJobNameValid() {
        assertEquals("my-job", PathUtil.sanitizeJobName("my-job"));
        assertEquals("job1", PathUtil.sanitizeJobName("job1"));
        assertNull(PathUtil.sanitizeJobName(null));
        assertNull(PathUtil.sanitizeJobName(""));
    }

    @Test
    public void testSanitizeJobNameInvalid() {
        assertNull("Path separator should be rejected", PathUtil.sanitizeJobName("a/b"));
        assertNull("Backslash should be rejected", PathUtil.sanitizeJobName("a\\b"));
        assertNull("Double dot should be rejected", PathUtil.sanitizeJobName(".."));
        assertNull("Single dot should be rejected", PathUtil.sanitizeJobName("."));
        assertNull("Null byte should be rejected", PathUtil.sanitizeJobName("job\0name"));
    }
}
