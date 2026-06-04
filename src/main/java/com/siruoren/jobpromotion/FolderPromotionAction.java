package com.siruoren.jobpromotion;

import com.cloudbees.hudson.plugins.folder.Folder;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.model.Action;
import hudson.model.Item;
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
            return "symbol-promote plugin-job-promotion";
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

    /**
     * Return the list of configured source Jenkins instances for the frontend dropdown.
     */
    @RequirePOST
    public HttpResponse doGetInstances() throws IOException, ServletException {
        folder.checkPermission(Item.CREATE);

        JobPromotionGlobalConfig config = JobPromotionGlobalConfig.get();
        net.sf.json.JSONArray arr = new net.sf.json.JSONArray();
        for (JobPromotionGlobalConfig.SourceJenkinsInstance inst : config.getInstances()) {
            net.sf.json.JSONObject obj = new net.sf.json.JSONObject();
            obj.put("name", inst.getName() != null ? inst.getName() : "");
            obj.put("url", inst.getUrl() != null ? inst.getUrl() : "");
            arr.add(obj);
        }
        return new JsonResponse(arr);
    }

    @RequirePOST
    public HttpResponse doListRemoteJobs(@QueryParameter("sourceInstance") String sourceInstance) throws IOException, ServletException {
        folder.checkPermission(Item.CREATE);
        folder.checkPermission(Item.CONFIGURE);

        String folderPath = folder.getFullName();

        try {
            PromotionService service = new PromotionService();
            List<RemoteJobInfo> jobs = service.fetchRemoteJobs(folderPath, sourceInstance);
            return new JsonResponse(jobs);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to list remote jobs for folder: " + folder.getFullName(), e);
            return new JsonResponseerror(e.getMessage());
        }
    }

    @RequirePOST
    public HttpResponse doPromoteJobs(StaplerRequest2 req) throws IOException, ServletException {
        folder.checkPermission(Item.CREATE);
        folder.checkPermission(Item.CONFIGURE);

        String jobsParam = req.getParameter("jobs");
        boolean forceUpdate = "true".equals(req.getParameter("forceUpdate"));
        String sourceInstance = req.getParameter("sourceInstance");

        if (jobsParam == null || jobsParam.trim().isEmpty()) {
            return new JsonResponseerror(Messages.FolderPromotionAction_noJobsSelected());
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
            return new JsonResponseerror(Messages.FolderPromotionAction_noJobsSelected());
        }

        PromotionService service = new PromotionService();
        Future<Map<String, PromotionResult>> future = PromotionThreadPool.getInstance().submitWithAuth(() -> {
            Map<String, PromotionResult> results = service.promoteJobs(jobFullPaths, folder, forceUpdate, folder.getFullName(), sourceInstance);
            // Log audit
            String username = jenkins.model.Jenkins.getAuthentication2().getName();
            AuditLogService.getInstance().logPromotion(username, sourceInstance, jobFullPaths, forceUpdate, results);
            return results;
        });

        try {
            Map<String, PromotionResult> results = future.get();
            return new JsonResponse(results);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Promotion task failed for folder: " + folder.getFullName(), e);
            return new JsonResponseerror(e.getMessage());
        }
    }
}
