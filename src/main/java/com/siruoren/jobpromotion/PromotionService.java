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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

        for (String fullPath : jobFullPaths) {
            try {
                PromotionResult result = promoteSingleJob(client, fullPath, targetGroup, forceUpdate);
                results.put(fullPath, result);
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Failed to promote job: " + fullPath, e);
                results.put(fullPath, PromotionResult.failure(fullPath, e.getMessage()));
            }
        }
        return results;
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
}
