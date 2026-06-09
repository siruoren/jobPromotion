package com.siruoren.jobpromotion.engine;

import com.cloudbees.hudson.plugins.folder.Folder;
import com.siruoren.jobpromotion.JenkinsRemoteClient;
import com.siruoren.jobpromotion.JobPromotionGlobalConfig;
import com.siruoren.jobpromotion.Messages;
import com.siruoren.jobpromotion.PromotionResult;
import com.siruoren.jobpromotion.RemoteJobInfo;
import com.siruoren.jobpromotion.SourceJenkinsInstance;
import com.siruoren.jobpromotion.util.PathUtil;
import com.siruoren.jobpromotion.util.XmlUtil;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.model.AbstractProject;
import hudson.model.Item;
import hudson.model.Job;
import hudson.model.TopLevelItem;
import jenkins.model.Jenkins;
import jenkins.model.ModifiableTopLevelItemGroup;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Core engine for job promotion logic.
 * Handles the actual mechanics of promoting jobs and syncing folders from remote Jenkins.
 */
public class PromotionEngine {

    private static final Logger LOGGER = Logger.getLogger(PromotionEngine.class.getName());
    private static final PromotionEngine INSTANCE = new PromotionEngine();

    private PromotionEngine() {
    }

    public static PromotionEngine getInstance() {
        return INSTANCE;
    }

    /**
     * Create a JenkinsRemoteClient for the given source instance.
     */
    public JenkinsRemoteClient createClient(String sourceInstanceName) {
        JobPromotionGlobalConfig config = JobPromotionGlobalConfig.get();
        SourceJenkinsInstance instance = config.getInstanceByName(sourceInstanceName);
        if (instance == null) {
            return null;
        }
        String url = instance.getUrl();
        String[] cred = config.resolveCredentialsForInstance(instance);
        if (url == null || url.trim().isEmpty() || cred == null) {
            return null;
        }
        return new JenkinsRemoteClient(url.trim(), cred[0], cred[1]);
    }

    /**
     * Validate source instance configuration.
     * Returns error message if invalid, null if valid.
     */
    public String validateSourceInstance(String sourceInstanceName) {
        JobPromotionGlobalConfig config = JobPromotionGlobalConfig.get();
        SourceJenkinsInstance instance = config.getInstanceByName(sourceInstanceName);
        if (instance == null) {
            return Messages.PromotionService_sourceNotConfigured();
        }
        String url = instance.getUrl();
        String[] cred = config.resolveCredentialsForInstance(instance);
        if (url == null || url.trim().isEmpty()) {
            return Messages.PromotionService_sourceNotConfigured();
        }
        if (cred == null) {
            return Messages.PromotionService_credentialsNotConfigured();
        }
        return null;
    }

    /**
     * Fetch remote jobs from source Jenkins.
     */
    public List<RemoteJobInfo> fetchRemoteJobs(String folderPath, String sourceInstanceName) throws Exception {
        // Validate folder path
        if (folderPath != null && !folderPath.isEmpty() && PathUtil.sanitizePath(folderPath) == null) {
            throw new IllegalArgumentException("Invalid folder path: path traversal detected");
        }

        JenkinsRemoteClient client = createClient(sourceInstanceName);
        if (client == null) {
            throw new IllegalStateException(Messages.PromotionService_sourceNotConfigured());
        }
        return client.listJobs(folderPath);
    }

