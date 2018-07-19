#!/usr/bin/env groovy

/* Only keep the 10 most recent builds. */
//[$class: 'BuildDiscarderProperty',strategy: [$class: 'LogRotator', numToKeepStr: '10']]

import groovy.json.JsonOutput
import java.util.Optional
import hudson.tasks.test.AbstractTestResultAction
import hudson.model.Actionable
import hudson.tasks.junit.CaseResult


def slackNotificationChannel = 'saas-it-builds'     // ex: = "builds"
def commitInformation = ""
def user = ""
def wpcf = SERVICE.split('_')[1]
unstableBuilds = []
def catchError = false
def version
def application_name

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



node () {
    try {
       stage('Choices') {
          //def service_name = input id: 'service_name', message: 'Service', parameters: [choice(choices: "SaasTokenService\nCSPUserService\nCSPEurekaService", description: 'List Of Services', name: 'Service')]
          version = input id: 'maven', message: 'Select Maven version', parameters: [[$class: 'MavenMetadataParameterDefinition', artifactId: wpcf, classifier: '', credentialsId: 'rbhal', currentArtifactInfoLabel: '', currentArtifactInfoPattern: '', currentArtifactInfoUrl: '', defaultValue: '', description: 'List of Maven Versions', groupId: GROUP_ID, maxVersions: '', name: 'Maven_Version', packaging: 'jar', repoBaseUrl: MAVEN_REPOSITORY_URL, sortOrder: "DESC", versionFilter: '']]
          sh "export HISTCONTROL=ignoreboth"
        }
        withCredentials([usernamePassword(credentialsId: 'ldap_pass', passwordVariable: 'PAAS_DEV_PASSWORD', usernameVariable: 'PAAS_DEV_USERNAME')]) {
            sh ' cf login -a ${PAAS_DEV_API_URL} -u ${PAAS_DEV_USERNAME} -p ${PAAS_DEV_PASSWORD} -o ${PAAS_DEV_ORG} -s ${PAAS_DEV_SPACE} --skip-ssl-validation'
        }
        stage('Get Jar& manifest from JFrog Repo'){
            def full_repo_path = "${MAVEN_REPOSITORY_URL}/com/vmware/$wpcf/${version}"
            sh "mkdir -p ${devcd_relative_path}/${service}"
            sh "mkdir -p ${devcd_relative_path}/${service}/target"
            sh " wget -O ${devcd_relative_path}/${service}/${MANIFEST_FILENAME} --http-user=rbhal --http-password=APHqjc12fPfBHAZVB3pFrpGarp ${full_repo_path}/${MANIFEST_FILENAME}"
            sh " wget -O ${devcd_relative_path}/${service}/target/${wpcf}.jar --http-user=rbhal --http-password=APHqjc12fPfBHAZVB3pFrpGarp ${full_repo_path}/${wpcf}-${version}.jar"
        }
        def manifest = readYaml file: "${devcd_relative_path}/${service}/${MANIFEST_FILENAME}"
        stage('modifying manifest.yml file'){
            application_name = manifest.applications[0].name
            manifest.applications[0].name = "${application_name}-temp"
            manifest.applications[0].routes[0].route="${application_name}-temp.${PAAS_DEV_DOMAIN}"
            manifest.applications[0].env.EUREKA_SERVER="eureka-${environment}.${PAAS_DEV_DOMAIN}"
            manifest.applications[0].env.POM_VERSION="${service}-${version}.jar"
            sh "rm ${devcd_relative_path}/${service}/${MANIFEST_FILENAME}"
            echo manifest.toString()
            writeYaml file: "${devcd_relative_path}/${service}/${MANIFEST_FILENAME}", data: manifest
        }
        stage('push temp app to pcf') {
            sh "cf push -f ${devcd_relative_path}/${service}/${MANIFEST_FILENAME}"
        }
        stage('routing traffic to temp app'){
            sh "cf map-route ${application_name}-temp ${PAAS_DEV_DOMAIN} --hostname ${application_name}-${environment}"
            sh "cf unmap-route ${application_name}-${environment} ${PAAS_DEV_DOMAIN} --hostname ${application_name}-${environment}"
            sh "cf unmap-route ${application_name}-temp ${PAAS_DEV_DOMAIN} --hostname ${application_name}-temp"
            sh "cf delete ${application_name}-${environment} -f"
            sh "cf rename ${application_name}-temp ${application_name}-${environment}"
        }
        stage('deleting jar'){
            sh "rm -rf ${devcd_relative_path}/${service}/target"
            sh "rm ${devcd_relative_path}/${service}/${MANIFEST_FILENAME}"
        }
    } catch (e) {
            catchError = true
            throw e
    } finally {
    }
}

4611d8294456728f

/etc/alternatives/java -Djava.awt.headless=true -DJENKINS_HOME=/var/lib/jenkins -jar /usr/lib/jenkins/jenkins.war --logfile=/var/log/jenkins/jenkins.log --webroot=/var/cache/jenkins/war --httpPort=8080 --debug=5 --handlerCountMax=100 --handlerCountMaxIdle=20
