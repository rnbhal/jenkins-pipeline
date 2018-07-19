package de.codecentric.jenkins.dashboard;

import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import hudson.security.ACL;
import hudson.util.ComboBoxModel;
import hudson.util.ListBoxModel;

import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;

import javax.annotation.Nonnull;

import jenkins.model.Jenkins;

import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;

import de.codecentric.jenkins.dashboard.api.environments.ServerEnvironment;
import de.codecentric.jenkins.dashboard.ec2.AwsKeyCredentials;
import de.codecentric.jenkins.dashboard.impl.environments.EnvironmentType;
import de.codecentric.jenkins.dashboard.impl.environments.ec2.AwsRegion;
import de.codecentric.jenkins.dashboard.impl.environments.ec2.EC2Connector;

/**
 * Describes the environment configuration.
 */
public class Environment extends AbstractDescribableImpl<Environment> {

    private final static Logger LOGGER = Logger.getLogger(Environment.class.getName());

    private String name;
    private String url;
    private String environmentType;
    private String buildJob;

    @DataBoundConstructor
    public Environment(@Nonnull final String name, @Nonnull final String url, @Nonnull final String environmentType, 
    		final String buildJob) {
//        LOGGER.info("New environment created: " + credentials + ", " + region);
        setName(name);
        setUrl(url);
//       setCredentials(credentials);
//        setRegion(region);
        setEnvironmentType(environmentType);
//        setAwsInstance(awsInstance);
        setBuildJob(buildJob);
//        setUrlPostfix(urlPostfix);
    }

    @Extension
    public static class EnvironmentDescriptor extends Descriptor<Environment> {
        
        public ComboBoxModel doFillBuildJobItems() {
            ComboBoxModel model = new ComboBoxModel();

            for (String jobName : Jenkins.getInstance().getJobNames()) {
                model.add(jobName);
            }

            return model;
        }

//        public ComboBoxModel doFillEnvironmentTypeItems() {
//        	ComboBoxModel model = new ComboBoxModel();
//
//            for (EnvironmentType value : EnvironmentType.values()) {
//                model.add(value.name());
//            }
//
//            return model;
//        }

        public String getDisplayName() {
            return Messages.Environment_DisplayName();
        }
    }

    public String getName() {
        return name;
    }

    public void setName(final String name) {
        this.name = name;
    }

    public String getEnvironmentType() {
        return environmentType;
    }

    public void setEnvironmentType(final String environmentType) {
        this.environmentType = environmentType;
    }

    public String getBuildJob() {
        return buildJob;
    }

    public void setBuildJob(final String buildJob) {
        this.buildJob = buildJob;
    }
    
    public void setUrl(String url) {
		this.url = url;
	}

	public String getUrl() {
        return url;
    }

    public void setUrlPrefix(String url) {
        this.url = url;
    }

}