    /**
     * Promote jobs with folder awareness.
     * Each entry in jobFullPaths is "fullPath|isFolder" format.
     *
     * @param jobFullPaths     list of job entries to promote
     * @param targetGroup      target item group (folder or Jenkins root)
     * @param forceUpdate      whether to force update existing items
     * @param currentFolderPath current folder path (skip syncing this folder and its parents)
     * @param sourceInstanceName source Jenkins instance name
     * @return map of job path to promotion result
     */
    public Map<String, PromotionResult> promoteJobs(
            @NonNull List<String> jobFullPaths,
            @NonNull ModifiableTopLevelItemGroup targetGroup,
            boolean forceUpdate,
            String currentFolderPath,
            String sourceInstanceName) {

        Map<String, PromotionResult> results = new HashMap<>();

        // Validate source instance
        String validationError = validateSourceInstance(sourceInstanceName);
        if (validationError != null) {
            for (String path : jobFullPaths) {
                results.put(path, PromotionResult.failure(path, validationError));
            }
            return results;
        }

        JenkinsRemoteClient client = createClient(sourceInstanceName);

        // Always resolve from Jenkins root, not from current targetGroup
        Jenkins jenkins = Jenkins.get();
        ModifiableTopLevelItemGroup rootTarget = jenkins;

        // Determine folders to skip: current folder and its parents
        Set<String> skipFolders = PathUtil.buildSkipFolders(currentFolderPath);

        // Collect all folders that need to be synced/created
        Set<String> foldersToSync = new LinkedHashSet<>();
        List<PathUtil.JobEntry> entries = new ArrayList<>();

        for (String entry : jobFullPaths) {
            PathUtil.JobEntry parsed = PathUtil.parseJobEntry(entry);

            // Validate path to prevent path traversal
            if (PathUtil.sanitizePath(parsed.fullPath) == null) {
                results.put(entry, PromotionResult.failure(entry, "Invalid path: path traversal detected"));
                continue;
            }
            if (PathUtil.sanitizeJobName(PathUtil.extractName(parsed.fullPath)) == null) {
                results.put(entry, PromotionResult.failure(entry, "Invalid job name"));
                continue;
            }

            entries.add(parsed);

            if (parsed.isFolder) {
                PathUtil.addParentFolders(foldersToSync, parsed.fullPath);
            } else {
                String parentPath = PathUtil.getParentPath(parsed.fullPath);
                if (parentPath != null && !parentPath.isEmpty()) {
                    PathUtil.addParentFolders(foldersToSync, parentPath);
                }
            }
        }

        // Remove folders that should be skipped
        foldersToSync.removeAll(skipFolders);

        // Sync all parent folders first (from root to leaf)
        // Use a separate map for folder sync results to avoid duplicates in final results
        Map<String, PromotionResult> folderSyncResults = new HashMap<>();
        for (String folderPath : foldersToSync) {
            try {
                PromotionResult result = syncFolderConfig(client, rootTarget, folderPath, forceUpdate);
                folderSyncResults.put(folderPath, result);
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Failed to sync folder: " + folderPath, e);
                folderSyncResults.put(folderPath, PromotionResult.failure(folderPath, "Failed to sync folder: " + e.getMessage()));
            }
        }

        // Promote each entry
        for (PathUtil.JobEntry entry : entries) {
            try {
                PromotionResult result;
                if (entry.isFolder) {
                    if (skipFolders.contains(entry.fullPath)) {
                        result = PromotionResult.skipped(entry.fullPath, Messages.PromotionService_folderExists());
                    } else {
                        // Folder already synced above, get result from folderSyncResults
                        result = folderSyncResults.get(entry.fullPath);
                        if (result == null) {
                            result = PromotionResult.success(entry.fullPath, Messages.PromotionService_folderCreated());
                        }
                    }
                } else {
                    result = promoteSingleJob(client, entry.fullPath, rootTarget, forceUpdate);
                }
                results.put(entry.fullPath, result);
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Failed to promote: " + entry.fullPath, e);
                results.put(entry.fullPath, PromotionResult.failure(entry.fullPath, e.getMessage()));
            }
        }
        return results;
    }

    /**
     * Sync a folder's config from remote Jenkins.
     * If the folder doesn't exist locally, create it.
     * If it exists and forceUpdate is true, update its config.
     */
    private PromotionResult syncFolderConfig(
            JenkinsRemoteClient client,
            ModifiableTopLevelItemGroup rootTarget,
            String folderPath,
            boolean forceUpdate) throws Exception {

        String[] parts = folderPath.split("/");
        ModifiableTopLevelItemGroup current = rootTarget;

        // Navigate/create to the parent of the target folder
        for (int i = 0; i < parts.length - 1; i++) {
            current = getOrCreateFolder(current, parts[i]);
        }

        // Handle the target folder itself
        String targetFolderName = parts[parts.length - 1];
        Item existingItem = current.getItem(targetFolderName);

        if (existingItem instanceof Folder) {
            if (forceUpdate) {
                try {
                    String folderConfigXml = client.getJobConfig(folderPath);
                    folderConfigXml = XmlUtil.cleanFolderConfigXml(folderConfigXml);
                    Folder existingFolder = (Folder) existingItem;
                    existingFolder.updateByXml(new javax.xml.transform.stream.StreamSource(new StringReader(folderConfigXml)));
                    existingFolder.save();
                    return PromotionResult.success(folderPath, Messages.PromotionService_folderUpdated());
                } catch (Exception e) {
                    LOGGER.log(Level.WARNING, "Failed to update folder config: " + folderPath, e);
                    return PromotionResult.success(folderPath, Messages.PromotionService_folderCreated());
                }
            }
            return PromotionResult.skipped(folderPath, Messages.PromotionService_folderExists());
        }

        // Folder doesn't exist - create it and sync config
        try {
            String folderConfigXml = null;
            try {
                folderConfigXml = client.getJobConfig(folderPath);
                folderConfigXml = XmlUtil.cleanFolderConfigXml(folderConfigXml);
            } catch (Exception e) {
                LOGGER.log(Level.FINE, "Could not fetch folder config, will create empty folder: " + folderPath, e);
            }

            Folder newFolder;
            if (current instanceof Folder currentFolder) {
                TopLevelItem created = currentFolder.createProject(Folder.class, targetFolderName);
                if (created instanceof Folder) {
                    newFolder = (Folder) created;
                } else {
                    return PromotionResult.failure(folderPath, "Created item is not a Folder");
                }
            } else if (current instanceof Jenkins jenkins) {
                TopLevelItem created = jenkins.createProject(Folder.class, targetFolderName);
                if (created instanceof Folder) {
                    newFolder = (Folder) created;
                } else {
                    return PromotionResult.failure(folderPath, "Created item is not a Folder");
                }
            } else {
                return PromotionResult.failure(folderPath, "Cannot create folder in " + current.getClass().getName());
            }

            if (folderConfigXml != null) {
                try {
                    newFolder.updateByXml(new javax.xml.transform.stream.StreamSource(new StringReader(folderConfigXml)));
                    newFolder.save();
                } catch (Exception e) {
                    LOGGER.log(Level.WARNING, "Failed to apply remote folder config: " + folderPath, e);
                }
            }

            return PromotionResult.success(folderPath, Messages.PromotionService_folderCreated());
        } catch (Exception e) {
            return PromotionResult.failure(folderPath, "Failed to create folder: " + e.getMessage());
        }
    }

