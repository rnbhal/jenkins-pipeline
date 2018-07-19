#!/usr/bin/env groovy

/* Only keep the 10 most recent builds. */
//[$class: 'BuildDiscarderProperty',strategy: [$class: 'LogRotator', numToKeepStr: '10']]

import groovy.json.JsonOutput
import java.util.Optional
import hudson.tasks.test.AbstractTestResultAction
import hudson.model.Actionable
import hudson.tasks.junit.CaseResult


def slackNotificationChannel = 'saas-it-builds'// ex: = "builds"
def testSummary = ""
def coverageStatus = ""
def total = 0
def failed = 0
def skipped = 0
def user = "${gitlabMergedByUser}"
def gitInfo
unstableBuilds = []
def environment = "dev11"
def jacocoInfo
def jacocoCoverage
def app = "${gitlabTargetRepoName}"
def lapp
def pomPath
def pom
def manifest
def mergeInfo
def server
def rtMaven
def buildInfo
def catchError = false
def buildChangeURL = "${gitlabSourceRepoHomepage}/merge_requests/${gitlabMergeRequestIid}/diffs"
def notDeployable = ['CSPEmailService', 'SaasEmailService', 'SaasValidator']


def lastSuccessfullBuild(build) {
    if(build != null && (build.result == 'FAILURE' || build.result == 'UNSTABLE')) {
        //Recurse now to handle in chronological order
        lastSuccessfullBuild(build.getPreviousBuild())
        //Add the build to the array
        unstableBuilds.add(build)
    }
    else {
        return
    }
 }

def isResultGoodForPublishing = { ->
    return currentBuild.result == null
}

def notifySlack(text, channel, attachments) {
    def slackURL = 'https://hooks.slack.com/services/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'
    def jenkinsIcon = 'https://wiki.jenkins-ci.org/download/attachments/2916393/logo.png'
    def payload = JsonOutput.toJson([text: text,
        channel: channel,
        username: "Jenkins",
        icon_url: jenkinsIcon,
        attachments: attachments
    ])

    scriptStatus = sh(script:"curl -X POST --data-urlencode \'payload=${payload}\' ${slackURL}", returnStdout:true)
}

@NonCPS
def getTestSummary = { ->
    def testResultAction = currentBuild.rawBuild.getAction(AbstractTestResultAction.class)
    def summary = ""

    if (testResultAction != null) {
        total = testResultAction.getTotalCount()
        failed = testResultAction.getFailCount()
        skipped = testResultAction.getSkipCount()

        summary = "Passed: " + (total - failed - skipped)
        summary = summary + (", *Failed: " + failed)
        summary = summary + ("*, Skipped: " + skipped)
    } else {
        summary = "No tests found"
    }
    return summary
}

def populateGlobalVariables = {
    testSummary = getTestSummary()
}


