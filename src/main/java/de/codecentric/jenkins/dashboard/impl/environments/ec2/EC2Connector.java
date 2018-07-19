package de.codecentric.jenkins.dashboard.impl.environments.ec2;

import java.io.IOException;
import java.text.DateFormat;
import java.text.MessageFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import org.apache.commons.lang.time.DurationFormatUtils;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import org.springframework.util.StringUtils;

import jenkins.model.Jenkins;

import com.amazonaws.regions.Region;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.ec2.model.CreateTagsRequest;
import com.amazonaws.services.ec2.model.DescribeInstancesResult;
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ec2.model.InstanceState;
import com.amazonaws.services.ec2.model.InstanceStateName;
import com.amazonaws.services.ec2.model.Reservation;
import com.amazonaws.services.ec2.model.Tag;
import com.amazonaws.util.json.JSONException;
import com.amazonaws.util.json.JSONObject;
import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;

import de.codecentric.jenkins.dashboard.Environment;
import de.codecentric.jenkins.dashboard.api.environments.EnvironmentDetails;
import de.codecentric.jenkins.dashboard.api.environments.EnvironmentInterface;
import de.codecentric.jenkins.dashboard.api.environments.EnvironmentTag;
import de.codecentric.jenkins.dashboard.api.environments.ServerEnvironment;
import de.codecentric.jenkins.dashboard.api.environments.ServerEnvironment.ENVIRONMENT_TYPES;
import de.codecentric.jenkins.dashboard.ec2.AwsKeyCredentials;
import de.codecentric.jenkins.dashboard.impl.deploy.DeployJobVariables;
import hudson.model.Descriptor;

/**
 * Implementation of EC2 environment integration
 * 
 * @author marcel.birkner
 * 
 */
public class EC2Connector implements EnvironmentInterface {

    public static final String JENKINS_VALUE = "JENKINS";
    public static final String STAGING_VALUE = "STAGING";
    public static final String TEST_VALUE = "test11";
    public static final String DEV_VALUE = "dev11";
    public static final String UAT_VALUE = "uat";
    public static final String PROD_VALUE = "prod";

    public static final String VERSION_TAG = "Version";
    public static final String DEFAULT_INSTANCE_NAME_TAG = "Name";

    private final static Logger LOGGER = Logger.getLogger(EC2Connector.class.getName());

    private AmazonEC2 ec2;

    /**
     * Helper method to create a EC2Connector when only the credentialsId is
     * known.
     * 
     * @param credentialsId
     *            the credentialsId used to access Amazon AWS
     * @return either a connector to access AWS/EC2 or null if the credentials
     *         are not known.
     */
    public static EC2Connector getEC2Connector(final String credentialsId) {
        /*final DomainRequirement domain = new DomainRequirement();
        final AwsKeyCredentials credentials = CredentialsMatchers.firstOrNull(CredentialsProvider.lookupCredentials(AwsKeyCredentials.class, Jenkins.getInstance(), null, domain),
                        CredentialsMatchers.withId(credentialsId));
        if (credentials == null) {
            LOGGER.warning("No credentials found for ID='" + credentialsId + "'");
            return null;
        }
        return new EC2Connector(new AmazonEC2Client(credentials.getAwsAuthCredentials()));*/
    	return new EC2Connector(null);
    }

    public EC2Connector(final AmazonEC2 ec2) {
        this.ec2 = ec2;
    }

    public boolean areAwsCredentialsValid() {
        try {
            ec2.describeInstances();
            return true;
        } catch (Exception e) {
            LOGGER.info("AWS is Invalid: " + e.getMessage());
            return false;
        }
    }

    public List<ServerEnvironment> getEnvironments(Region region) {
        List<ServerEnvironment> environments = new ArrayList<ServerEnvironment>();

        ec2.setRegion(region);
        DescribeInstancesResult instances = ec2.describeInstances();
        for (Reservation reservation : instances.getReservations()) {
            for (Instance instance : reservation.getInstances()) {
                environments.add(getEnvironmentFromInstance(instance));
            }
        }

        return environments;
    }

