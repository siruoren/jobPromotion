package com.siruoren.jobpromotion;

import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.Item;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import jenkins.model.GlobalConfiguration;
import jenkins.model.Jenkins;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest2;
import org.kohsuke.stapler.interceptor.RequirePOST;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

@Extension
public class JobPromotionGlobalConfig extends GlobalConfiguration {

    private static final Logger LOGGER = Logger.getLogger(JobPromotionGlobalConfig.class.getName());

    private List<SourceJenkinsInstance> instances = new ArrayList<>();
    private int auditLogRetentionDays = 30;

    public static class SourceJenkinsInstance {
        private String name;
        private String url;
        private String credentialsId;

        public SourceJenkinsInstance() {
        }

        public SourceJenkinsInstance(String name, String url, String credentialsId) {
            this.name = name;
            this.url = url;
            this.credentialsId = credentialsId;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public String getCredentialsId() {
            return credentialsId;
        }

        public void setCredentialsId(String credentialsId) {
            this.credentialsId = credentialsId;
        }
    }

    public JobPromotionGlobalConfig() {
        load();
    }

    @Override
    @NonNull
    public String getDisplayName() {
        return Messages.JobPromotionGlobalConfig_displayName();
    }

    @Override
    public boolean configure(StaplerRequest2 req, JSONObject json) throws FormException {
        this.instances = new ArrayList<>();
        JSONArray instancesArray = json.optJSONArray("instances");
        if (instancesArray != null) {
            for (int i = 0; i < instancesArray.size(); i++) {
                JSONObject instObj = instancesArray.getJSONObject(i);
                String name = instObj.optString("name", "").trim();
                String url = instObj.optString("url", "").trim();
                String credId = instObj.optString("credentialsId", "").trim();
                if (!url.isEmpty()) {
                    this.instances.add(new SourceJenkinsInstance(name, url, credId));
                }
            }
        }
        this.auditLogRetentionDays = json.optInt("auditLogRetentionDays", 30);
        if (this.auditLogRetentionDays < 1) {
            this.auditLogRetentionDays = 30;
        }
        save();
        return true;
    }

    public List<SourceJenkinsInstance> getInstances() {
        return instances;
    }

    public void setInstances(List<SourceJenkinsInstance> instances) {
        this.instances = instances;
    }

    public int getAuditLogRetentionDays() {
        return auditLogRetentionDays;
    }

    public void setAuditLogRetentionDays(int auditLogRetentionDays) {
        this.auditLogRetentionDays = auditLogRetentionDays;
    }

    public static JobPromotionGlobalConfig get() {
        return GlobalConfiguration.all().getInstance(JobPromotionGlobalConfig.class);
    }

    /**
     * Get a source Jenkins instance by name.
     */
    public SourceJenkinsInstance getInstanceByName(String name) {
        if (name == null || name.isEmpty()) {
            return instances.isEmpty() ? null : instances.get(0);
        }
        for (SourceJenkinsInstance inst : instances) {
            if (name.equals(inst.getName())) {
                return inst;
            }
        }
        return instances.isEmpty() ? null : instances.get(0);
    }

    /**
     * Resolve credentials for a specific instance.
     */
    public StandardUsernamePasswordCredentials resolveCredentialsForInstance(SourceJenkinsInstance instance) {
        if (instance == null || instance.getCredentialsId() == null || instance.getCredentialsId().trim().isEmpty()) {
            return null;
        }
        return resolveCredentials(instance.getCredentialsId());
    }

    public ListBoxModel doFillCredentialsIdItems(@AncestorInPath Item item,
                                                  @QueryParameter String credentialsId) {
        Jenkins jenkins = Jenkins.get();
        if (jenkins == null || !jenkins.hasPermission(Jenkins.ADMINISTER)) {
            return new ListBoxModel();
        }
        List<StandardUsernamePasswordCredentials> credentials = CredentialsProvider.lookupCredentials(
                StandardUsernamePasswordCredentials.class,
                jenkins,
                null,
                Collections.<DomainRequirement>emptyList()
        );
        ListBoxModel items = new ListBoxModel();
        items.add(Messages.JobPromotionGlobalConfig_selectCredentials(), "");
        for (StandardUsernamePasswordCredentials c : credentials) {
            items.add(c.getDescription() + " (" + c.getId() + ")", c.getId());
        }
        return items;
    }

    @RequirePOST
    public FormValidation doTestConnection(
            @QueryParameter("url") String url,
            @QueryParameter("credentialsId") String credId) {
        Jenkins.get().checkPermission(Jenkins.ADMINISTER);

        if (url == null || url.trim().isEmpty()) {
            return FormValidation.error(Messages.JobPromotionGlobalConfig_urlRequired());
        }
        if (credId == null || credId.trim().isEmpty()) {
            return FormValidation.error(Messages.JobPromotionGlobalConfig_credentialsRequired());
        }

        try {
            StandardUsernamePasswordCredentials credentials = resolveCredentials(credId);
            if (credentials == null) {
                return FormValidation.error(Messages.JobPromotionGlobalConfig_credentialsNotFound());
            }

            JenkinsRemoteClient client = new JenkinsRemoteClient(
                    url.trim(), credentials.getUsername(), credentials.getPassword().getPlainText()
            );
            boolean success = client.testConnection();
            if (success) {
                return FormValidation.ok(Messages.JobPromotionGlobalConfig_connectionSuccess());
            } else {
                return FormValidation.error(Messages.JobPromotionGlobalConfig_connectionFailed());
            }
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Connection test failed", e);
            return FormValidation.error(Messages.JobPromotionGlobalConfig_connectionError(e.getMessage()));
        }
    }

    public StandardUsernamePasswordCredentials resolveCredentials(String credId) {
        if (credId == null || credId.trim().isEmpty()) {
            return null;
        }
        return CredentialsMatchers.firstOrNull(
                CredentialsProvider.lookupCredentials(
                        StandardUsernamePasswordCredentials.class,
                        Jenkins.get(),
                        null,
                        Collections.<DomainRequirement>emptyList()
                ),
                CredentialsMatchers.withId(credId.trim())
        );
    }

    // Keep backward compatibility
    public StandardUsernamePasswordCredentials resolveCredentials() {
        if (instances.isEmpty()) {
            return null;
        }
        return resolveCredentialsForInstance(instances.get(0));
    }

    // Keep backward compatibility
    public String getSourceJenkinsUrl() {
        if (instances.isEmpty()) {
            return "";
        }
        return instances.get(0).getUrl();
    }
}
