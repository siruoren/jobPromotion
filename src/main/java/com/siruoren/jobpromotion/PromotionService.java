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
        JobPromotionGlobalConfig config = JobPromotionGlobalConfig.get();
        String url = config.getSourceJenkinsUrl();
        var credentials = config.resolveCredentials();

        if (url == null || url.trim().isEmpty()) {
            throw new IllegalStateException(Messages.PromotionService_sourceNotConfigured());
        }
        if (credentials == null) {
            throw new IllegalStateException(Messages.PromotionService_credentialsNotConfigured());
        }

        JenkinsRemoteClient client = new JenkinsRemoteClient(
                url.trim(), credentials.getUsername(), credentials.getPassword().getPlainText()
        );
        return client.listJobs(folderPath);
    }

    /**
     * Promote jobs with folder awareness.
     * Each entry in jobFullPaths is "fullPath|isFolder" format.
     * - If a folder is selected, create the folder under the target group
     * - If only a job is selected (without its parent folder), create missing parent folders and promote the job
     */
    public Map<String, PromotionResult> promoteJobs(
            @NonNull List<String> jobFullPaths,
            @NonNull ModifiableTopLevelItemGroup targetGroup,
            boolean forceUpdate) {

        Map<String, PromotionResult> results = new HashMap<>();
        JobPromotionGlobalConfig config = JobPromotionGlobalConfig.get();
        String url = config.getSourceJenkinsUrl();
        var credentials = config.resolveCredentials();

        if (url == null || url.trim().isEmpty()) {
            for (String path : jobFullPaths) {
                results.put(path, PromotionResult.failure(path, Messages.PromotionService_sourceNotConfigured()));
            }
            return results;
        }
        if (credentials == null) {
            for (String path : jobFullPaths) {
                results.put(path, PromotionResult.failure(path, Messages.PromotionService_credentialsNotConfigured()));
            }
            return results;
        }

        JenkinsRemoteClient client = new JenkinsRemoteClient(
                url.trim(), credentials.getUsername(), credentials.getPassword().getPlainText()
        );

        // Collect all folders that need to be created first
        Set<String> foldersToCreate = new LinkedHashSet<>();
        // Parse job entries
        List<JobEntry> entries = new ArrayList<>();
        for (String entry : jobFullPaths) {
            String[] parts = entry.split("\\|");
            String fullPath = parts[0].trim();
            boolean isFolder = parts.length > 1 && "true".equalsIgnoreCase(parts[1].trim());
            entries.add(new JobEntry(fullPath, isFolder));

            if (isFolder) {
                // When a folder is selected, add it to folder creation list
                foldersToCreate.add(fullPath);
            } else {
                // For jobs, add parent paths to folder creation list
                String parentPath = getParentPath(fullPath);
                if (parentPath != null && !parentPath.isEmpty()) {
                    addParentFolders(foldersToCreate, parentPath);
                }
            }
        }

        // Create all needed folders first
        for (String folderPath : foldersToCreate) {
            try {
                ensureFolderExists(targetGroup, folderPath);
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Failed to create folder: " + folderPath, e);
            }
        }

        // Now promote each entry
        for (JobEntry entry : entries) {
            try {
                PromotionResult result;
                if (entry.isFolder) {
                    // Folder: just ensure the folder exists (already created above)
                    result = ensureFolderExists(targetGroup, entry.fullPath);
                } else {
                    // Job: promote the job config
                    result = promoteSingleJob(client, entry.fullPath, targetGroup, forceUpdate);
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

    private PromotionResult ensureFolderExists(ModifiableTopLevelItemGroup rootTarget, String folderPath) {
        String[] parts = folderPath.split("/");
        ModifiableTopLevelItemGroup current = rootTarget;

        for (String folderName : parts) {
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
                    return PromotionResult.failure(folderPath, "Failed to create folder: " + e.getMessage());
                }
            }
        }
        return PromotionResult.success(folderPath, Messages.PromotionService_folderCreated());
    }

    private PromotionResult promoteSingleJob(
            JenkinsRemoteClient client,
            String fullPath,
            ModifiableTopLevelItemGroup targetGroup,
            boolean forceUpdate) throws Exception {

        String configXml = client.getJobConfig(fullPath);
        configXml = cleanConfigXml(configXml);

        String jobName = extractJobName(fullPath);
        String[] pathParts = fullPath.split("/");
        ModifiableTopLevelItemGroup target = resolveTargetGroup(targetGroup, pathParts);

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

    private ModifiableTopLevelItemGroup resolveTargetGroup(ModifiableTopLevelItemGroup rootTarget, String[] pathParts) {
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
