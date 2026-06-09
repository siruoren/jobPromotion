package com.siruoren.jobpromotion;

import edu.umd.cs.findbugs.annotations.NonNull;
import jenkins.model.Jenkins;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public class AuditLogService {

    private static final Logger LOGGER = Logger.getLogger(AuditLogService.class.getName());
    private static volatile AuditLogService instance;

    private final List<AuditLogEntry> logs = Collections.synchronizedList(new ArrayList<>());
    private final File storageFile;

    private AuditLogService() {
        File pluginDir = new File(Jenkins.get().getRootDir(), "plugins/job-promotion");
        if (!pluginDir.exists()) {
            pluginDir.mkdirs();
        }
        this.storageFile = new File(pluginDir, "audit-logs.json");
        loadFromDisk();
        cleanOldLogs();
    }

    public static AuditLogService getInstance() {
        if (instance == null) {
            synchronized (AuditLogService.class) {
                if (instance == null) {
                    instance = new AuditLogService();
                }
            }
        }
        return instance;
    }

    /**
     * Log a promotion action.
     */
    public void logPromotion(@NonNull String username, String sourceInstance,
                              @NonNull List<String> jobPaths, boolean forceUpdate,
                              @NonNull Map<String, PromotionResult> results) {
        int successCount = 0;
        int failureCount = 0;
        int skippedCount = 0;
        for (PromotionResult result : results.values()) {
            switch (result.getStatus()) {
                case SUCCESS: successCount++; break;
                case FAILURE: failureCount++; break;
                case SKIPPED: skippedCount++; break;
            }
        }

        AuditLogEntry entry = new AuditLogEntry(
                System.currentTimeMillis(),
                username,
                "PROMOTE",
                sourceInstance != null ? sourceInstance : "",
                new ArrayList<>(jobPaths),
                forceUpdate,
                successCount,
                failureCount,
                skippedCount
        );

        logs.add(entry);
        saveToDisk();
    }

    /**
     * Log a delivery action.
     */
    public void logDelivery(@NonNull String username, String sourceInstance,
                             @NonNull List<String> jobPaths) {
        AuditLogEntry entry = new AuditLogEntry(
                System.currentTimeMillis(),
                username,
                "DELIVER",
                sourceInstance != null ? sourceInstance : "",
                new ArrayList<>(jobPaths),
                false,
                jobPaths.size(),
                0,
                0
        );

        logs.add(entry);
        saveToDisk();
    }

    /**
     * Log a cancel delivery action.
     */
    public void logCancelDelivery(@NonNull String username, @NonNull List<String> ids) {
        AuditLogEntry entry = new AuditLogEntry(
                System.currentTimeMillis(),
                username,
                "CANCEL_DELIVERY",
                "",
                new ArrayList<>(ids),
                false,
                ids.size(),
                0,
                0
        );

        logs.add(entry);
        saveToDisk();
    }

    /**
     * Get logs with pagination.
     */
    public List<AuditLogEntry> getLogs(int page, int pageSize) {
        synchronized (logs) {
            int total = logs.size();
            int fromIndex = Math.max(0, total - page * pageSize);
            int toIndex = Math.min(total, total - (page - 1) * pageSize);
            if (fromIndex >= toIndex) {
                return new ArrayList<>();
            }
            // Return in reverse chronological order
            List<AuditLogEntry> result = new ArrayList<>();
            for (int i = toIndex - 1; i >= fromIndex; i--) {
                result.add(logs.get(i));
            }
            return result;
        }
    }

    /**
     * Get total log count.
     */
    public int getTotalLogCount() {
        return logs.size();
    }

    /**
     * Clean logs older than retention days.
     */
    public void cleanOldLogs() {
        int retentionDays = JobPromotionGlobalConfig.get().getAuditLogRetentionDays();
        long cutoffTime = System.currentTimeMillis() - (long) retentionDays * 24 * 60 * 60 * 1000;

        synchronized (logs) {
            boolean removed = logs.removeIf(entry -> entry.getTimestamp() < cutoffTime);
            if (removed) {
                saveToDisk();
                LOGGER.log(Level.INFO, "Cleaned audit logs older than " + retentionDays + " days");
            }
        }
    }

    private void saveToDisk() {
        try {
            JSONArray arr;
            synchronized (logs) {
                arr = AuditLogEntry.toJsonArray(new ArrayList<>(logs));
            }
            try (FileOutputStream fos = new FileOutputStream(storageFile)) {
                fos.write(arr.toString().getBytes("UTF-8"));
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to save audit logs to disk", e);
        }
    }

    private void loadFromDisk() {
        if (!storageFile.exists()) {
            return;
        }
        try {
            byte[] bytes;
            try (FileInputStream fis = new FileInputStream(storageFile)) {
                bytes = fis.readAllBytes();
            }
            String json = new String(bytes, "UTF-8");
            JSONArray arr = JSONArray.fromObject(json);
            logs.clear();
            for (int i = 0; i < arr.size(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                AuditLogEntry entry = AuditLogEntry.fromJson(obj);
                if (entry != null) {
                    logs.add(entry);
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to load audit logs from disk, starting fresh", e);
        }
    }
}