node ('master') {
    try {
        gitlabBuilds(builds: ['checkout', 'preparations', 'build', 'Executing Test', 'Post Test', 'Configuration', 'Deploy', 'Uploading Artifact', 'Publishing Results', 'Send Slack Notification']) {
            stage('checkout') {
                //updateGitlabCommitStatus name: 'checkout', state: 'running'
                lapp = app.toLowerCase()
                sh "export HISTCONTROL=ignoreboth"
                def json = sh (returnStdout: true, script: " curl https://gitlab.eng.vmware.com/api/v4/projects/${gitlabMergeRequestTargetProjectId}/merge_requests/${gitlabMergeRequestIid}?private_token=Dq3q7X3CKBZn7dDFJamN")
                mergeInfo = readJSON text: "${json}"
                //echo sh(returnStdout: true, script: 'env')
                if(mergeInfo.merge_status != "can_be_merged"){
                    //updateGitlabCommitStatus name: 'Jenkins', state: 'canceled'
                    updateGitlabCommitStatus name: 'preparations', state: 'failed'
                    updateGitlabCommitStatus name: 'Jenkins', state: 'failed'
                    error "Pipeline cannot be run with conflicts in the merge requests"
                }
                if(mergeInfo.state == "merged")
                    gitInfo = git branch: "dev", credentialsId: '06daa9fc-551c-427d-9d4c-c5799eb727c7', url: "https://gitlab.eng.vmware.com/saas-it-eng/${app}.git"
                else
                    gitInfo = git branch: "${gitlabSourceBranch}", credentialsId: '06daa9fc-551c-427d-9d4c-c5799eb727c7', url: "https://gitlab.eng.vmware.com/saas-it-eng/${app}.git"
                //echo sh(returnStdout: true, script: 'env')
                //echo gitinfo.GIT_COMMIT
                // def commitList = sh (returnStdout: true, script: "git diff-tree --no-commit-id --name-only -r ${gitInfo.GIT_COMMIT}").split('\n')
                // for(commit in commitList){
                //     //If empty don't add
                //     if(!commit.isEmpty())
                //         applications[commit.split('/')[0]] = true
                // }
                //echo "${applications.size()}"
                updateGitlabCommitStatus name: 'checkout', state: 'success'
            }
            stage('preparations'){
                server = Artifactory.newServer url: MAVEN_REPOSITORY_BASE_URL, credentialsId: 'rbhal'
                rtMaven = Artifactory.newMavenBuild()
                buildInfo = Artifactory.newBuildInfo()
                pomPath = "pom.xml";
                manifest = readYaml file: "${MANIFEST_FILENAME}"
                pom = readMavenPom file: "${pomPath}"
                updateGitlabCommitStatus name: 'preparations', state: 'success'
            }
            stage('build') {
                //updateGitlabCommitStatus name: 'build', state: 'running'
                buildInfo = rtMaven.run pom: pomPath.toString(), goals: 'clean install -Dmaven.test.skip=true'
                updateGitlabCommitStatus name: 'build', state: 'success'
            }
            stage('Executing Test') {
                //updateGitlabCommitStatus name: 'Executing Test', state: 'running'
                buildInfo = rtMaven.run pom: pomPath.toString(), goals: 'clean install'
                updateGitlabCommitStatus name: 'Executing Test', state: 'success'
            }
            stage('Post Test') {
                //updateGitlabCommitStatus name: 'Post Test', state: 'running'
                junit allowEmptyResults: true, testResults: 'target/site/surefire-report/*.xml'
                populateGlobalVariables()
                if(total != 0){
                    jacocoInfo =  jacoco changeBuildStatus: true, maximumLineCoverage: "${REQUIRED_CODE_COVERAGE}", minimumLineCoverage: "${MINIMUM_CODE_COVERAGE}",execPattern: "target/**.exec"
                    coverageStatus = currentBuild.result == null ? "Success" : currentBuild.result
                    //sh "cp ${branchName}/src/test/resources/application-test11.properties ${branchName}/target/site/allure-results/environment.properties"
                    allure jdk: '', report: "target/site/allure-reports", results: [[path: "target/site/allure-results"]]
                    //sh "mkdir -p ../../allure-results/${branchName}"
                    //sh "cp -r ${branchName}/target/site/allure-reports ../../allure-results/${branchName}"                    
                    def xml = new XmlSlurper().parse("${env.BUILD_URL}jacoco/api/xml")
                    def elementToFind = 'percentageFloat'
                    def result = xml.'**'.findAll{it.name() == elementToFind}
                    jacocoCoverage = result[4].text()
                }
                else {
                    jacocoCoverage = 0
                    coverageStatus = "SUCCESS"
                    echo "No Tests Found"
                }
                lastSuccessfullBuild(currentBuild.getPreviousBuild())
                updateGitlabCommitStatus name: 'Post Test', state: 'success'
            }
            stage('Configuration'){
                //updateGitlabCommitStatus name: 'Configuration', state: 'running'
                def application_name = manifest.applications[0].name
                manifest.applications[0].routes[0].route="${lapp}-${environment}.${PAAS_DEV_DOMAIN}"
                def profile = ["SPRING_PROFILES_ACTIVE": "${environment}"]
                if(manifest.applications[0].env == null)
                    manifest.applications[0].env = profile
                else
                    manifest.applications[0].env.SPRING_PROFILES_ACTIVE="${environment}"
                manifest.applications[0].env.POM_VERSION="${app}-${pom.version}.jar"
                manifest.applications[0].env.COMMIT_ID="${gitlabMergeRequestLastCommit}"
                sh "rm ${MANIFEST_FILENAME}"
                writeYaml file: "${MANIFEST_FILENAME}", data: manifest
                echo "${manifest}"
                //env.MAVEN_HOME = MAVEN_HOME
                rtMaven.deployer.deployArtifacts = false
                rtMaven.deployer server: server, releaseRepo: 'test3'
                rtMaven.deployer.artifactDeploymentPatterns.addInclude("*.jar")
                rtMaven.deployer.artifactDeploymentPatterns.addInclude("*.pom")
                updateGitlabCommitStatus name: 'Configuration', state: 'success'
            }
            stage('Deploy'){
                //updateGitlabCommitStatus name: 'Deploy', state: 'success'
                def deploy = true
                for(i in notDeployable){
                    if( i == "${app}")
                        deploy = false
                }
                echo "Deploying: ${deploy}"
                if(deploy){
                    retry(2) {
                        pushToCloudFoundry cloudSpace: PAAS_DEV_SPACE, credentialsId: 'ldap_pass', manifestChoice: [manifestFile: "${MANIFEST_FILENAME}".toString()], organization: PAAS_NP_ORG, selfSigned: true, pluginTimeout: 180, target: PAAS_DEV_API_URL
                    }
                }
                //pushToCloudFoundry cloudSpace: PAAS_DEV_SPACE, credentialsId: 'ldap_pass', manifestChoice: [appName: SERVICE, appPath: 'target/${SERVICE}.jar', buildpack: PAAS_BUILD_PACK, command: '', domain: PAAS_DEV_DOMAIN, hostname: "${ENVIRONMENT}-${SERVICE}", instances: 2, memory: 1024, stack: '', timeout: 120, value: 'jenkinsConfig'], organization: PAAS_DEV_ORG, selfSigned: true, target: PAAS_DEV_API_URL
                updateGitlabCommitStatus name: 'Deploy', state: 'success'
            }
            stage('Uploading Artifact'){
                //updateGitlabCommitStatus name: 'Uploading Artifact', state: 'success'
                def checkSum = sh(returnStdout: true, script: "md5sum ${MANIFEST_FILENAME}").split(' ')[0]
                sh "curl --header 'X-Checksum-MD5:${checkSum}' -urbhal:APAKG2rr4X53xoxHGnr91tm7V94 -T ${MANIFEST_FILENAME} \"${MAVEN_REPOSITORY_URL}/com/vmware/${app}/${pom.version}/\""
                buildInfo.env.capture = true
                rtMaven.deployer.deployArtifacts buildInfo
                updateGitlabCommitStatus name: 'Uploading Artifact', state: 'success'
            }
            stage('Publishing Results') {
                //updateGitlabCommitStatus name: 'Publishing Results', state: 'success'
                server.publishBuildInfo buildInfo
                //sh "rm -rf ${service}"
                updateGitlabCommitStatus name: 'Publishing Results', state: 'success'
            }
            stage('Send Slack Notification') {
                //def blueOceanTestUrl = "${env.JENKINS_URL}" + "/blue/organizations/jenkins/slack_notification/detail/" + "${branchName}/" + "${env.BUILD_NUMBER}/tests"
                def testReportURL = "${env.BUILD_URL}" + "testReport"
                def buildColor = currentBuild.result == "NULL" ? "success" : currentBuild.result == "SUCCESS" ? "good" : currentBuild.result == "UNSTABLE" ? "warning" : "danger"
                def jacocoURL = "${env.BUILD_URL}" + "jacoco"
                def allureURL = "${env.BUILD_URL}" + "allure"
                def buildStatus = currentBuild.result == null ? "Success" : currentBuild.result
                def numberOfLastBuildsUnstable = unstableBuilds.size() + 1
                def text = ""
                def title = ""
                if(failed > 0 && (coverageStatus == "UNSTABLE" || coverageStatus == "FAILURE")){
                    if(coverageStatus == "UNSTABLE"){
                        text = "Hey! <@${user}>, please kindly resolve it\nTests Report: ${failed}/${total} Failed\nCoverage : ${jacocoCoverage}/${REQUIRED_CODE_COVERAGE}\n Last ${numberOfLastBuildsUnstable} builds failed\n<!channel>"
                        title = "#${env.BUILD_NUMBER} ${app}: Tests Failed and Coverage fell below ${REQUIRED_CODE_COVERAGE}%"
                    }
                    else {
                        text = "Hey! <@${user}>, please kindly resolve it\nTests Report: ${failed}/${total} Failed\nCoverage : ${jacocoCoverage}/${MINIMUM_CODE_COVERAGE}\n Last ${numberOfLastBuildsUnstable} builds failed\n<!channel>"
                        title = "#${env.BUILD_NUMBER} ${app}: Tests Failed and Coverage fell below ${MINIMUM_CODE_COVERAGE}%"
                    }
                } else if(failed > 0){
                    text = "Hey! <@${user}>, please, kindly fix your test cases immediately\nTests Report: ${failed}/${total} Failed\nLast ${numberOfLastBuildsUnstable} builds failed\n<!here>"
                    title = "#${env.BUILD_NUMBER} ${app}: Tests Failed"
                } else if(coverageStatus == "UNSTABLE" || coverageStatus == "FAILURE"){
                    if(coverageStatus == "UNSTABLE"){
                        text = "Hey! <@${user}>, please increase your code coverage\nCoverage : ${jacocoCoverage}/${REQUIRED_CODE_COVERAGE}\n<!here>"
                        title = "#${env.BUILD_NUMBER} ${app}: Coverage fell below ${REQUIRED_CODE_COVERAGE}% "
                    }
                    else {
                        text = "Hey! <@${user}>, please increase your code coverage\nCoverage : ${jacocoCoverage}/${MINIMUM_CODE_COVERAGE}\n<!here>"
                        title = "#${env.BUILD_NUMBER} ${app} coverage fell below ${MINIMUM_CODE_COVERAGE}%"
                    }
                }
                def rgbcolor
                if(buildColor == null || buildColor == "good"){
                    rgbcolor = "RGB(0,255,0)" // green
                }
                else if(buildColor == "warning"){
                    rgbcolor = "RGB(255,255,0)" // yellow
                }
                else{
                    rgbcolor = "RGB(255,0,0)" // red
                }
                def passed = total - failed - skipped
                addGitLabMRComment comment: """[${title}](${RUN_DISPLAY_URL})

@${gitlabMergedByUser} 

`${rgbcolor}`Status: ${buildStatus}

**Test Results:**
Passed: ${passed}, **Failed: ${failed}**, Skipped: ${skipped}
 
**Coverage:** ${jacocoCoverage}/${REQUIRED_CODE_COVERAGE}

`RGB(255,0,0)`[Allure Report](${allureURL})) `RGB(0,255,0)`[Changes](${buildChangeURL}) `RGB(255,0,0)`[Coverage Report](${jacocoURL}) `RGB(255,0,0)`[Junit Report](${testReportURL})"""
                if (failed > 0 || (coverageStatus == "UNSTABLE" || coverageStatus == "FAILURE")) {

                    notifySlack("", slackNotificationChannel, [
                        [
                            title: "${title}",
                            text: "${text}",
                            title_link: "${RUN_DISPLAY_URL}",
                            color: "${buildColor}",
                            "mrkdwn_in": ["fields"],
                            fields: [
                                [
                                    title: "Test Results",
                                    value: "${testSummary}",
                                    short: false
                                ]
                            ],
                            actions: [
                                [
                                  type: "button",
                                  text: "Allure Test Report",
                                  url: "${allureURL}",
                                  style: "danger"
                                ],
                                [
                                  type: "button",
                                  text: "Changes",
                                  url: "${buildChangeURL}",
                                  style: "primary"
                                ],
                                [
                                  type: "button",
                                  text: "Coverage Report",
                                  url: "${jacocoURL}",
                                  style: "danger"
                                ],
                                [
                                  type: "button",
                                  text: "JUnit Test Report",
                                  url: "${testReportURL}",
                                  style: "danger"
                                ]
                            ]
                        ]
                    ])
                }
                updateGitlabCommitStatus name: 'Send Slack Notification', state: 'success'
                updateGitlabCommitStatus name: 'Jenkins', state: 'success'          
            } // stage ending
        } //Gitlab build Stages
    } catch (e) {
        currentBuild.result = 'FAILURE'
        updateGitlabCommitStatus name: 'Jenkins', state: 'failed'
        catchError = true
        notifySlack("", slackNotificationChannel, [
                    [
                        title: "${gitlabTargetRepoName}, Build Failed #${env.BUILD_NUMBER}",
                        text: "Hey <!here> ! build for the ${gitlabTargetRepoName} failed. <@${user}>, please kindly resolve it immediately.",
                        title_link: "${RUN_DISPLAY_URL}",
                        color: "danger",
                        fields: [
                            [
                                title: "Error",
                                value: "${e}",
                                short: false
                            ]
                        ],
                        actions: [
                            [
                              type: "button",
                              text: "Changes",
                              url: "${buildChangeURL}",
                              style: "primary"
                            ],
                        ]
                    ]
                ])
        throw e
    } finally {
        if((currentBuild.result == null || currentBuild.result == "FAILURE") && catchError != true && coverageStatus != "FAILURE"){
            def buildStatus = "Failed"
            notifySlack("", slackNotificationChannel, [
                [
                    title: "${gitlabTargetRepoName}, Build Failed #${env.BUILD_NUMBER}",
                    text: "Hey <!here> ! build for the ${gitlabTargetRepoName} failed. <@${user}>, please kindly resolve it immediately.",
                    title_link: "${RUN_DISPLAY_URL}",
                    color: "danger",
                    actions: [
                        [
                          type: "button",
                          text: "Changes",
                          url: "${buildChangeURL}",
                          style: "primary"
                        ],
                    ]
                ]
            ])
        }
    }
}
