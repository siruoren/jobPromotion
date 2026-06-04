package com.siruoren.jobpromotion;

import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.util.FormValidation;
import jenkins.model.GlobalConfiguration;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
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
     */
    public StandardUsernamePasswordCredentials resolveCredentialsForInstance(SourceJenkinsInstance instance) {
        if (instance == null || instance.getCredentialsId() == null || instance.getCredentialsId().trim().isEmpty()) {
            return null;
        }
        return resolveCredentials(instance.getCredentialsId());
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
