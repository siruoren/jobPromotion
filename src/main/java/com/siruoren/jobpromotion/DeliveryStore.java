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
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Persistent storage for delivery items.
 * Items are indexed by folderPath for efficient directory-scoped queries.
 */
public class DeliveryStore {

    private static final Logger LOGGER = Logger.getLogger(DeliveryStore.class.getName());
    private static volatile DeliveryStore instance;

    private final List<DeliveryItem> items = Collections.synchronizedList(new ArrayList<>());
    private final File storageFile;

    private DeliveryStore() {
        File pluginDir = new File(Jenkins.get().getRootDir(), "plugins/job-promotion");
        if (!pluginDir.exists()) {
            pluginDir.mkdirs();
        }
        this.storageFile = new File(pluginDir, "delivery-items.json");
        loadFromDisk();
    }

    public static DeliveryStore getInstance() {
        if (instance == null) {
            synchronized (DeliveryStore.class) {
                if (instance == null) {
                    instance = new DeliveryStore();
                }
            }
        }
        return instance;
    }

    /**
     * Add delivery items.
     */
    public void addItems(@NonNull List<DeliveryItem> newItems) {
        synchronized (items) {
            items.addAll(newItems);
        }
        saveToDisk();
    }

    /**
     * Add a single delivery item.
     */
    public void addItem(@NonNull DeliveryItem item) {
        synchronized (items) {
            items.add(item);
        }
        saveToDisk();
    }

    /**
     * Cancel (revoke) delivery items by IDs.
     */
    public int cancelItems(@NonNull List<String> ids) {
        int count;
        synchronized (items) {
            count = 0;
            for (DeliveryItem item : items) {
                if (ids.contains(item.getId()) && item.getStatus() == DeliveryItem.Status.DELIVERED) {
                    item.markCancelled();
                    count++;
                }
            }
        }
        if (count > 0) {
            saveToDisk();
        }
        return count;
    }

    /**
     * Mark items as promoted by IDs.
     */
    public int markPromoted(@NonNull List<String> ids, @NonNull String promotedBy) {
        int count;
        synchronized (items) {
            count = 0;
            for (DeliveryItem item : items) {
                if (ids.contains(item.getId()) && item.getStatus() == DeliveryItem.Status.DELIVERED) {
                    item.markPromoted(promotedBy);
                    count++;
                }
            }
        }
        if (count > 0) {
            saveToDisk();
        }
        return count;
    }

    /**
     * Mark items as promoted by job full paths.
     */
    public int markPromotedByPaths(@NonNull List<String> jobFullPaths, @NonNull String promotedBy) {
        int count;
        synchronized (items) {
            count = 0;
            for (DeliveryItem item : items) {
                if (item.getStatus() == DeliveryItem.Status.DELIVERED && jobFullPaths.contains(item.getJobFullPath())) {
                    item.markPromoted(promotedBy);
                    count++;
                }
            }
        }
        if (count > 0) {
            saveToDisk();
        }
        return count;
    }

    /**
     * Get delivered items for a specific folder path (recursive - includes subdirectories).
     * Only returns DELIVERED status items.
     */
    public List<DeliveryItem> getDeliveredItemsByFolder(@NonNull String folderPath) {
        synchronized (items) {
            return items.stream()
                    .filter(item -> item.getStatus() == DeliveryItem.Status.DELIVERED)
                    .filter(item -> isUnderFolder(item, folderPath))
                    .collect(Collectors.toList());
        }
    }

    /**
     * Get all delivered items (for root page / API).
     */
    public List<DeliveryItem> getDeliveredItems() {
        synchronized (items) {
            return items.stream()
                    .filter(item -> item.getStatus() == DeliveryItem.Status.DELIVERED)
                    .collect(Collectors.toList());
        }
    }

    /**
     * Get all items (including promoted and cancelled) for a specific folder path.
     */
    public List<DeliveryItem> getAllItemsByFolder(@NonNull String folderPath) {
        synchronized (items) {
            return items.stream()
                    .filter(item -> isUnderFolder(item, folderPath))
                    .collect(Collectors.toList());
        }
    }

    /**
     * Get promoted items for a specific folder path.
     */
    public List<DeliveryItem> getPromotedItemsByFolder(@NonNull String folderPath) {
        synchronized (items) {
            return items.stream()
                    .filter(item -> item.getStatus() == DeliveryItem.Status.PROMOTED)
                    .filter(item -> isUnderFolder(item, folderPath))
                    .collect(Collectors.toList());
        }
    }

    /**
     * Get all promoted items (for root page).
     */
    public List<DeliveryItem> getPromotedItems() {
        synchronized (items) {
            return items.stream()
                    .filter(item -> item.getStatus() == DeliveryItem.Status.PROMOTED)
                    .collect(Collectors.toList());
        }
    }

    /**
     * Get all items for root page display.
     */
    public List<DeliveryItem> getAllItems() {
        synchronized (items) {
            return new ArrayList<>(items);
        }
    }

    /**
     * Get item by ID.
     */
    public DeliveryItem getItemById(@NonNull String id) {
        synchronized (items) {
            for (DeliveryItem item : items) {
                if (id.equals(item.getId())) {
                    return item;
                }
            }
        }
        return null;
    }

    /**
     * Check if an item belongs to a folder path (non-recursive, only matches the exact folder).
     */
    private boolean isUnderFolder(DeliveryItem item, String folderPath) {
        String itemFolderPath = item.getFolderPath();
        if (itemFolderPath == null) itemFolderPath = "";
        if (folderPath == null) folderPath = "";
        return itemFolderPath.equals(folderPath);
    }

    /**
     * Convert items to JSON array.
     */
    public static JSONArray toJsonArray(@NonNull List<DeliveryItem> items) {
        JSONArray arr = new JSONArray();
        for (DeliveryItem item : items) {
            arr.add(item.toJson());
        }
        return arr;
    }

    /**
     * Clean old cancelled items.
     */
    public void cleanOldItems() {
        int retentionDays = JobPromotionGlobalConfig.get().getAuditLogRetentionDays();
        long cutoffTime = System.currentTimeMillis() - (long) retentionDays * 24 * 60 * 60 * 1000;

        synchronized (items) {
            boolean removed = items.removeIf(item ->
                    (item.getStatus() == DeliveryItem.Status.CANCELLED
                            || (item.getStatus() == DeliveryItem.Status.PROMOTED && item.getPromotedAt() < cutoffTime))
                            && item.getDeliveredAt() < cutoffTime
            );
            if (removed) {
                saveToDisk();
                LOGGER.log(Level.INFO, "Cleaned old delivery items");
            }
        }
    }

    private void saveToDisk() {
        try {
            JSONArray arr;
            synchronized (items) {
                arr = toJsonArray(new ArrayList<>(items));
            }
            try (FileOutputStream fos = new FileOutputStream(storageFile)) {
                fos.write(arr.toString().getBytes("UTF-8"));
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to save delivery items to disk", e);
        }
    }

    @SuppressWarnings("unchecked")
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
            items.clear();
            for (int i = 0; i < arr.size(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                DeliveryItem item = DeliveryItem.fromJson(obj);
                if (item != null) {
                    items.add(item);
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to load delivery items from disk, starting fresh", e);
        }
    }
}
