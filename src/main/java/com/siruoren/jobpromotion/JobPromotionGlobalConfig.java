package com.siruoren.jobpromotion;

import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import jenkins.model.GlobalConfiguration;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.jenkinsci.plugins.plaincredentials.StringCredentials;
import org.kohsuke.stapler.StaplerRequest2;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;

@Extension
public class JobPromotionGlobalConfig extends GlobalConfiguration {

    private static final Logger LOGGER = Logger.getLogger(JobPromotionGlobalConfig.class.getName());

    private List<SourceJenkinsInstance> instances = new ArrayList<>();
    private int auditLogRetentionDays = 30;

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
        // Use bindJSONToList for proper repeatableProperty binding
        instances = req.bindJSONToList(SourceJenkinsInstance.class, json.opt("instances"));
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
     * Uses Username + API Token (Secret text) authentication.
     * Returns a String array: [username, apiToken]
     */
    public String[] resolveCredentialsForInstance(SourceJenkinsInstance instance) {
        if (instance == null) {
            return null;
        }

        if (instance.getUsername() != null && !instance.getUsername().trim().isEmpty()
                && instance.getApiTokenCredentialsId() != null && !instance.getApiTokenCredentialsId().trim().isEmpty()) {
            StringCredentials apiTokenCred = resolveApiTokenCredentials(instance.getApiTokenCredentialsId());
            if (apiTokenCred != null) {
                return new String[]{instance.getUsername().trim(), apiTokenCred.getSecret().getPlainText()};
            }
        }

        return null;
    }

    public StringCredentials resolveApiTokenCredentials(String credId) {
        if (credId == null || credId.trim().isEmpty()) {
            return null;
        }
        return CredentialsMatchers.firstOrNull(
                CredentialsProvider.lookupCredentials(
                        StringCredentials.class,
                        Jenkins.get(),
                        null,
                        Collections.<DomainRequirement>emptyList()
                ),
                CredentialsMatchers.withId(credId.trim())
        );
    }

    // Keep backward compatibility
    public String getSourceJenkinsUrl() {
        if (instances.isEmpty()) {
            return "";
        }
        return instances.get(0).getUrl();
    }
}
