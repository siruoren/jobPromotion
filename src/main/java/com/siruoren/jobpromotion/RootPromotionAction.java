package com.siruoren.jobpromotion;

import com.cloudbees.hudson.plugins.folder.Folder;
import com.siruoren.jobpromotion.engine.PromotionEngine;
import com.siruoren.jobpromotion.service.DeliveryService;
import com.siruoren.jobpromotion.util.JsonResponseUtil;
import com.siruoren.jobpromotion.util.VersionUtil;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.Item;
import hudson.model.RootAction;
import jenkins.model.Jenkins;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest2;
import org.kohsuke.stapler.interceptor.RequirePOST;

import javax.servlet.ServletException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.logging.Level;
import java.util.logging.Logger;

@Extension
public class RootPromotionAction implements RootAction {

    private static final Logger LOGGER = Logger.getLogger(RootPromotionAction.class.getName());

    @Override
    public String getIconFileName() {
        if (Jenkins.get().hasPermission(Jenkins.ADMINISTER)) {
            return "symbol-download";
        }
        return null;
    }

    @Override
    public String getDisplayName() {
        return Messages.RootPromotionAction_displayName();
    }

    @Override
    public String getUrlName() {
        if (Jenkins.get().hasPermission(Jenkins.ADMINISTER)) {
            return "job-promotion";
        }
        return null;
    }

    // ==================== Version API ====================

    @RequirePOST
    public HttpResponse doGetVersion() throws IOException, ServletException {
        Jenkins.get().checkPermission(Jenkins.READ);
        net.sf.json.JSONObject result = new net.sf.json.JSONObject();
        result.put("version", VersionUtil.getVersion());
        return JsonResponseUtil.success(result);
    }

    // ==================== Delivery APIs ====================

    @RequirePOST
    public HttpResponse doGetDeliveryList(StaplerRequest2 req) throws IOException, ServletException {
        Jenkins.get().checkPermission(Jenkins.ADMINISTER);

        String folderPath = req.getParameter("folderPath");
        String statusFilter = req.getParameter("status");
        return DeliveryService.getInstance().getDeliveryList(
                folderPath != null ? folderPath : "", statusFilter);
    }

    @RequirePOST
    public HttpResponse doPromotionCallback(StaplerRequest2 req) throws IOException, ServletException {
        Jenkins.get().checkPermission(Jenkins.ADMINISTER);

        String jobPathsParam = req.getParameter("jobPaths");
        String promotedBy = req.getParameter("promotedBy");
        return DeliveryService.getInstance().handlePromotionCallback(jobPathsParam, promotedBy);
    }

    // ==================== Source Jenkins APIs ====================

    @RequirePOST
    public HttpResponse doDeliverJobs(StaplerRequest2 req) throws IOException, ServletException {
        Jenkins.get().checkPermission(Jenkins.ADMINISTER);

        String jobsParam = req.getParameter("jobs");
        String sourceInstance = req.getParameter("sourceInstance");
        return DeliveryService.getInstance().deliverJobs(jobsParam, "", sourceInstance);
    }

    @RequirePOST
    public HttpResponse doCancelDelivery(StaplerRequest2 req) throws IOException, ServletException {
        Jenkins.get().checkPermission(Jenkins.ADMINISTER);

        String idsParam = req.getParameter("ids");
        return DeliveryService.getInstance().cancelDelivery(idsParam);
    }

    // ==================== Remote Jenkins APIs ====================

    @RequirePOST
    public HttpResponse doFetchSourceDeliveryList(StaplerRequest2 req) throws IOException, ServletException {
        Jenkins.get().checkPermission(Jenkins.ADMINISTER);

        String sourceInstance = req.getParameter("sourceInstance");
        String folderPath = req.getParameter("folderPath");
        return DeliveryService.getInstance().fetchSourceDeliveryList(sourceInstance, folderPath != null ? folderPath : "");
    }

    // ==================== Instance & Job Listing APIs ====================

    @RequirePOST
    public HttpResponse doGetInstances() throws IOException, ServletException {
        Jenkins.get().checkPermission(Jenkins.ADMINISTER);
        return JsonResponseUtil.success(DeliveryService.getInstance().getInstancesAsJson());
    }

    @RequirePOST
    public HttpResponse doListLocalJobs(StaplerRequest2 req) throws IOException, ServletException {
        Jenkins.get().checkPermission(Jenkins.READ);

        String folderPath = req.getParameter("folderPath");

        try {
            // Recursively list all jobs and sub-folders
            List<RemoteJobInfo> jobs = new ArrayList<>();
            if (folderPath != null && !folderPath.trim().isEmpty()) {
                Item item = Jenkins.get().getItemByFullName(folderPath);
                if (item instanceof Folder) {
                    listJobsInFolder((Folder) item, folderPath, jobs);
                }
            } else {
                // Root level: load all Jenkins jobs recursively
                // Only iterate top-level items, then recurse into folders
                for (Item item : Jenkins.get().getAllItems()) {
                    String itemPath = item.getFullName();
                    // Skip nested items - they will be visited via folder recursion
                    if (itemPath.contains("/")) continue;
                    if (item instanceof Folder) {
                        jobs.add(new RemoteJobInfo(item.getName(), itemPath, true, null));
                        listJobsInFolder((Folder) item, itemPath, jobs);
                    } else if (item instanceof hudson.model.Job) {
                        jobs.add(new RemoteJobInfo(item.getName(), itemPath, false, null));
                    }
                }
            }
            return JsonResponseUtil.success(jobs);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to list local jobs", e);
            return JsonResponseUtil.error(e.getMessage());
        }
    }

