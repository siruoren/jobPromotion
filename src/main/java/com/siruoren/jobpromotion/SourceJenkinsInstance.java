package com.siruoren.jobpromotion;

import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.plaincredentials.StringCredentials;
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
    private String username;
    private String apiTokenCredentialsId;

    public SourceJenkinsInstance() {
    }

    @DataBoundConstructor
    public SourceJenkinsInstance(String name, String url, String username, String apiTokenCredentialsId) {
        this.name = name;
        this.url = url;
        this.username = username;
        this.apiTokenCredentialsId = apiTokenCredentialsId;
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

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getApiTokenCredentialsId() {
        return apiTokenCredentialsId;
    }

    public void setApiTokenCredentialsId(String apiTokenCredentialsId) {
        this.apiTokenCredentialsId = apiTokenCredentialsId;
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<SourceJenkinsInstance> {

        @NonNull
        @Override
        public String getDisplayName() {
            return "Source Jenkins Instance";
        }

        /**
         * Fill API Token credentials dropdown - shows Secret text credentials.
         * Uses StandardCredentials type so that c:select can show the "Add" button
         * for creating new credentials inline.
         */
        public ListBoxModel doFillApiTokenCredentialsIdItems(@AncestorInPath Jenkins context,
                                                              @QueryParameter String apiTokenCredentialsId) {
            if (context == null || !context.hasPermission(Jenkins.ADMINISTER)) {
                return new ListBoxModel();
            }
            List<StringCredentials> credentials = CredentialsProvider.lookupCredentials(
                    StringCredentials.class,
                    context,
                    null,
                    Collections.<DomainRequirement>emptyList()
            );
            ListBoxModel items = new ListBoxModel();
            items.add(Messages.SourceJenkinsInstance_selectApiToken(), "");
            for (StringCredentials c : credentials) {
                items.add(c.getDescription() + " (" + c.getId() + ")", c.getId());
            }
            return items;
        }

        @RequirePOST
        public FormValidation doTestConnection(
                @QueryParameter("url") String url,
                @QueryParameter("username") String usernameParam,
                @QueryParameter("apiTokenCredentialsId") String apiTokenCredId) {
            Jenkins.get().checkPermission(Jenkins.ADMINISTER);

            if (url == null || url.trim().isEmpty()) {
                return FormValidation.error(Messages.JobPromotionGlobalConfig_urlRequired());
            }
            if (usernameParam == null || usernameParam.trim().isEmpty()) {
                return FormValidation.error(Messages.SourceJenkinsInstance_usernameRequired());
            }
            if (apiTokenCredId == null || apiTokenCredId.trim().isEmpty()) {
                return FormValidation.error(Messages.SourceJenkinsInstance_apiTokenRequired());
            }

            StringCredentials apiTokenCred = resolveApiTokenCredentials(apiTokenCredId);
            if (apiTokenCred == null) {
                return FormValidation.error(Messages.SourceJenkinsInstance_apiTokenNotFound());
            }

            try {
                JenkinsRemoteClient client = new JenkinsRemoteClient(
                        url.trim(), usernameParam.trim(), apiTokenCred.getSecret().getPlainText()
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

        private StringCredentials resolveApiTokenCredentials(String credId) {
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
    }
}
