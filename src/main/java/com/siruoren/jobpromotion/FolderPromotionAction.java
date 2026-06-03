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
        return "symbol-copy";
    }

    @Override
    public String getDisplayName() {
        return Messages.FolderPromotionAction_displayName();
    }

    @Override
    public String getUrlName() {
        return "job-promotion";
    }

    public Folder getFolder() {
        return folder;
    }

    @RequirePOST
    public HttpResponse doListRemoteJobs(@QueryParameter("folderPath") String folderPath) throws IOException, ServletException {
        folder.checkPermission(Item.CONFIGURE);

        try {
            PromotionService service = new PromotionService();
            List<RemoteJobInfo> jobs = service.fetchRemoteJobs(folderPath);
            return new JsonResponse(jobs);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to list remote jobs for folder: " + folder.getFullName(), e);
            return new JsonResponseerror(e.getMessage());
        }
    }

    @RequirePOST
    public HttpResponse doPromoteJobs(StaplerRequest2 req) throws IOException, ServletException {
        folder.checkPermission(Item.CONFIGURE);

        String jobsParam = req.getParameter("jobs");
        boolean forceUpdate = "true".equals(req.getParameter("forceUpdate"));

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
            return service.promoteJobs(jobFullPaths, folder, forceUpdate, folder.getFullName());
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