    public List<ServerEnvironment> getEnvironmentsByTag(Region region, String searchTag) {
        LOGGER.info("getEnvironmentsByTag " + region + " tag: " + searchTag);
        List<ServerEnvironment> environments = new ArrayList<ServerEnvironment>();

        ec2.setRegion(region);
        DescribeInstancesResult instances = ec2.describeInstances();
        for (Reservation reservation : instances.getReservations()) {
            for (Instance instance : reservation.getInstances()) {
                for (Tag tag : instance.getTags()) {
                    if (tag.getValue().equalsIgnoreCase(searchTag)) {
                        environments.add(getEnvironmentFromInstance(instance));
                    }
                }
            }
        }
        return environments;
    }
    
    /*
     * 
     * Adding Custom Function to Get Info from PCF for our envrionment 
     * address will be live {appName}-{env}.{domain}
     * 
     * 
    */
    @SuppressWarnings("deprecation")
	public List<ServerEnvironment> getEnvironmentByPCF(String appNames, List<String> environmentType, String url) {
    	LOGGER.info("appName " + appNames + " environmentType: " + environmentType + " url: " + url);
		List<ServerEnvironment> environments = new ArrayList<ServerEnvironment>();
		String[] apps = appNames.split(",");
		if (apps.length == 0 || environmentType.size() == 0)
			return environments;
		for (String appName : apps) {
			ServerEnvironment serverEnv = new ServerEnvironment();
			List<EnvironmentDetails> envDetails = new ArrayList<EnvironmentDetails>();
			serverEnv.setAppName(appName);
			
			for (String environment : environmentType) {
				EnvironmentDetails envDetail = new EnvironmentDetails();
				InstanceState instanceState = new InstanceState();
				String healthCheckUrl;
				if (StringUtils.hasText(appName) && StringUtils.hasText(environment))
					healthCheckUrl = MessageFormat.format(url, appName.toLowerCase(), environment);
				else if (StringUtils.hasText(environment))
					healthCheckUrl = MessageFormat.format(url, appName.toLowerCase());
				else
					throw new IllegalArgumentException("Please provide url with atleast applicationName as a holder in the string");
				LOGGER.info("Health Check URL is: " + healthCheckUrl);
				int lindex = healthCheckUrl.lastIndexOf("com");
				String uptimeURL = null;
				if(lindex != -1)
					uptimeURL = healthCheckUrl.substring(0, lindex + 3)+"/metrics";
				HttpClient client = HttpClientBuilder.create().build();
				HttpGet request = new HttpGet(healthCheckUrl);
				request.addHeader(HttpHeaders.CONTENT_TYPE, "application/json");
				request.addHeader(HttpHeaders.CONNECTION, "close");
				HttpGet request2 = new HttpGet(uptimeURL);
				request2.addHeader(HttpHeaders.CONTENT_TYPE, "application/json");
				request2.addHeader(HttpHeaders.CONNECTION, "close");
				HttpResponse httpResponse;
				HttpResponse httpResponse2;
				try {
					httpResponse = client.execute(request);
					String entity = EntityUtils.toString(httpResponse.getEntity());
					JSONObject response = new JSONObject(entity);
					LOGGER.fine("httpResponse is " + response);
					if (httpResponse.getStatusLine().getStatusCode() == 200)
						instanceState.setName(InstanceStateName.Running);
					else {
						instanceState.setName(InstanceStateName.Stopped);
					}
					envDetail.setState(instanceState);
					Date date = new Date(Long.parseLong(response.getJSONObject("build").get("time").toString()));
					envDetail.setBuildTime(date);
					envDetail.setVersion(response.getJSONObject("build").get("version").toString());
					envDetail.setEnv(ENVIRONMENT_TYPES.getType(environment));
					envDetail.setUri(healthCheckUrl);
					
					if(uptimeURL != null) {
						LOGGER.info("uptimeURL is " + uptimeURL);
						httpResponse2 = client.execute(request2);
						String entity2 = EntityUtils.toString(httpResponse2.getEntity());
						JSONObject response2 = new JSONObject(entity2);
						String upTime = DurationFormatUtils.formatDuration(Long.parseLong(response2.get("uptime").toString()), "yyyy-MM-dd'T'HH:mm:ss.SSSZ"); 
						envDetail.setUpTime(upTime);
					}
				} catch (Exception e) {
					// TODO Auto-generated catch block
					LOGGER.severe("Exception " + e);
					instanceState.setName(InstanceStateName.Stopped);
					envDetail.setState(instanceState);
					envDetail.setUpTime(null);
					envDetail.setEnv(ENVIRONMENT_TYPES.getType(environment));
				}
				envDetails.add(envDetail);
			}
			serverEnv.setEnvrionmentDetails(envDetails);
			environments.add(serverEnv);
		}
		return environments;
    }

