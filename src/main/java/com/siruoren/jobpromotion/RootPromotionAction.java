package com.siruoren.jobpromotion;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
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
        return "symbol-promote plugin-job-promotion";
    }

    @Override
    public String getDisplayName() {
        return Messages.RootPromotionAction_displayName();
    }

    @Override
    public String getUrlName() {
        return "job-promotion";
    }

    @RequirePOST
    public HttpResponse doListRemoteJobs(@QueryParameter("folderPath") String folderPath) throws IOException, ServletException {
        Jenkins.get().checkPermission(Jenkins.ADMINISTER);

        try {
            PromotionService service = new PromotionService();
            List<RemoteJobInfo> jobs = service.fetchRemoteJobs(folderPath);
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
            return service.promoteJobs(jobFullPaths, Jenkins.get(), forceUpdate);
        });

        try {
            Map<String, PromotionResult> results = future.get();
            return new JsonResponse(results);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Promotion task failed", e);
            return new JsonResponseerror(e.getMessage());
        }
    }
}
