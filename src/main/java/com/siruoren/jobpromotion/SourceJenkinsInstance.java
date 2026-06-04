package com.siruoren.jobpromotion;

import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import jenkins.model.Jenkins;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.interceptor.RequirePOST;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class SourceJenkinsInstance extends AbstractDescribableImpl<SourceJenkinsInstance> {

    private static final Logger LOGGER = Logger.getLogger(SourceJenkinsInstance.class.getName());

    private String name;
    private String url;
    private String credentialsId;

    public SourceJenkinsInstance() {
    }

    @DataBoundConstructor
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

    @Extension
    public static class DescriptorImpl extends Descriptor<SourceJenkinsInstance> {

        @NonNull
        @Override
        public String getDisplayName() {
            return "Source Jenkins Instance";
        }

        public ListBoxModel doFillCredentialsIdItems(@AncestorInPath Jenkins context,
                                                      @QueryParameter String credentialsId) {
            if (context == null || !context.hasPermission(Jenkins.ADMINISTER)) {
                return new ListBoxModel();
            }
            List<StandardUsernamePasswordCredentials> credentials = CredentialsProvider.lookupCredentials(
                    StandardUsernamePasswordCredentials.class,
                    context,
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

        private StandardUsernamePasswordCredentials resolveCredentials(String credId) {
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
    }
}
