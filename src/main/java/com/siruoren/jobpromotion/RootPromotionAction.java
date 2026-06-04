package com.siruoren.jobpromotion;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.RootAction;
import jenkins.model.Jenkins;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
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
            return "symbol-promote plugin-job-promotion";
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

    /**
     * Return the list of configured source Jenkins instances for the frontend dropdown.
     */
    @RequirePOST
    public HttpResponse doGetInstances() throws IOException, ServletException {
        Jenkins.get().checkPermission(Jenkins.ADMINISTER);

        JobPromotionGlobalConfig config = JobPromotionGlobalConfig.get();
        JSONArray arr = new JSONArray();
        for (SourceJenkinsInstance inst : config.getInstances()) {
            JSONObject obj = new JSONObject();
            obj.put("name", inst.getName() != null ? inst.getName() : "");
            obj.put("url", inst.getUrl() != null ? inst.getUrl() : "");
            arr.add(obj);
        }
        return new JsonResponse(arr);
    }

    @RequirePOST
    public HttpResponse doListRemoteJobs(@QueryParameter("folderPath") String folderPath,
                                          @QueryParameter("sourceInstance") String sourceInstance) throws IOException, ServletException {
        Jenkins.get().checkPermission(Jenkins.ADMINISTER);

        try {
            PromotionService service = new PromotionService();
            List<RemoteJobInfo> jobs = service.fetchRemoteJobs(folderPath, sourceInstance);
            return new JsonResponse(jobs);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to list remote jobs", e);
            return new JsonResponseerror(e.getMessage());
        }
    }

    @RequirePOST
    public HttpResponse doPromoteJobs(StaplerRequest2 req) throws IOException, ServletException {
        Jenkins.get().checkPermission(Jenkins.ADMINISTER);

        String jobsParam = req.getParameter("jobs");
        boolean forceUpdate = "true".equals(req.getParameter("forceUpdate"));
        String sourceInstance = req.getParameter("sourceInstance");

        if (jobsParam == null || jobsParam.trim().isEmpty()) {
            return new JsonResponseerror(Messages.RootPromotionAction_noJobsSelected());
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
            return new JsonResponseerror(Messages.RootPromotionAction_noJobsSelected());
        }

        PromotionService service = new PromotionService();
        Future<Map<String, PromotionResult>> future = PromotionThreadPool.getInstance().submitWithAuth(() -> {
            Map<String, PromotionResult> results = service.promoteJobs(jobFullPaths, Jenkins.get(), forceUpdate, null, sourceInstance);
            // Log audit
            String username = Jenkins.getAuthentication2().getName();
            AuditLogService.getInstance().logPromotion(username, sourceInstance, jobFullPaths, forceUpdate, results);
            return results;
        });

        try {
            Map<String, PromotionResult> results = future.get();
            return new JsonResponse(results);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Promotion task failed", e);
            return new JsonResponseerror(e.getMessage());
        }
    }

    /**
     * Get audit logs for the audit log page.
     */
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

        JSONObject result = new JSONObject();
        result.put("logs", AuditLogEntry.toJsonArray(logs));
        result.put("total", total);
        result.put("page", page);
        result.put("pageSize", pageSize);
        result.put("retentionDays", retentionDays);
        return new JsonResponse(result);
    }

    /**
     * Update audit log retention days.
     */
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
            return new JsonResponseerror("Invalid retention days value");
        }
        if (retentionDays < 1) {
            return new JsonResponseerror("Retention days must be at least 1");
        }

        JobPromotionGlobalConfig config = JobPromotionGlobalConfig.get();
        config.setAuditLogRetentionDays(retentionDays);
        config.save();

        AuditLogService.getInstance().cleanOldLogs();

        JSONObject result = new JSONObject();
        result.put("retentionDays", retentionDays);
        return new JsonResponse(result);
    }
}
