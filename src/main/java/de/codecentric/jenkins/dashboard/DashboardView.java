package de.codecentric.jenkins.dashboard;

import hudson.Extension;
import hudson.model.Item;
import hudson.model.Job;
import hudson.model.Result;
import hudson.model.TopLevelItem;
import hudson.model.ViewGroup;
import hudson.model.Cause;
import hudson.model.CauseAction;
import hudson.model.Descriptor;
import hudson.model.ModifiableItemGroup;
import hudson.model.ParameterValue;
import hudson.model.ParametersAction;
import hudson.model.StringParameterValue;
import hudson.model.View;
import hudson.model.ViewDescriptor;
import hudson.model.queue.QueueTaskFuture;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;

import javax.servlet.ServletException;

import jenkins.model.Jenkins;
import jenkins.model.ParameterizedJobMixIn;

import org.apache.commons.lang.exception.ExceptionUtils;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.bind.JavaScriptMethod;
import org.springframework.util.StringUtils;

import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;

import de.codecentric.jenkins.dashboard.api.environments.ServerEnvironment;
import de.codecentric.jenkins.dashboard.api.environments.ServerEnvironment.ENVIRONMENT_TYPES;
import de.codecentric.jenkins.dashboard.api.repositories.Artifact;
import de.codecentric.jenkins.dashboard.api.repositories.RepositoryInterface;
import de.codecentric.jenkins.dashboard.impl.environments.ec2.EC2Connector;
import de.codecentric.jenkins.dashboard.impl.repositories.RepositoryTypes;
import de.codecentric.jenkins.dashboard.impl.repositories.artifactory.ArtifactoryConnector;
import de.codecentric.jenkins.dashboard.impl.repositories.nexus.NexusConnector;


/**
 * Central class for the dashboard view. When adding a new view to Jenkins page,
 * this DashboardView will appear. Each time this view is loaded, this class
 * will be called.
 * 
 */
public class DashboardView extends View {

    private final static Logger LOGGER = Logger.getLogger(DashboardView.class.getName());

    @Extension
    public static final DashboardViewDescriptor DESCRIPTOR = new DashboardViewDescriptor();

//    public static final String PARAM_VERSION = "VERSION";
//    public static final String PARAM_ENVIRONMENT = "ENVIRONMENT";
//    public static final String PARAM_EC2_REGION = "EC2_REGION";
//    public static final String PARAM_AWS_KEY= "AWS_KEY";
    
    public static final String PARAM_VERSION = "version";
	public static final String PARAM_ENVIRONMENT = "envs";
	public static final String PARAM_APPLICATION = "app";
    
    private boolean showDeployField;

    private String groupId = "";
    private String artefactId = "";

    private List<Environment> environments;

    public DashboardView(final String name) {
        super(name);
    }

    public DashboardView(final String name, final ViewGroup owner) {
        super(name, owner);
    }

    @DataBoundConstructor
    public DashboardView(final String name, final boolean showDeployField, final String groupId, final String artefactId, final List<Environment> environments) {
        this(name);
        setShowDeployField(showDeployField);
        setGroupId(groupId);
        setArtefactId(artefactId);
        setEnvironments(environments);
    }

    @Override
    public ViewDescriptor getDescriptor() {
        return DESCRIPTOR;
    }

    /**
     * Gets all the items in this collection in a read-only view.
     */
    @Override
    public Collection<TopLevelItem> getItems() {
        return new ArrayList<TopLevelItem>();
    }

    /**
     * Checks if the job is in this collection.
     */
    @Override
    public boolean contains(final TopLevelItem item) {
        return false;
    }

