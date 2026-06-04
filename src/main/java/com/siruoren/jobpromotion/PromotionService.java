package com.siruoren.jobpromotion;

import com.cloudbees.hudson.plugins.folder.Folder;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.model.AbstractProject;
import hudson.model.Item;
import hudson.model.Job;
import hudson.model.TopLevelItem;
import jenkins.model.Jenkins;
import jenkins.model.ModifiableTopLevelItemGroup;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

public class PromotionService {

    private static final Logger LOGGER = Logger.getLogger(PromotionService.class.getName());

    public List<RemoteJobInfo> fetchRemoteJobs(String folderPath) throws Exception {
        return fetchRemoteJobs(folderPath, null);
    }

    public List<RemoteJobInfo> fetchRemoteJobs(String folderPath, String sourceInstanceName) throws Exception {
        JobPromotionGlobalConfig config = JobPromotionGlobalConfig.get();
        SourceJenkinsInstance instance = config.getInstanceByName(sourceInstanceName);
        if (instance == null) {
            throw new IllegalStateException(Messages.PromotionService_sourceNotConfigured());
        }
        String url = instance.getUrl();
        String[] cred = config.resolveCredentialsForInstance(instance);

        if (url == null || url.trim().isEmpty()) {
            throw new IllegalStateException(Messages.PromotionService_sourceNotConfigured());
        }
        if (cred == null) {
            throw new IllegalStateException(Messages.PromotionService_credentialsNotConfigured());
        }

        JenkinsRemoteClient client = new JenkinsRemoteClient(
                url.trim(), cred[0], cred[1]
        );
        return client.listJobs(folderPath);
    }

    /**
     * Promote jobs with folder awareness.
     * Each entry in jobFullPaths is "fullPath|isFolder" format.
     * fullPath is always resolved from Jenkins root directory.
     * - currentFolderPath: the current folder's full path, skip syncing this folder and its parents
     * - If a folder is selected, sync the folder config from remote
     * - If only a job is selected, auto-create parent folders and sync their configs, then promote the job
     */
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

        Map<String, PromotionResult> results = new HashMap<>();
        JobPromotionGlobalConfig config = JobPromotionGlobalConfig.get();
        SourceJenkinsInstance instance = config.getInstanceByName(sourceInstanceName);
        if (instance == null) {
            for (String path : jobFullPaths) {
                results.put(path, PromotionResult.failure(path, Messages.PromotionService_sourceNotConfigured()));
            }
            return results;
        }
        String url = instance.getUrl();
        String[] cred = config.resolveCredentialsForInstance(instance);

        if (url == null || url.trim().isEmpty()) {
            for (String path : jobFullPaths) {
                results.put(path, PromotionResult.failure(path, Messages.PromotionService_sourceNotConfigured()));
            }
            return results;
        }
        if (cred == null) {
            for (String path : jobFullPaths) {
                results.put(path, PromotionResult.failure(path, Messages.PromotionService_credentialsNotConfigured()));
            }
            return results;
        }

        JenkinsRemoteClient client = new JenkinsRemoteClient(
                url.trim(), cred[0], cred[1]
        );

        // Always resolve from Jenkins root, not from current targetGroup
        Jenkins jenkins = Jenkins.get();
        ModifiableTopLevelItemGroup rootTarget = jenkins;

