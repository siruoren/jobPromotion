package com.siruoren.jobpromotion;

import com.siruoren.jobpromotion.engine.PromotionEngine;
import edu.umd.cs.findbugs.annotations.NonNull;
import jenkins.model.ModifiableTopLevelItemGroup;

import java.util.List;
import java.util.Map;

/**
 * Facade for promotion operations.
 * Delegates to PromotionEngine for actual logic.
 * Kept for backward compatibility with existing callers.
 */
public class PromotionService {

    private final PromotionEngine engine = PromotionEngine.getInstance();

    public List<RemoteJobInfo> fetchRemoteJobs(String folderPath) throws Exception {
        return fetchRemoteJobs(folderPath, null);
    }

    public List<RemoteJobInfo> fetchRemoteJobs(String folderPath, String sourceInstanceName) throws Exception {
        return engine.fetchRemoteJobs(folderPath, sourceInstanceName);
    }

    public Map<String, PromotionResult> promoteJobs(
            @NonNull List<String> jobFullPaths,
            @NonNull ModifiableTopLevelItemGroup targetGroup,
            boolean forceUpdate,
            String currentFolderPath) {
        return promoteJobs(jobFullPaths, targetGroup, forceUpdate, currentFolderPath, null);
    }

    public Map<String, PromotionResult> promoteJobs(
            @NonNull List<String> jobFullPaths,
            @NonNull ModifiableTopLevelItemGroup targetGroup,
            boolean forceUpdate,
            String currentFolderPath,
            String sourceInstanceName) {
        return engine.promoteJobs(jobFullPaths, targetGroup, forceUpdate, currentFolderPath, sourceInstanceName);
    }
}
