package com.siruoren.jobpromotion;

import com.cloudbees.hudson.plugins.folder.Folder;
import com.siruoren.jobpromotion.engine.PromotionEngine;
import com.siruoren.jobpromotion.service.DeliveryService;
import com.siruoren.jobpromotion.util.JsonResponseUtil;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.model.Action;
import hudson.model.Item;
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

public class FolderPromotionAction implements Action {

    private static final Logger LOGGER = Logger.getLogger(FolderPromotionAction.class.getName());

    private final Folder folder;

    public FolderPromotionAction(@NonNull Folder folder) {
        this.folder = folder;
    }

    @Override
    public String getIconFileName() {
        if (folder.hasPermission(Item.CREATE) && folder.hasPermission(Item.CONFIGURE)) {
            return "symbol-download";
        }
        return null;
    }

    @Override
    public String getDisplayName() {
        return Messages.FolderPromotionAction_displayName();
    }

    @Override
    public String getUrlName() {
        if (folder.hasPermission(Item.CREATE) && folder.hasPermission(Item.CONFIGURE)) {
            return "job-promotion";
        }
        return null;
    }

    public Folder getFolder() {
        return folder;
    }

    public String getFixedFolderPath() {
        return folder.getFullName();
    }

    // ==================== Source Jenkins APIs (交付相关) ====================

    @RequirePOST
    public HttpResponse doDeliverJobs(StaplerRequest2 req) throws IOException, ServletException {
        folder.checkPermission(Item.CREATE);
        folder.checkPermission(Item.CONFIGURE);

        String jobsParam = req.getParameter("jobs");
        String sourceInstance = req.getParameter("sourceInstance");
        return DeliveryService.getInstance().deliverJobs(jobsParam, folder.getFullName(), sourceInstance);
    }

    @RequirePOST
    public HttpResponse doCancelDelivery(StaplerRequest2 req) throws IOException, ServletException {
        folder.checkPermission(Item.CREATE);
        folder.checkPermission(Item.CONFIGURE);

        String idsParam = req.getParameter("ids");
        return DeliveryService.getInstance().cancelDelivery(idsParam);
    }

    @RequirePOST
    public HttpResponse doGetDeliveryList(StaplerRequest2 req) throws IOException, ServletException {
        folder.checkPermission(Item.READ);

        String statusFilter = req.getParameter("status");
        return DeliveryService.getInstance().getDeliveryList(folder.getFullName(), statusFilter);
    }

    // ==================== Callback API ====================

    @RequirePOST
    public HttpResponse doPromotionCallback(StaplerRequest2 req) throws IOException, ServletException {
        folder.checkPermission(Item.CREATE);

        String jobPathsParam = req.getParameter("jobPaths");
        String promotedBy = req.getParameter("promotedBy");
        return DeliveryService.getInstance().handlePromotionCallback(jobPathsParam, promotedBy);
    }

    // ==================== Promotion APIs ====================

    @RequirePOST
    public HttpResponse doGetInstances() throws IOException, ServletException {
        folder.checkPermission(Item.CREATE);
        return JsonResponseUtil.success(DeliveryService.getInstance().getInstancesAsJson());
    }

    @RequirePOST
    public HttpResponse doListLocalJobs(StaplerRequest2 req) throws IOException, ServletException {
        folder.checkPermission(Item.READ);

        try {
            // Recursively list all jobs and sub-folders under current folder
            List<RemoteJobInfo> jobs = new ArrayList<>();
            listJobsInFolder(folder, folder.getFullName(), jobs);
            return JsonResponseUtil.success(jobs);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to list local jobs for folder: " + folder.getFullName(), e);
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
    public HttpResponse doListRemoteJobs(@QueryParameter("sourceInstance") String sourceInstance) throws IOException, ServletException {
        folder.checkPermission(Item.CREATE);
        folder.checkPermission(Item.CONFIGURE);

        try {
            List<RemoteJobInfo> jobs = PromotionEngine.getInstance().fetchRemoteJobs(folder.getFullName(), sourceInstance);
            return JsonResponseUtil.success(jobs);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to list remote jobs for folder: " + folder.getFullName(), e);
            return JsonResponseUtil.error(e.getMessage());
        }
    }

    @RequirePOST
    public HttpResponse doFetchSourceDeliveryList(StaplerRequest2 req) throws IOException, ServletException {
        folder.checkPermission(Item.READ);

        String sourceInstance = req.getParameter("sourceInstance");
        String folderPath = req.getParameter("folderPath");
        if (folderPath == null || folderPath.trim().isEmpty()) {
            folderPath = folder.getFullName();
        }
        return DeliveryService.getInstance().fetchSourceDeliveryList(sourceInstance, folderPath);
    }

    @RequirePOST
    public HttpResponse doPromoteJobs(StaplerRequest2 req) throws IOException, ServletException {
        folder.checkPermission(Item.CREATE);
        folder.checkPermission(Item.CONFIGURE);

        String jobsParam = req.getParameter("jobs");
        boolean forceUpdate = "true".equals(req.getParameter("forceUpdate"));
        String sourceInstance = req.getParameter("sourceInstance");

        if (jobsParam == null || jobsParam.trim().isEmpty()) {
            return JsonResponseUtil.error(Messages.FolderPromotionAction_noJobsSelected());
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
            return JsonResponseUtil.error(Messages.FolderPromotionAction_noJobsSelected());
        }

        Future<Map<String, PromotionResult>> future = PromotionThreadPool.getInstance().submitWithAuth(() -> {
            Map<String, PromotionResult> results = PromotionEngine.getInstance().promoteJobs(
                    jobFullPaths, folder, forceUpdate, folder.getFullName(), sourceInstance);

            String username = Jenkins.getAuthentication2().getName();
            // Collect deliveredBy from delivery items
            String deliveredBy = DeliveryStore.getInstance().getDeliveredByForJobs(jobFullPaths);
            AuditLogService.getInstance().logPromotion(username, sourceInstance, jobFullPaths, forceUpdate, results, deliveredBy);

            DeliveryService.getInstance().callbackSourceJenkins(sourceInstance, jobFullPaths, username, results);

            return results;
        });

        try {
            Map<String, PromotionResult> results = future.get(5, java.util.concurrent.TimeUnit.MINUTES);
            return JsonResponseUtil.success(results);
        } catch (java.util.concurrent.TimeoutException e) {
            LOGGER.log(Level.WARNING, "Promotion task timed out for folder: " + folder.getFullName());
            future.cancel(true);
            return JsonResponseUtil.error("Promotion task timed out after 5 minutes");
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Promotion task failed for folder: " + folder.getFullName(), e);
            return JsonResponseUtil.error(e.getMessage());
        }
    }
}