    private ModifiableTopLevelItemGroup getOrCreateFolder(ModifiableTopLevelItemGroup parent, String folderName) throws Exception {
        Item item = parent.getItem(folderName);
        if (item instanceof Folder) {
            return (Folder) item;
        }
        if (item instanceof ModifiableTopLevelItemGroup) {
            return (ModifiableTopLevelItemGroup) item;
        }

        if (parent instanceof Folder currentFolder) {
            TopLevelItem created = currentFolder.createProject(Folder.class, folderName);
            if (created instanceof Folder) {
                return (Folder) created;
            }
        } else if (parent instanceof Jenkins jenkins) {
            TopLevelItem created = jenkins.createProject(Folder.class, folderName);
            if (created instanceof Folder) {
                return (Folder) created;
            }
        }

        throw new IOException("Cannot create folder: " + folderName);
    }

    private PromotionResult promoteSingleJob(
            JenkinsRemoteClient client,
            String fullPath,
            ModifiableTopLevelItemGroup rootTarget,
            boolean forceUpdate) throws Exception {

        String configXml = client.getJobConfig(fullPath);
        configXml = XmlUtil.cleanJobConfigXml(configXml);

        String jobName = PathUtil.extractName(fullPath);
        String[] pathParts = fullPath.split("/");
        ModifiableTopLevelItemGroup target = resolveTargetGroupFromRoot(rootTarget, pathParts);

        Item existingItem = target.getItem(jobName);

        if (existingItem != null) {
            if (!forceUpdate) {
                return PromotionResult.skipped(fullPath, Messages.PromotionService_jobExists());
            }
            if (existingItem instanceof Job) {
                Job<?, ?> existingJob = (Job<?, ?>) existingItem;
                existingJob.updateByXml(new javax.xml.transform.stream.StreamSource(new StringReader(configXml)));
                existingJob.save();
                disableJob(existingJob);
                return PromotionResult.success(fullPath, Messages.PromotionService_jobUpdated());
            }
            existingItem.delete();
        }

        try {
            TopLevelItem newItem = target.createProjectFromXML(jobName, new ByteArrayInputStream(configXml.getBytes(StandardCharsets.UTF_8)));
            if (newItem instanceof Job) {
                disableJob((Job<?, ?>) newItem);
            }
            return PromotionResult.success(fullPath, Messages.PromotionService_jobCreated());
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to create job: " + jobName, e);
            return PromotionResult.failure(fullPath, e.getMessage());
        }
    }

    private ModifiableTopLevelItemGroup resolveTargetGroupFromRoot(ModifiableTopLevelItemGroup rootTarget, String[] pathParts) {
        if (pathParts.length <= 1) {
            return rootTarget;
        }

        ModifiableTopLevelItemGroup current = rootTarget;
        for (int i = 0; i < pathParts.length - 1; i++) {
            String folderName = pathParts[i];
            Item item = current.getItem(folderName);

            if (item instanceof Folder) {
                current = (Folder) item;
            } else if (item instanceof ModifiableTopLevelItemGroup) {
                current = (ModifiableTopLevelItemGroup) item;
            } else {
                try {
                    if (current instanceof Folder currentFolder) {
                        TopLevelItem created = currentFolder.createProject(Folder.class, folderName);
                        if (created instanceof Folder) {
                            current = (Folder) created;
                        }
                    } else if (current instanceof Jenkins jenkins) {
                        TopLevelItem created = jenkins.createProject(Folder.class, folderName);
                        if (created instanceof Folder) {
                            current = (Folder) created;
                        }
                    }
                } catch (Exception e) {
                    LOGGER.log(Level.WARNING, "Failed to create folder: " + folderName, e);
                    return rootTarget;
                }
            }
        }
        return current;
    }

    private void disableJob(Job<?, ?> job) {
        try {
            if (job instanceof AbstractProject) {
                ((AbstractProject<?, ?>) job).makeDisabled(true);
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to disable job: " + job.getFullName(), e);
        }
    }
}
