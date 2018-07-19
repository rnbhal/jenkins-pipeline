package de.codecentric.jenkins.dashboard.api.environments;

import java.util.Date;
import java.util.List;

import org.apache.commons.lang.builder.ToStringBuilder;

import com.amazonaws.services.ec2.model.InstanceState;

import de.codecentric.jenkins.dashboard.Messages;

/**
 * Detailed server environment information. These information are displayed on
 * the dashboard view.
 * 
 * @author marcel.birkner
 * 
 */
public class ServerEnvironment {

	public enum ENVIRONMENT_TYPES {
		DEFAULT, DEV11, TEST11, UAT, PRODUCTION, STAGING, JENKINS;

		public static String getType(String type) {
			if (type != null) {
				for (ENVIRONMENT_TYPES t : ENVIRONMENT_TYPES.values()) {
					if (type.equalsIgnoreCase(t.toString())) {
						return type;
					}
				}
			}
			return "DEFAULT";
		}

	}

	private String appName;
	private List<EnvironmentDetails> envrionmentDetails;
	private ENVIRONMENT_TYPES type;

	public ServerEnvironment() {
	}

	public ServerEnvironment(String instanceId, String instanceType) {
		this.type = ENVIRONMENT_TYPES.DEFAULT;
	}

	/*
	 * public String displayWebAppLink() { if
	 * (state.getName().equalsIgnoreCase("running")) { return "true"; } return
	 * "false"; }
	 */
	public String getAppName() {
		return appName;
	}

	public List<EnvironmentDetails> getEnvrionmentDetails() {
		return envrionmentDetails;
	}

	public ENVIRONMENT_TYPES getType() {
		return type;
	}

	/*
	 * public String getPublicIpAddress() { if
	 * (state.getName().equalsIgnoreCase("running")) { return publicIpAddress; }
	 * return Messages.ServerEnvironment_serverNotRunning(); }
	 */

	public void setAppName(String appName) {
		this.appName = appName;
	}

	/*
	 * public String getWebAppLink() { if
	 * (state.getName().equalsIgnoreCase("running")) { return uri; } return
	 * Messages.ServerEnvironment_serverNotRunning(); }
	 */

	public void setEnvrionmentDetails(List<EnvironmentDetails> envrionmentDetails) {
		this.envrionmentDetails = envrionmentDetails;
	}

	@Override
	public String toString() {
		return ToStringBuilder.reflectionToString(this);
	}

}
