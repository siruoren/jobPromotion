package com.siruoren.jobpromotion.service;

import com.siruoren.jobpromotion.AuditLogService;
import com.siruoren.jobpromotion.DeliveryItem;
import com.siruoren.jobpromotion.DeliveryStore;
import com.siruoren.jobpromotion.JenkinsRemoteClient;
import com.siruoren.jobpromotion.JobPromotionGlobalConfig;
import com.siruoren.jobpromotion.SourceJenkinsInstance;
import com.siruoren.jobpromotion.util.PathUtil;
import edu.umd.cs.findbugs.annotations.NonNull;
import jenkins.model.Jenkins;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.HttpResponse;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Service layer for delivery-related business logic.
 * Consolidates common delivery operations shared between FolderPromotionAction and RootPromotionAction.
 */
public class DeliveryService {

    private static final Logger LOGGER = Logger.getLogger(DeliveryService.class.getName());
    private static final DeliveryService INSTANCE = new DeliveryService();

    private DeliveryService() {
    }

    public static DeliveryService getInstance() {
        return INSTANCE;
    }

    /**
     * Deliver jobs to delivery list.
     *
     * @param jobEntries      comma-separated "fullPath|isFolder" entries
     * @param folderPath      current folder path (empty string for root)
     * @param sourceInstance  source Jenkins instance name
     * @return HttpResponse with delivery result
     */
    public HttpResponse deliverJobs(@NonNull String jobEntries, @NonNull String folderPath, String sourceInstance) {
        if (jobEntries == null || jobEntries.trim().isEmpty()) {
            return errorResponse("No jobs selected");
        }

        String username = Jenkins.getAuthentication2().getName();
        String[] entries = jobEntries.split(",");
        List<DeliveryItem> newItems = new ArrayList<>();

        for (String entry : entries) {
            String trimmed = entry.trim();
            if (trimmed.isEmpty()) continue;

            PathUtil.JobEntry parsed = PathUtil.parseJobEntry(trimmed);

            // Validate path to prevent path traversal
            if (PathUtil.sanitizePath(parsed.fullPath) == null) {
                continue;
            }

            String jobName = PathUtil.extractName(parsed.fullPath);

            // Check if already delivered
            boolean alreadyDelivered;
            if (folderPath.isEmpty()) {
                alreadyDelivered = DeliveryStore.getInstance().getDeliveredItems().stream()
                        .anyMatch(item -> item.getJobFullPath().equals(parsed.fullPath) && item.getStatus() == DeliveryItem.Status.DELIVERED);
            } else {
                alreadyDelivered = DeliveryStore.getInstance().getDeliveredItemsByFolder(folderPath).stream()
                        .anyMatch(item -> item.getJobFullPath().equals(parsed.fullPath) && item.getStatus() == DeliveryItem.Status.DELIVERED);
            }

            if (!alreadyDelivered) {
                DeliveryItem item = new DeliveryItem(jobName, parsed.fullPath, parsed.isFolder, folderPath, username, sourceInstance);
                newItems.add(item);
            }
        }

        if (newItems.isEmpty()) {
            return errorResponse("No jobs selected");
        }

        DeliveryStore.getInstance().addItems(newItems);

        // Log audit
        List<String> paths = new ArrayList<>();
        for (DeliveryItem item : newItems) {
            paths.add(item.getJobFullPath());
        }
        AuditLogService.getInstance().logDelivery(username, sourceInstance, paths);

        JSONObject result = new JSONObject();
        result.put("deliveredCount", newItems.size());
        result.put("items", DeliveryStore.toJsonArray(newItems));
        return successResponse(result);
    }

    /**
     * Cancel (revoke) delivery for selected items.
     */
    public HttpResponse cancelDelivery(@NonNull String idsParam) {
        if (idsParam == null || idsParam.trim().isEmpty()) {
            return errorResponse("No items selected");
        }

        String[] ids = idsParam.split(",");
        List<String> idList = new ArrayList<>();
        for (String id : ids) {
            String trimmed = id.trim();
            if (!trimmed.isEmpty()) {
                idList.add(trimmed);
            }
        }

        int count = DeliveryStore.getInstance().cancelItems(idList);

        String username = Jenkins.getAuthentication2().getName();
        AuditLogService.getInstance().logCancelDelivery(username, idList);

        JSONObject result = new JSONObject();
        result.put("cancelledCount", count);
        return successResponse(result);
    }

    /**
     * Get delivery list for a specific folder (non-recursive, only current directory).
     */
    public HttpResponse getDeliveryList(@NonNull String folderPath, String statusFilter) {
        List<DeliveryItem> items;
        String effectiveFolder = folderPath != null ? folderPath : "";

        if ("promoted".equalsIgnoreCase(statusFilter)) {
            items = DeliveryStore.getInstance().getPromotedItemsByFolder(effectiveFolder);
        } else if ("all".equalsIgnoreCase(statusFilter)) {
            items = DeliveryStore.getInstance().getAllItemsByFolder(effectiveFolder);
        } else {
            items = DeliveryStore.getInstance().getDeliveredItemsByFolder(effectiveFolder);
        }

        JSONObject result = new JSONObject();
        result.put("items", DeliveryStore.toJsonArray(items));
        result.put("folderPath", effectiveFolder);
        return successResponse(result);
    }

