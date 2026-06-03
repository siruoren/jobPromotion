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
import net.sf.json.JSONObject;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest2;
import org.kohsuke.stapler.interceptor.RequirePOST;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

@Extension
public class JobPromotionGlobalConfig extends GlobalConfiguration {

    private static final Logger LOGGER = Logger.getLogger(JobPromotionGlobalConfig.class.getName());

    private String sourceJenkinsUrl;
    private String credentialsId;

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
        this.sourceJenkinsUrl = json.optString("sourceJenkinsUrl", "").trim();
        this.credentialsId = json.optString("credentialsId", "").trim();
        save();
        return true;
    }

    public String getSourceJenkinsUrl() {
        return sourceJenkinsUrl;
    }

    public void setSourceJenkinsUrl(String sourceJenkinsUrl) {
        this.sourceJenkinsUrl = sourceJenkinsUrl;
    }

    public String getCredentialsId() {
        return credentialsId;
    }

    public void setCredentialsId(String credentialsId) {
        this.credentialsId = credentialsId;
    }

    public static JobPromotionGlobalConfig get() {
        return GlobalConfiguration.all().getInstance(JobPromotionGlobalConfig.class);
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
            @QueryParameter("sourceJenkinsUrl") String url,
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

    public StandardUsernamePasswordCredentials resolveCredentials() {
        return resolveCredentials(this.credentialsId);
    }
}
