package com.siruoren.jobpromotion.util;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Utility class for path manipulation operations.
 */
public class PathUtil {

    private PathUtil() {
    }

    /**
     * Validate a path to prevent path traversal attacks.
     * Rejects paths containing "..", null bytes, or absolute paths.
     * Returns the sanitized path or null if invalid.
     */
    public static String sanitizePath(String path) {
        if (path == null || path.isEmpty()) {
            return path;
        }
        // Reject null bytes
        if (path.indexOf('\0') >= 0) {
            return null;
        }
        // Reject path traversal
        String[] parts = path.split("/");
        for (String part : parts) {
            if ("..".equals(part) || ".".equals(part)) {
                return null;
            }
            if (part.isEmpty() && parts.length > 1) {
                return null; // double slash or leading slash
            }
        }
        return path;
    }

    /**
     * Validate and sanitize a job name.
     * Rejects names with path separators, null bytes, or traversal patterns.
     */
    public static String sanitizeJobName(String name) {
        if (name == null || name.isEmpty()) {
            return null;
        }
        if (name.indexOf('\0') >= 0 || name.contains("..") || name.contains("/") || name.contains("\\") || name.equals(".") || name.equals("..")) {
            return null;
        }
        return name;
    }

    /**
     * Extract the job/folder name from a full path.
     * e.g., "a/b/c/jobName" -> "jobName"
     */
    public static String extractName(String fullPath) {
        if (fullPath == null || fullPath.isEmpty()) {
            return "unknown";
        }
        String[] parts = fullPath.split("/");
        return parts[parts.length - 1];
    }

    /**
     * Get the parent path from a full path.
     * e.g., "a/b/c/jobName" -> "a/b/c"
     */
    public static String getParentPath(String fullPath) {
        if (fullPath == null || !fullPath.contains("/")) {
            return null;
        }
        return fullPath.substring(0, fullPath.lastIndexOf("/"));
    }

    /**
     * Add a path and all its parent paths to the set.
     * e.g., "a/b/c" adds "a", "a/b", "a/b/c"
     */
    public static void addParentFolders(Set<String> folders, String path) {
        String[] parts = path.split("/");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) sb.append("/");
            sb.append(parts[i]);
            folders.add(sb.toString());
        }
    }

    /**
     * Build the set of folder paths to skip (current folder and its parents).
     */
    public static Set<String> buildSkipFolders(String currentFolderPath) {
        Set<String> skipFolders = new LinkedHashSet<>();
        if (currentFolderPath != null && !currentFolderPath.isEmpty()) {
            addParentFolders(skipFolders, currentFolderPath);
        }
        return skipFolders;
    }

    /**
     * Parse a job entry string "fullPath|isFolder" into its components.
     */
    public static JobEntry parseJobEntry(String entry) {
        String[] parts = entry.split("\\|");
        String fullPath = parts[0].trim();
        boolean isFolder = parts.length > 1 && "true".equalsIgnoreCase(parts[1].trim());
        return new JobEntry(fullPath, isFolder);
    }

    /**
     * Simple data class for parsed job entries.
     */
    public static class JobEntry {
        public final String fullPath;
        public final boolean isFolder;

        public JobEntry(String fullPath, boolean isFolder) {
            this.fullPath = fullPath;
            this.isFolder = isFolder;
        }
    }
}