    /**
     * Handle promotion callback from remote Jenkins.
     */
    public HttpResponse handlePromotionCallback(@NonNull String jobPathsParam, String promotedBy) {
        if (jobPathsParam == null || jobPathsParam.trim().isEmpty()) {
            return errorResponse("No job paths provided");
        }

        String[] jobPaths = jobPathsParam.split(",");
        List<String> pathList = new ArrayList<>();
        for (String path : jobPaths) {
            String trimmed = path.trim();
            if (!trimmed.isEmpty()) {
                pathList.add(trimmed);
            }
        }

        int count = DeliveryStore.getInstance().markPromotedByPaths(pathList, promotedBy != null ? promotedBy : "remote");

        LOGGER.log(Level.INFO, "Promotion callback received: " + count + " items marked as promoted");

        JSONObject result = new JSONObject();
        result.put("promotedCount", count);
        return successResponse(result);
    }

    /**
     * Fetch delivery list from source Jenkins (remote side).
     */
    public HttpResponse fetchSourceDeliveryList(String sourceInstance, String folderPath) {
        if (folderPath == null) folderPath = "";

        JobPromotionGlobalConfig config = JobPromotionGlobalConfig.get();
        SourceJenkinsInstance instance = config.getInstanceByName(sourceInstance);
        if (instance == null) {
            return errorResponse("Source Jenkins instance not configured");
        }

        String[] cred = config.resolveCredentialsForInstance(instance);
        if (cred == null) {
            return errorResponse("Credentials not configured for source Jenkins instance");
        }

        try {
            JenkinsRemoteClient client = new JenkinsRemoteClient(instance.getUrl().trim(), cred[0], cred[1]);
            String jsonResponse = client.fetchDeliveryList(folderPath);
            // Return raw JSON from source Jenkins
            return new org.kohsuke.stapler.HttpResponse() {
                @Override
                public void generateResponse(org.kohsuke.stapler.StaplerRequest2 req, org.kohsuke.stapler.StaplerResponse2 rsp, @NonNull Object node) throws IOException {
                    rsp.setContentType("application/json;charset=UTF-8");
                    rsp.setStatus(200);
                    try (PrintWriter w = rsp.getWriter()) {
                        w.write(jsonResponse);
                    }
                }
            };
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to fetch delivery list from source Jenkins", e);
            return errorResponse(e.getMessage());
        }
    }

    /**
     * Callback to source Jenkins to mark promoted items.
     */
    public void callbackSourceJenkins(String sourceInstanceName, List<String> jobFullPaths, String promotedBy, java.util.Map<String, com.siruoren.jobpromotion.PromotionResult> results) {
        try {
            JobPromotionGlobalConfig config = JobPromotionGlobalConfig.get();
            SourceJenkinsInstance instance = config.getInstanceByName(sourceInstanceName);
            if (instance == null) {
                LOGGER.log(Level.WARNING, "Cannot callback: source instance not found: " + sourceInstanceName);
                return;
            }

            String[] cred = config.resolveCredentialsForInstance(instance);
            if (cred == null) {
                LOGGER.log(Level.WARNING, "Cannot callback: credentials not configured for: " + sourceInstanceName);
                return;
            }

            // Only callback for successfully promoted jobs
            List<String> successPaths = new ArrayList<>();
            for (java.util.Map.Entry<String, com.siruoren.jobpromotion.PromotionResult> entry : results.entrySet()) {
                if (entry.getValue().isSuccess()) {
                    successPaths.add(entry.getKey());
                }
            }

            if (successPaths.isEmpty()) {
                return;
            }

            JenkinsRemoteClient client = new JenkinsRemoteClient(instance.getUrl().trim(), cred[0], cred[1]);
            client.notifyPromotionCallback(successPaths, promotedBy);

            LOGGER.log(Level.INFO, "Promotion callback sent to source Jenkins: " + sourceInstanceName);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to callback source Jenkins for promotion notification", e);
        }
    }

    /**
     * Get configured source Jenkins instances as JSON array.
     */
    public JSONArray getInstancesAsJson() {
        JobPromotionGlobalConfig config = JobPromotionGlobalConfig.get();
        JSONArray arr = new JSONArray();
        for (SourceJenkinsInstance inst : config.getInstances()) {
            JSONObject obj = new JSONObject();
            obj.put("name", inst.getName() != null ? inst.getName() : "");
            obj.put("url", inst.getUrl() != null ? inst.getUrl() : "");
            arr.add(obj);
        }
        return arr;
    }

    private HttpResponse successResponse(Object data) {
        return com.siruoren.jobpromotion.util.JsonResponseUtil.success(data);
    }

    private HttpResponse errorResponse(String message) {
        return com.siruoren.jobpromotion.util.JsonResponseUtil.error(message);
    }
}
