package de.codecentric.jenkins.dashboard.api.environments;

import org.apache.commons.lang.builder.ToStringBuilder;

/**
 * Environment Tag consisting of a key-value pair. EC2 instances can be tagged.
 * We use that concept, to attach information like instance name and deployed
 * software version to an the instance.
 * 
 * @author marcel.birkner
 * 
 */
public class EnvironmentTag {

    private String key;
    private String value;

    public EnvironmentTag(String key, String value) {
        this.key = key;
        this.value = value;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    @Override
    public String toString() {
        return ToStringBuilder.reflectionToString(this);
    }

}