    private void listJobsInFolder(Folder f, String currentPath, List<RemoteJobInfo> jobs) {
        for (Item item : f.getItems()) {
            String itemPath = currentPath.isEmpty() ? item.getName() : currentPath + "/" + item.getName();
            if (item instanceof Folder) {
                jobs.add(new RemoteJobInfo(item.getName(), itemPath, true, null));
                listJobsInFolder((Folder) item, itemPath, jobs);
            } else if (item instanceof hudson.model.Job) {
                jobs.add(new RemoteJobInfo(item.getName(), itemPath, false, null));
            }
        }
    }

    @RequirePOST
    public HttpResponse doListRemoteJobs(@QueryParameter("folderPath") String folderPath,
                                          @QueryParameter("sourceInstance") String sourceInstance) throws IOException, ServletException {
        Jenkins.get().checkPermission(Jenkins.ADMINISTER);

        try {
            List<RemoteJobInfo> jobs = PromotionEngine.getInstance().fetchRemoteJobs(folderPath, sourceInstance);
            return JsonResponseUtil.success(jobs);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to list remote jobs", e);
            return JsonResponseUtil.error(e.getMessage());
        }
    }

    @RequirePOST
    public HttpResponse doPromoteJobs(StaplerRequest2 req) throws IOException, ServletException {
        Jenkins.get().checkPermission(Jenkins.ADMINISTER);

        String jobsParam = req.getParameter("jobs");
        boolean forceUpdate = "true".equals(req.getParameter("forceUpdate"));
        String sourceInstance = req.getParameter("sourceInstance");

        if (jobsParam == null || jobsParam.trim().isEmpty()) {
            return JsonResponseUtil.error(Messages.RootPromotionAction_noJobsSelected());
        }

        String[] jobPaths = jobsParam.split(",");
        List<String> jobFullPaths = new ArrayList<>();
        for (String path : jobPaths) {
            String trimmed = path.trim();
            if (!trimmed.isEmpty()) {
                jobFullPaths.add(trimmed);
            }
        }

        if (jobFullPaths.isEmpty()) {
            return JsonResponseUtil.error(Messages.RootPromotionAction_noJobsSelected());
        }

        Future<Map<String, PromotionResult>> future = PromotionThreadPool.getInstance().submitWithAuth(() -> {
            Map<String, PromotionResult> results = PromotionEngine.getInstance().promoteJobs(
                    jobFullPaths, Jenkins.get(), forceUpdate, null, sourceInstance);

            String username = Jenkins.getAuthentication2().getName();
            AuditLogService.getInstance().logPromotion(username, sourceInstance, jobFullPaths, forceUpdate, results);

            DeliveryService.getInstance().callbackSourceJenkins(sourceInstance, jobFullPaths, username, results);

            return results;
        });

        try {
            Map<String, PromotionResult> results = future.get(5, java.util.concurrent.TimeUnit.MINUTES);
            return JsonResponseUtil.success(results);
        } catch (java.util.concurrent.TimeoutException e) {
            LOGGER.log(Level.WARNING, "Promotion task timed out");
            future.cancel(true);
            return JsonResponseUtil.error("Promotion task timed out after 5 minutes");
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Promotion task failed", e);
            return JsonResponseUtil.error(e.getMessage());
        }
    }

    // ==================== Audit Log APIs ====================

    @RequirePOST
    public HttpResponse doGetAuditLogs(StaplerRequest2 req) throws IOException, ServletException {
        Jenkins.get().checkPermission(Jenkins.ADMINISTER);

        int page = 1;
        int pageSize = 20;
        String pageParam = req.getParameter("page");
        String pageSizeParam = req.getParameter("pageSize");
        try {
            if (pageParam != null && !pageParam.isEmpty()) page = Integer.parseInt(pageParam);
            if (pageSizeParam != null && !pageSizeParam.isEmpty()) pageSize = Integer.parseInt(pageSizeParam);
        } catch (NumberFormatException e) {
            // use defaults
        }
        if (page < 1) page = 1;
        if (pageSize < 1 || pageSize > 100) pageSize = 20;

        AuditLogService auditService = AuditLogService.getInstance();
        List<AuditLogEntry> logs = auditService.getLogs(page, pageSize);
        int total = auditService.getTotalLogCount();

        int retentionDays = JobPromotionGlobalConfig.get().getAuditLogRetentionDays();

        net.sf.json.JSONObject result = new net.sf.json.JSONObject();
        result.put("logs", AuditLogEntry.toJsonArray(logs));
        result.put("total", total);
        result.put("page", page);
        result.put("pageSize", pageSize);
        result.put("retentionDays", retentionDays);
        return JsonResponseUtil.success(result);
    }

    @RequirePOST
    public HttpResponse doUpdateAuditLogRetention(StaplerRequest2 req) throws IOException, ServletException {
        Jenkins.get().checkPermission(Jenkins.ADMINISTER);

        String retentionDaysParam = req.getParameter("retentionDays");
        int retentionDays = 30;
        try {
            if (retentionDaysParam != null && !retentionDaysParam.isEmpty()) {
                retentionDays = Integer.parseInt(retentionDaysParam);
            }
        } catch (NumberFormatException e) {
            return JsonResponseUtil.error("Invalid retention days value");
        }
        if (retentionDays < 1) {
            return JsonResponseUtil.error("Retention days must be at least 1");
        }

        JobPromotionGlobalConfig config = JobPromotionGlobalConfig.get();
        config.setAuditLogRetentionDays(retentionDays);
        config.save();

        AuditLogService.getInstance().cleanOldLogs();

        net.sf.json.JSONObject result = new net.sf.json.JSONObject();
        result.put("retentionDays", retentionDays);
        return JsonResponseUtil.success(result);
    }
}
