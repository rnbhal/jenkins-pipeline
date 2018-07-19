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
def appName = app
unstableBuilds = []
def catchError = false
def jarVersion
def application_name
def manifest
def env = envs
def domain
def org
def space


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


pipeline{
    agent any
    options {
        timeout(time: 1, unit: 'HOURS') 
    }
    parameters {
        choice choices: ['SaasTokenService', 'SaasUserService', 'SaasEurekaService', 'SaasCloudConfig', 'SaasContractService', 'SaasUsageCapture', 'SaasFundGroupService', 'SaasCreditCardService', 'SaasCreditCardIntegration', 'SaasPayByInvoice', 'SaasPaidOrderService', 'SaasBillingService', 'SaasMicroService', 'SaasSupportService', 'SaasSubscriptionService'], description: 'List of Services', name: 'app'
        choice choices: ['dev11', 'test11', 'uat', 'stage', 'test36', 'prod'], description: 'Environment to which the jar will be deployed', name: 'envs'
        input id: 'maven', message: 'Select Maven version', parameters: [[$class: 'MavenMetadataParameterDefinition', artifactId: params.app, classifier: '', credentialsId: 'rbhal', currentArtifactInfoLabel: '', currentArtifactInfoPattern: '', currentArtifactInfoUrl: '', defaultValue: '', description: 'List of Maven Versions', groupId: GROUP_ID, maxVersions: '', name: 'Maven_Version', packaging: 'jar', repoBaseUrl: MAVEN_REPOSITORY_URL, sortOrder: "DESC", versionFilter: '']]
    }
    stages{
       stage('Prepartion') {
          //def appName_name = input id: 'appName_name', message: 'appName', parameters: [choice(choices: "SaasTokenappName\nCSPUserappName\nCSPEurekaappName", description: 'List Of appNames', name: 'appName')]
          if(version == null || version.isEmpty())
            jarVersion = 
          else
             jarVersion = version
          sh "export HISTCONTROL=ignoreboth"
          if(envs == "production" || envs == "lt"){
            org = PAAS_ORG
            domain = PAAS_DOMAIN
          }
          else {
            org = PAAS_NP_ORG
            domain = PAAS_DEV_DOMAIN
          }

        }
        stage('Login'){
            withCredentials([usernamePassword(credentialsId: 'ldap_pass', passwordVariable: 'PAAS_DEV_PASSWORD', usernameVariable: 'PAAS_DEV_USERNAME')]) {
                if(envs == "dev11")
                    sh " cf login -a ${PAAS_DEV_API_URL} -u ${PAAS_DEV_USERNAME} -p ${PAAS_DEV_PASSWORD} -o ${org} -s ${PAAS_DEV_SPACE} --skip-ssl-validation"
                else if(envs == "test11")
                    sh " cf login -a ${PAAS_DEV_API_URL} -u ${PAAS_DEV_USERNAME} -p ${PAAS_DEV_PASSWORD} -o ${org} -s ${PAAS_TEST11_SPACE} --skip-ssl-validation"
                else if(envs == "uat")
                    sh " cf login -a ${PAAS_DEV_API_URL} -u ${PAAS_DEV_USERNAME} -p ${PAAS_DEV_PASSWORD} -o ${org} -s ${PAAS_UAT_SPACE} --skip-ssl-validation"
                else if(envs == "lt")
                    sh " cf login -a ${PAAS_DEV_API_URL} -u ${PAAS_DEV_USERNAME} -p ${PAAS_DEV_PASSWORD} -o ${org} -s ${PAAS_LT_SPACE} --skip-ssl-validation"
                else if(envs == "test36")
                    sh " cf login -a ${PAAS_DEV_API_URL} -u ${PAAS_DEV_USERNAME} -p ${PAAS_DEV_PASSWORD} -o ${org} -s ${PAAS_TEST36_SPACE} --skip-ssl-validation"
                else if(envs == "cstg")
                    sh " cf login -a ${PAAS_DEV_API_URL} -u ${PAAS_DEV_USERNAME} -p ${PAAS_DEV_PASSWORD} -o ${org} -s ${PAAS_CSTG_SPACE} --skip-ssl-validation"
                else if(envs == "production"){
                    sh " cf login -a ${PAAS_DEV_API_URL} -u ${PAAS_DEV_USERNAME} -p ${PAAS_DEV_PASSWORD} -o ${org} -s ${PAAS_PRODUCTION_SPACE} --skip-ssl-validation"
                }
            }
        }
        stage('Download'){
            def full_repo_path = "${MAVEN_REPOSITORY_URL}/com/vmware/$appName/${jarVersion}"
            sh "mkdir -p ${appName}/target"
            sh " wget -O ${appName}/${MANIFEST_FILENAME} --http-user=rbhal --http-password=APAKG2rr4X53xqTce8KLWttKXS7 ${full_repo_path}/${MANIFEST_FILENAME}"
            sh " wget -O ${appName}/target/${appName}.jar --http-user=rbhal --http-password=APAKG2rr4X53xqTce8KLWttKXS7 ${full_repo_path}/${appName}-${jarVersion}.jar"
        }
        
        stage('Modify Manifest'){
            manifest = readYaml file: "${appName}/${MANIFEST_FILENAME}"
            def profile = ["SPRING_PROFILES_ACTIVE": "${env}"]
            application_name = manifest.applications[0].name
            manifest.applications[0].name = "${application_name}-temp"
            manifest.applications[0].routes[0].route= "${application_name}".toLowerCase() + "-${env}-temp.${domain}"
            if(manifest.applications[0].env == null)
                manifest.applications[0].env = profile
            else
                manifest.applications[0].env.SPRING_PROFILES_ACTIVE="${envs}"
            sh "rm ${appName}/${MANIFEST_FILENAME}"
            echo manifest.toString()
            writeYaml file: "${appName}/${MANIFEST_FILENAME}", data: manifest
        }
        stage("Deploy") {
            sh "cf push -f ${appName}/${MANIFEST_FILENAME}"
        }
        stage('Blue Green Deployment'){
            def status = sh returnStatus: true, returnStdout: false, script: "cf app ${application_name}"
            def lcap = application_name.toLowerCase()
            sh "cf map-route ${application_name}-temp ${domain} --hostname ${lcap}-${envs}"
            if(status == 0)
                sh "cf unmap-route ${application_name} ${domain} --hostname ${lcap}-${envs}"
            sh "cf unmap-route ${application_name}-temp ${domain} --hostname ${lcap}-${env}-temp"
            sh "cf delete-route ${domain} --hostname ${lcap}-${env}-temp -f"
            if(status == 0)
                sh "cf delete ${application_name} -f"
            sh "cf rename ${application_name}-temp ${application_name}"
        }
        stage('Delete Extra'){
            sh "rm -rf ${appName}/target"
            sh "rm ${appName}/${MANIFEST_FILENAME}"
        }
    }
    post {
        always{
            echo "Job has been completed"
        }
        cleanup{

        }
    }
}