    private ServerEnvironment getEnvironmentFromInstance(Instance instance) {
        ServerEnvironment env = new ServerEnvironment(instance.getInstanceId(), instance.getInstanceType());
        List<EnvironmentTag> tags = new ArrayList<EnvironmentTag>();
        for (Tag tag : instance.getTags()) {
            EnvironmentTag envTag = new EnvironmentTag(tag.getKey(), tag.getValue());
            tags.add(envTag);
//            if (tag.getKey().equalsIgnoreCase(DEFAULT_INSTANCE_NAME_TAG)) {
////                env.setEnvironmentTag(tag.getValue());
//                if (tag.getValue().contains(PROD_VALUE)) {
//                    env.setType(ENVIRONMENT_TYPES.PRODUCTION);
//                } else if (tag.getValue().contains(STAGING_VALUE)) {
//                    env.setType(ENVIRONMENT_TYPES.STAGING);
//                } else if (tag.getValue().contains(JENKINS_VALUE)) {
//                    env.setType(ENVIRONMENT_TYPES.JENKINS);
//                } else {
//                    env.setType(ENVIRONMENT_TYPES.DEFAULT);
//                }
//            }
            if (tag.getKey().equalsIgnoreCase(VERSION_TAG)) {
//                env.setVersion(tag.getValue());
            }
        }
//        env.setState(instance.getState());
//        env.setLaunchTime(instance.getLaunchTime());
//        env.setPublicIpAddress(instance.getPublicIpAddress());
//        env.setPrivateIpAddress(instance.getPrivateIpAddress());
//        env.setTags(tags);
        return env;
    }

    @Override
    public boolean tagEnvironmentWithVersion(Region region, DeployJobVariables jobVariables) {
        String searchTag = jobVariables.getEnvironment();
        String version = jobVariables.getVersion();
        LOGGER.info("tagEnvironmentWithVersion " + region + " Tag " + searchTag + " version " + version);

        boolean environmentSuccessfulTagged = false;
        ec2.setRegion(region);
        DescribeInstancesResult instances = ec2.describeInstances();
        for (Reservation reservation : instances.getReservations()) {
            for (Instance instance : reservation.getInstances()) {
                for (Tag tag : instance.getTags()) {
                    if (tag.getValue().equalsIgnoreCase(searchTag)) {
                        CreateTagsRequest createTagsRequest = new CreateTagsRequest();
                        createTagsRequest.withResources(instance.getInstanceId()).withTags(new Tag(VERSION_TAG, version));
                        LOGGER.info("Create Tag " + version + " for instance " + instance.getInstanceId());
                        ec2.createTags(createTagsRequest);
                        environmentSuccessfulTagged = true;
                    }
                }
            }
        }
        return environmentSuccessfulTagged;
    }

}