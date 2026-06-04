package com.siruoren.jobpromotion;

import edu.umd.cs.findbugs.annotations.NonNull;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

public class AuditLogEntry {

    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    private long timestamp;
    private String username;
    private String action;
    private String sourceInstance;
    private List<String> jobPaths;
    private boolean forceUpdate;
    private int successCount;
    private int failureCount;
    private int skippedCount;

    public AuditLogEntry() {
    }

    public AuditLogEntry(long timestamp, String username, String action, String sourceInstance,
                          List<String> jobPaths, boolean forceUpdate,
                          int successCount, int failureCount, int skippedCount) {
        this.timestamp = timestamp;
        this.username = username;
        this.action = action;
        this.sourceInstance = sourceInstance;
        this.jobPaths = jobPaths;
        this.forceUpdate = forceUpdate;
        this.successCount = successCount;
        this.failureCount = failureCount;
        this.skippedCount = skippedCount;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getSourceInstance() {
        return sourceInstance;
    }

    public void setSourceInstance(String sourceInstance) {
        this.sourceInstance = sourceInstance;
    }

    public List<String> getJobPaths() {
        return jobPaths;
    }

    public void setJobPaths(List<String> jobPaths) {
        this.jobPaths = jobPaths;
    }

    public boolean isForceUpdate() {
        return forceUpdate;
    }

    public void setForceUpdate(boolean forceUpdate) {
        this.forceUpdate = forceUpdate;
    }

    public int getSuccessCount() {
        return successCount;
    }

    public void setSuccessCount(int successCount) {
        this.successCount = successCount;
    }

    public int getFailureCount() {
        return failureCount;
    }

    public void setFailureCount(int failureCount) {
        this.failureCount = failureCount;
    }

    public int getSkippedCount() {
        return skippedCount;
    }

    public void setSkippedCount(int skippedCount) {
        this.skippedCount = skippedCount;
    }

    public String getFormattedTimestamp() {
        return DATE_FORMAT.format(new Date(timestamp));
    }

    public String getJobPathsSummary() {
        if (jobPaths == null || jobPaths.isEmpty()) return "";
        if (jobPaths.size() <= 3) {
            return String.join(", ", jobPaths);
        }
        return jobPaths.get(0) + ", " + jobPaths.get(1) + " ... (" + jobPaths.size() + " jobs)";
    }

    public JSONObject toJson() {
        JSONObject obj = new JSONObject();
        obj.put("timestamp", timestamp);
        obj.put("formattedTimestamp", getFormattedTimestamp());
        obj.put("username", username != null ? username : "");
        obj.put("action", action != null ? action : "");
        obj.put("sourceInstance", sourceInstance != null ? sourceInstance : "");
        obj.put("jobPathsSummary", getJobPathsSummary());
        obj.put("forceUpdate", forceUpdate);
        obj.put("successCount", successCount);
        obj.put("failureCount", failureCount);
        obj.put("skippedCount", skippedCount);
        if (jobPaths != null) {
            JSONArray pathsArr = new JSONArray();
            for (String p : jobPaths) {
                pathsArr.add(p);
            }
            obj.put("jobPaths", pathsArr);
        }
        return obj;
    }

    public static JSONArray toJsonArray(@NonNull List<AuditLogEntry> entries) {
        JSONArray arr = new JSONArray();
        for (AuditLogEntry entry : entries) {
            arr.add(entry.toJson());
        }
        return arr;
    }
}
