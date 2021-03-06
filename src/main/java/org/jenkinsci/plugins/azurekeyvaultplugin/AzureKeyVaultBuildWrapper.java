/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2017 Microsoft Corporation
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
 
package org.jenkinsci.plugins.azurekeyvaultplugin;

import com.microsoft.azure.credentials.*;
import com.microsoft.azure.keyvault.KeyVaultClient;
import com.microsoft.azure.keyvault.authentication.KeyVaultCredentials;
import com.microsoft.azure.keyvault.models.SecretBundle;
import hudson.*;
import hudson.model.*;
import hudson.tasks.BuildWrapper;
import hudson.util.Secret;
import jenkins.tasks.SimpleBuildWrapper;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.QueryParameter;

import javax.annotation.CheckForNull;
import java.io.IOException;
import java.util.List;

/**
 * Sample {@link Builder}.
 *
 * <p>
 * When the user configures the project and enables this builder,
 * {@link DescriptorImpl#newInstance(StaplerRequest)} is invoked
 * and a new {@link AzureKeyVaultBuildWrapper} is created. The created
 * instance is persisted to the project configuration XML by using
 * XStream, so this allows you to use instance fields (like {@link #name})
 * to remember the configuration.
 *
 * <p>
 * When a build is performed, the {@link #perform} method will be invoked. 
 *
 * @author Kohsuke Kawaguchi
 */
public class AzureKeyVaultBuildWrapper extends SimpleBuildWrapper {

    private List<AzureKeyVaultSecret> azureKeyVaultSecrets;

    @DataBoundConstructor
    public AzureKeyVaultBuildWrapper(@CheckForNull List<AzureKeyVaultSecret> azureKeyVaultSecrets) {
        this.azureKeyVaultSecrets = azureKeyVaultSecrets;
    }
    
    public String getKeyVaultURL() {
        return getDescriptor().getKeyVaultURL();
    }
       
    public String getApplicationID() {
       return getDescriptor().getApplicationID();
    }
        
    public Secret getApplicationSecret() {
        return getDescriptor().getApplicationSecret();
    }
    
    public List<AzureKeyVaultSecret> getAzureKeyVaultSecrets() {
        return azureKeyVaultSecrets;
    }
    
    // Overridden for better type safety.
    // If your plugin doesn't really define any property on Descriptor,
    // you don't have to do this.
    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl)super.getDescriptor();
    }

    public void setUp(Context context, Run<?, ?> build, FilePath workspace,
      Launcher launcher, TaskListener listener, EnvVars initialEnvironment) {
          String applicationID = getApplicationID();
          Secret applicationSecret = getApplicationSecret();
          if (applicationID == null) {
              throw new NullPointerException("applicationID");
          } 
          if (applicationSecret == null) {
              throw new NullPointerException("applicationSecret");
          } 

          KeyVaultCredentials creds = new AzureKeyVaultCredential(applicationID, applicationSecret.toString());
          KeyVaultClient client = new KeyVaultClient(creds);

          for (AzureKeyVaultSecret secret : azureKeyVaultSecrets) {
              String secretIdentifier = getKeyVaultURL() + secret.getSecretType() + "/" + secret.getName() + "/" + secret.getVersion();
              try {
                SecretBundle bundle = client.getSecret(secretIdentifier);
                context.env(secret.getEnvVariable(), bundle.value());
              } catch (IOException e) {
                  e.printStackTrace();
              }
          }
    }

    /**
     * Descriptor for {@link AzureKeyVaultBuildWrapper}. Used as a singleton.
     * The class is marked as public so that it can be accessed from views.
     *
     * <p>
     * for the actual HTML fragment for the configuration screen.
     */
    @Extension // This indicates to Jenkins that this is an implementation of an extension point.
    public static final class DescriptorImpl extends Descriptor<BuildWrapper> {
        /**
         * To persist global configuration information,
         * simply store it in a field and call save().
         *
         * <p>
         * If you don't want fields to be persisted, use {@code transient}.
         */
        private String keyVaultURL;
        private String applicationID;
        private Secret applicationSecret;

        /**
         * In order to load the persisted global configuration, you have to 
         * call load() in the constructor.
         */
        public DescriptorImpl() {
            super(AzureKeyVaultBuildWrapper.class);
            load();
        }

        public boolean isApplicable(AbstractProject<?, ? > item) {
            // Indicates that this builder can be used with all kinds of project types 
            return true;
        }

        public String getKeyVaultURL() {
            return keyVaultURL;
        }
        
        public void setKeyVaultUrl(String keyVaultURL) {
            this.keyVaultURL = keyVaultURL;
        }

        public String getApplicationID() {
            return applicationID;
        }
        
        public void setApplicationID(String applicationID) {
            this.applicationID = applicationID;
        }

        public Secret getApplicationSecret() {
            return applicationSecret;
        }
        
        public void setApplicationSecret(String applicationSecret) {
            this.applicationSecret = Secret.fromString(applicationSecret);
        }

        /**
         * This human readable name is used in the configuration screen.
         */
        public String getDisplayName() {
            return "Azure Key Vault Plugin";
        }

        @Override
        public boolean configure(StaplerRequest req, JSONObject formData) throws FormException {
            this.keyVaultURL = formData.getString("keyVaultURL");
            this.applicationID = formData.getString("applicationID");
            this.applicationSecret = Secret.fromString(formData.getString("applicationSecret"));
            save();
            return super.configure(req,formData);
        }
    }
}