    @JavaScriptMethod
    public String deploy(String appName, String version, String environment) {
        LOGGER.info("Deploy " + appName + " version " + version + " to environment " + environment);

        // Get the environment with corresponding build-job
        Environment buildEnvironment = null;
        for (Environment env : environments) {
                buildEnvironment = env;
                break;
        }

//        final AbstractProject buildJob = Jenkins.getInstance().getItemByFullName(buildEnvironment.getBuildJob(), AbstractProject.class);
        final Job<?, ?> job = Jenkins.getInstance().getItemByFullName(buildEnvironment.getBuildJob(), Job.class);
//		final ParameterizedJobMixIn buildJob = JobInfoHelpers.asParameterizedJobMixIn(job);
        ParameterizedJobMixIn.ParameterizedJob pBuildJob = (ParameterizedJobMixIn.ParameterizedJob) job;
        ParameterizedJobMixIn buildJob = this.retrieveScheduleJob(job);
		String jobName = buildEnvironment.getBuildJob();
        LOGGER.info("Executing job: " + buildJob.toString());
        
        
        if (buildJob == null) {
            return String.format(Messages.DashboardView_buildJobNotFound(), buildEnvironment.getName());
        }

        if ((!buildJob.isParameterized())) {
            return Messages.DashboardView_deploymentCannotBeExecuted();
        }

//        final ParametersAction versionParam = new ParametersAction(new StringParameterValue(PARAM_VERSION, version));
//        final ParametersAction environmentParam = new ParametersAction(new StringParameterValue(PARAM_ENVIRONMENT, environment));

        final ParameterValue versionParam =  new StringParameterValue(PARAM_VERSION, version, "Version to be deployed");
		final ParameterValue environmentParam =  new StringParameterValue(PARAM_ENVIRONMENT, environment, "Env in which to be deployed");
		final ParameterValue appParam =  new StringParameterValue(PARAM_APPLICATION, appName, "Artifact to be deployed");
		
		
//        List<ParametersAction> actions = Arrays.asList(versionParam, environmentParam);
//        QueueTaskFuture<AbstractBuild> scheduledBuild = buildJob.scheduleBuild2(2, new Cause.UserIdCause(), actions);
		List<ParameterValue> actions = Arrays.asList(versionParam, environmentParam, appParam);
		QueueTaskFuture<WorkflowRun> scheduledBuild = buildJob.scheduleBuild2(pBuildJob.getQuietPeriod(), new CauseAction(new Cause.UserIdCause()), new ParametersAction(actions));
//		QueueTaskFuture<WorkflowRun> scheduledBuild = buildJob.scheduleBuild2(2, new CauseAction(new Cause.UserIdCause()), new ParametersAction(actions));
		
        Result result = Result.FAILURE;
        try {
//            AbstractBuild finishedBuild = scheduledBuild.get();
        	WorkflowRun finishedBuild = scheduledBuild.get();
            result = finishedBuild.getResult();
            LOGGER.info("Build finished with result: " + result + " completed in: " + finishedBuild.getDurationString() + ". ");
        } catch (Exception e) {
            LOGGER.severe("Error while waiting for build " + scheduledBuild.toString() + ".");
            LOGGER.severe(e.getMessage());
            LOGGER.severe(ExceptionUtils.getFullStackTrace(e));
            return String.format(Messages.DashboardView_buildJobFailed(), jobName);
        }
        if (result == Result.SUCCESS) {
            return String.format(Messages.DashboardView_buildJobScheduledSuccessfully(), jobName);
        }
        return String.format(Messages.DashboardView_buildJobSchedulingFailed(), jobName);
    }
    
    
    private ParameterizedJobMixIn retrieveScheduleJob(final Job<?, ?> job) {
        // TODO 1.621+ use standard method
        return new ParameterizedJobMixIn() {
            @Override
            protected Job asJob() {
                return job;
            }
        };
    }

    /**
     * Handles the configuration submission. Load view-specific properties here.
     */
    @Override
    protected synchronized void submit(final StaplerRequest req) throws IOException, ServletException, Descriptor.FormException {
        LOGGER.info("DashboardView submit configuration");
        req.bindJSON(this, req.getSubmittedForm()); // Mapping the JSON directly should work
    }

    /**
     * Creates a new {@link hudson.model.Item} in this collection.
     * 
     * This method should call
     * {@link ModifiableItemGroup#doCreateItem(org.kohsuke.stapler.StaplerRequest, org.kohsuke.stapler.StaplerResponse)}
     * and then add the newly created item to this view.
     * 
     * @return null if it fails
     */
    @Override
    public Item doCreateItem(final StaplerRequest req, final StaplerResponse rsp) throws IOException, ServletException {
        return Jenkins.getInstance().doCreateItem(req, rsp);
    }

