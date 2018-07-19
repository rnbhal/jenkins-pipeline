package de.codecentric.jenkins.dashboard.api.environments;

import java.util.Date;

import com.amazonaws.services.ec2.model.InstanceState;

public class EnvironmentDetails {
	
	private String version;
	
	private String uri;
	
	private String upTime;
	
	private Date buildTime;

	private String env;

	private InstanceState state;
	
	public EnvironmentDetails() {
	}
	
	public Date getBuildTime() {
		return buildTime;
	}

	public String getEnv() {
		return env;
	}

	public InstanceState getState() {
		return state;
	}

	public String getUpTime() {
		return upTime;
	}

	public String getUri() {
		return uri;
	}

	public String getVersion() {
		return version;
	}

	public void setBuildTime(Date buildTime) {
		this.buildTime = buildTime;
	}

	public void setEnv(String env) {
		this.env = env;
	}

	public void setState(InstanceState state) {
		this.state = state;
	}

	public void setUpTime(String upTime) {
		this.upTime = upTime;
	}

	public void setUri(String uri) {
		this.uri = uri;
	}

	public void setVersion(String version) {
		this.version = version;
	} 

}
