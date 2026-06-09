package com.siruoren.jobpromotion;

import edu.umd.cs.findbugs.annotations.NonNull;
import net.sf.json.JSONObject;

import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.UUID;

/**
 * Represents a delivery item - a job that has been delivered for promotion.
 */
public class DeliveryItem implements Serializable {

    private static final long serialVersionUID = 1L;

    public enum Status {
        DELIVERED,    // 已交付，等待晋级
        PROMOTED,     // 已晋级
        CANCELLED     // 已撤销
    }

    private String id;
    private String jobName;
    private String jobFullPath;
    private boolean folder;
    private String folderPath;      // 当前目录路径（交付时所在的目录）
    private Status status;
    private String deliveredBy;
    private long deliveredAt;
    private String promotedBy;
    private long promotedAt;
    private String sourceInstance;  // 源 Jenkins 实例名称

    public DeliveryItem() {
        this.id = UUID.randomUUID().toString();
        this.status = Status.DELIVERED;
        this.deliveredAt = System.currentTimeMillis();
    }

    public DeliveryItem(@NonNull String jobName, @NonNull String jobFullPath, boolean folder,
                        @NonNull String folderPath, @NonNull String deliveredBy, String sourceInstance) {
        this.id = UUID.randomUUID().toString();
        this.jobName = jobName;
        this.jobFullPath = jobFullPath;
        this.folder = folder;
        this.folderPath = folderPath;
        this.status = Status.DELIVERED;
        this.deliveredBy = deliveredBy;
        this.deliveredAt = System.currentTimeMillis();
        this.sourceInstance = sourceInstance;
    }

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getJobName() { return jobName; }
    public void setJobName(String jobName) { this.jobName = jobName; }

    public String getJobFullPath() { return jobFullPath; }
    public void setJobFullPath(String jobFullPath) { this.jobFullPath = jobFullPath; }

    public boolean isFolder() { return folder; }
    public void setFolder(boolean folder) { this.folder = folder; }

    public String getFolderPath() { return folderPath; }
    public void setFolderPath(String folderPath) { this.folderPath = folderPath; }

    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }

    public String getDeliveredBy() { return deliveredBy; }
    public void setDeliveredBy(String deliveredBy) { this.deliveredBy = deliveredBy; }

    public long getDeliveredAt() { return deliveredAt; }
    public void setDeliveredAt(long deliveredAt) { this.deliveredAt = deliveredAt; }

    public String getPromotedBy() { return promotedBy; }
    public void setPromotedBy(String promotedBy) { this.promotedBy = promotedBy; }

    public long getPromotedAt() { return promotedAt; }
    public void setPromotedAt(long promotedAt) { this.promotedAt = promotedAt; }

    public String getSourceInstance() { return sourceInstance; }
    public void setSourceInstance(String sourceInstance) { this.sourceInstance = sourceInstance; }

    public String getFormattedDeliveredAt() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(deliveredAt));
    }

    public String getFormattedPromotedAt() {
        return promotedAt > 0 ? new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(promotedAt)) : "";
    }

    public void markPromoted(@NonNull String promotedBy) {
        this.status = Status.PROMOTED;
        this.promotedBy = promotedBy;
        this.promotedAt = System.currentTimeMillis();
    }

    public void markCancelled() {
        this.status = Status.CANCELLED;
    }

    public JSONObject toJson() {
        JSONObject obj = new JSONObject();
        obj.put("id", id != null ? id : "");
        obj.put("jobName", jobName != null ? jobName : "");
        obj.put("jobFullPath", jobFullPath != null ? jobFullPath : "");
        obj.put("folder", folder);
        obj.put("folderPath", folderPath != null ? folderPath : "");
        obj.put("status", status != null ? status.name() : "");
        obj.put("deliveredBy", deliveredBy != null ? deliveredBy : "");
        obj.put("deliveredAt", deliveredAt);
        obj.put("formattedDeliveredAt", getFormattedDeliveredAt());
        obj.put("promotedBy", promotedBy != null ? promotedBy : "");
        obj.put("promotedAt", promotedAt);
        obj.put("formattedPromotedAt", getFormattedPromotedAt());
        obj.put("sourceInstance", sourceInstance != null ? sourceInstance : "");
        return obj;
    }

    public static DeliveryItem fromJson(JSONObject obj) {
        DeliveryItem item = new DeliveryItem();
        item.setId(obj.optString("id", UUID.randomUUID().toString()));
        item.setJobName(obj.optString("jobName", ""));
        item.setJobFullPath(obj.optString("jobFullPath", ""));
        item.setFolder(obj.optBoolean("folder", false));
        item.setFolderPath(obj.optString("folderPath", ""));
        try {
            item.setStatus(Status.valueOf(obj.optString("status", "DELIVERED")));
        } catch (IllegalArgumentException e) {
            item.setStatus(Status.DELIVERED);
        }
        item.setDeliveredBy(obj.optString("deliveredBy", ""));
        item.setDeliveredAt(obj.optLong("deliveredAt", System.currentTimeMillis()));
        item.setPromotedBy(obj.optString("promotedBy", ""));
        item.setPromotedAt(obj.optLong("promotedAt", 0));
        item.setSourceInstance(obj.optString("sourceInstance", ""));
        return item;
    }
}