        // Determine folders to skip: current folder and its parents
        Set<String> skipFolders = new LinkedHashSet<>();
        if (currentFolderPath != null && !currentFolderPath.isEmpty()) {
            // Add current folder and all its parent paths to skip set
            String[] currentParts = currentFolderPath.split("/");
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < currentParts.length; i++) {
                if (i > 0) sb.append("/");
                sb.append(currentParts[i]);
                skipFolders.add(sb.toString());
            }
        }

        // Collect all folders that need to be synced/created
        Set<String> foldersToSync = new LinkedHashSet<>();
        // Parse job entries
        List<JobEntry> entries = new ArrayList<>();
        for (String entry : jobFullPaths) {
            String[] parts = entry.split("\\|");
            String fullPath = parts[0].trim();
            boolean isFolder = parts.length > 1 && "true".equalsIgnoreCase(parts[1].trim());
            entries.add(new JobEntry(fullPath, isFolder));

            if (isFolder) {
                // When a folder is selected, add it and all parent folders to sync list
                addParentFolders(foldersToSync, fullPath);
            } else {
                // For jobs, add parent paths to folder sync list
                String parentPath = getParentPath(fullPath);
                if (parentPath != null && !parentPath.isEmpty()) {
                    addParentFolders(foldersToSync, parentPath);
                }
            }
        }

        // Remove folders that should be skipped (current folder and its parents)
        foldersToSync.removeAll(skipFolders);

        // Sync all parent folders first (from root to leaf), skipping current and parent folders
        for (String folderPath : foldersToSync) {
            try {
                PromotionResult result = syncFolderConfig(client, rootTarget, folderPath, forceUpdate);
                results.put(folderPath + "/", result);
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Failed to sync folder: " + folderPath, e);
                results.put(folderPath + "/", PromotionResult.failure(folderPath, "Failed to sync folder: " + e.getMessage()));
            }
        }

        // Now promote each entry
        for (JobEntry entry : entries) {
            try {
                PromotionResult result;
                if (entry.isFolder) {
                    if (skipFolders.contains(entry.fullPath)) {
                        // Skip current folder and its parents
                        result = PromotionResult.skipped(entry.fullPath, Messages.PromotionService_folderExists());
                    } else {
                        // Folder already synced above, just report result
                        result = results.get(entry.fullPath + "/");
                        if (result == null) {
                            result = PromotionResult.success(entry.fullPath, Messages.PromotionService_folderCreated());
                        }
                    }
                } else {
                    // Job: promote the job config, always resolve from root
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

    private void addParentFolders(Set<String> folders, String path) {
        String[] parts = path.split("/");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) sb.append("/");
            sb.append(parts[i]);
            folders.add(sb.toString());
        }
    }

    private String getParentPath(String fullPath) {
        if (fullPath == null || !fullPath.contains("/")) {
            return null;
        }
        return fullPath.substring(0, fullPath.lastIndexOf("/"));
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
            String folderName = parts[i];
            current = getOrCreateFolder(current, folderName);
        }

        // Handle the target folder itself
        String targetFolderName = parts[parts.length - 1];
        Item existingItem = current.getItem(targetFolderName);

        if (existingItem instanceof Folder) {
            // Folder exists - update its config if force update
            if (forceUpdate) {
                try {
                    String folderConfigXml = client.getJobConfig(folderPath);
                    folderConfigXml = cleanFolderConfigXml(folderConfigXml);
                    Folder existingFolder = (Folder) existingItem;
                    existingFolder.updateByXml(new StreamSource(new StringReader(folderConfigXml)));
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
                folderConfigXml = cleanFolderConfigXml(folderConfigXml);
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

            // Apply remote config if available
            if (folderConfigXml != null) {
                try {
                    newFolder.updateByXml(new StreamSource(new StringReader(folderConfigXml)));
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

        // Create the folder
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
        configXml = cleanConfigXml(configXml);

        String jobName = extractJobName(fullPath);
        String[] pathParts = fullPath.split("/");
        ModifiableTopLevelItemGroup target = resolveTargetGroupFromRoot(rootTarget, pathParts);

        Item existingItem = target.getItem(jobName);

        if (existingItem != null) {
            if (!forceUpdate) {
                return PromotionResult.skipped(fullPath, Messages.PromotionService_jobExists());
            }
            if (existingItem instanceof Job) {
                Job<?, ?> existingJob = (Job<?, ?>) existingItem;
                existingJob.updateByXml(new StreamSource(new StringReader(configXml)));
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

    /**
     * Resolve target group from Jenkins root, not from current folder.
     * fullPath like "a/b/c/job" -> navigate root -> a -> b -> c
     */
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

    private String extractJobName(String fullPath) {
        if (fullPath == null || fullPath.isEmpty()) {
            return "unknown";
        }
        String[] parts = fullPath.split("/");
        return parts[parts.length - 1];
    }

    String cleanConfigXml(String configXml) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(false);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", false);
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);

            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(new InputSource(new StringReader(configXml)));

            cleanTriggers(doc);
            cleanMonitorTriggers(doc);
            cleanBuildDiscarder(doc);

            TransformerFactory tf = TransformerFactory.newInstance();
            Transformer transformer = tf.newTransformer();
            transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");

            StringWriter writer = new StringWriter();
            transformer.transform(new DOMSource(doc), new StreamResult(writer));
            String result = writer.toString();

            result = cleanGarbledChars(result);

            return result;
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to clean config XML, returning original", e);
            return cleanGarbledChars(configXml);
        }
    }

    private String cleanFolderConfigXml(String configXml) {
        // For folders, just clean garbled chars, don't remove triggers etc.
        return cleanGarbledChars(configXml);
    }

    private void cleanTriggers(Document doc) {
        NodeList triggersList = doc.getElementsByTagName("triggers");
        for (int i = 0; i < triggersList.getLength(); i++) {
            Element triggers = (Element) triggersList.item(i);
            triggers.setTextContent("");
        }
    }

    private void cleanMonitorTriggers(Document doc) {
        String[] monitorTags = {"monitor", "buildMonitor", "monitorJobs", "hudson.plugins.buildmonitor.BuildMonitor"};
        for (String tag : monitorTags) {
            NodeList nodes = doc.getElementsByTagName(tag);
            for (int i = 0; i < nodes.getLength(); i++) {
                Element el = (Element) nodes.item(i);
                el.getParentNode().removeChild(el);
            }
        }
    }

    private void cleanBuildDiscarder(Document doc) {
        NodeList discarderList = doc.getElementsByTagName("strategy");
        for (int i = 0; i < discarderList.getLength(); i++) {
            Element strategy = (Element) discarderList.item(i);
            String clazz = strategy.getAttribute("class");
            if (clazz != null && clazz.contains("LogRotator")) {
                strategy.getParentNode().removeChild(strategy);
            }
        }
    }

    private String cleanGarbledChars(String text) {
        if (text == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isISOControl(c) && c != '\n' && c != '\r' && c != '\t') {
                continue;
            }
            if (isGarbled(c)) {
                continue;
            }
            sb.append(c);
        }
        return sb.toString();
    }

    private boolean isGarbled(char c) {
        int type = Character.getType(c);
        return type == Character.PRIVATE_USE
                || type == Character.SURROGATE
                || (type == Character.OTHER_SYMBOL && c > 0xFFF0);
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

    private static class JobEntry {
        final String fullPath;
        final boolean isFolder;

        JobEntry(String fullPath, boolean isFolder) {
            this.fullPath = fullPath;
            this.isFolder = isFolder;
        }
    }
}