    public String getDisplayDeployField() {
        return showDeployField ? "" : "display:none;";
    }

    public boolean getShowDeployField() {
        return showDeployField;
    }

    public void setShowDeployField(boolean showDeployField) {
        this.showDeployField = showDeployField;
    }

    public String getArtefactId() {
        return artefactId;
    }

    public void setArtefactId(final String artefactId) {
        this.artefactId = artefactId;
    }

    public String getGroupId() {
        return groupId;
    }

    public void setGroupId(String groupId) {
        this.groupId = groupId;
    }

    @JavaScriptMethod
    public List<Artifact> getArtifacts(String appName) {
        LOGGER.info("Getting artifacts for " + DESCRIPTOR.getRepositoryType() + "  " + appName);

        // User needs to configure an artifact repository on the global config
        // page
        if (DESCRIPTOR.getRepositoryType() == null) {
            return new ArrayList<Artifact>();
        }

        RepositoryInterface repository;
        try {
            repository = createRepository();
        } catch (URISyntaxException e) {
            e.printStackTrace();
            return new ArrayList<Artifact>();
        }

        return repository.getArtefactList(groupId, appName);
    }

    private RepositoryInterface createRepository() throws URISyntaxException {
        URI repositoryURI = new URI(DESCRIPTOR.getRepositoryRestUri());
        RepositoryInterface repository;

        if (DESCRIPTOR.getRepositoryType().equalsIgnoreCase(RepositoryTypes.ARTIFACTORY.getid())) {
            repository = new ArtifactoryConnector(DESCRIPTOR.getUsername(), DESCRIPTOR.getPassword(), repositoryURI);
        } else {
            repository = new NexusConnector(DESCRIPTOR.getUsername(), DESCRIPTOR.getPassword(), repositoryURI);
        }
        return repository;
    }

    public List<ServerEnvironment> getMatchingEC2Environments() {
        final List<ServerEnvironment> list = new ArrayList<ServerEnvironment>();
        for (Environment env : environments) {
            final EC2Connector envConn = EC2Connector.getEC2Connector(env.getName());
//            if (envConn == null || !envConn.areAwsCredentialsValid()) {
//                LOGGER.info("Invalid credentials in environment '" + env.getName() + "'");
//                continue;
//            }
//            List<ServerEnvironment> foundEnvironments = envConn.getEnvironmentsByTag(Region.getRegion(Regions.fromName(env.getRegion())), env.getAwsInstance());
        	List<ServerEnvironment> foundEnvironments = envConn.getEnvironmentByPCF(env.getName(),
					this.getEnvList(), env.getUrl());
//            updateEnvironmentsWithUrlPrePostFix(foundEnvironments, env);
            list.addAll(foundEnvironments);
        }
        return list;
    }
    
    public List<String> getEnvList() {
		List<String> envList = new ArrayList<String>();
		for (Environment env : environments) {
			if (StringUtils.hasText(env.getEnvironmentType())) {
				String[] targetenvs = env.getEnvironmentType().split(",");
				for (String targetenv : targetenvs)
					envList.add(targetenv);
			}
		}
		return envList;
	}
	
	public List<String> getAppList() {
		List<String> appList = new ArrayList<String>();
		for (Environment env : environments) {
			if (StringUtils.hasText(env.getName())) {
				String[] appName = env.getName().split(",");
				for (String app : appName)
					appList.add(app);
			}
		}
		return appList;
	}

    private void updateEnvironmentsWithUrlPrePostFix(List<ServerEnvironment> foundEnvironments, Environment environment) {
        for (ServerEnvironment serverEnvironment : foundEnvironments) {
//            serverEnvironment.setAppName(environment.getName());
            if( serverEnvironment.getType().equals( ENVIRONMENT_TYPES.DEFAULT ) ) {
//                serverEnvironment.setType(ENVIRONMENT_TYPES.getType(environment.getEnvironmentType()));
            }
        }
    }

    public List<Environment> getEnvironments() {
        return Collections.unmodifiableList(environments);
    }

    public void setEnvironments(final List<Environment> environmentsList) {
        this.environments = environmentsList == null ? new ArrayList<Environment>() : new ArrayList<Environment>(environmentsList);
    }

}
