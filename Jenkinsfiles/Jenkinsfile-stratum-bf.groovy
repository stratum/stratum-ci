/*
Build Parameters
BUILD_NODE: p4-dev
DOCKER_REGISTRY_IP: 10.128.13.253
DOCKER_REGISTRY_PORT: 5000
*/

pipeline {
    agent {
        label "${BUILD_NODE}"
    }
    options {
        timeout(time: 120, unit: 'MINUTES')
    }
    stages {
        stage('Build, Test and Publish') {
            matrix {
                axes {
                    axis {
                        name 'SDE_VERSION'
                        values '8.9.2', '9.0.0', '9.1.0', '9.2.0', '9.3.0'
                    }
                }
                agent {
                    label "${BUILD_NODE}"
                }
                stages {
                    stage("Build") {
                        steps {
                            sh returnStdout: false, label: "Start building stratum-bf:${SDE_VERSION}", script: ""
                            build job: "stratum-bf-build", parameters: [
                                string(name: 'SDE_VERSION', value: "${SDE_VERSION}"),
                                string(name: 'DOCKER_REGISTRY_IP', value: "${DOCKER_REGISTRY_IP}"),
                                string(name: 'DOCKER_REGISTRY_PORT', value: "${DOCKER_REGISTRY_PORT}"),
                            ]
                        }
                    }
                    stage('Test') {
                        steps {
                            sh returnStdout: false, label: "Start testing ${DOCKER_REGISTRY_IP}:${DOCKER_REGISTRY_PORT}/stratum-bf:${SDE_VERSION}", script: ""
                            build job: "stratum-bf-test-combined", parameters: [
                                string(name: 'DOCKER_IMAGE', value: "${DOCKER_REGISTRY_IP}:${DOCKER_REGISTRY_PORT}/stratum-bf"),
                                string(name: 'DOCKER_IMAGE_TAG', value: "${SDE_VERSION}"),
                            ]
                        }
                    }
                    stage('Publish') {
                        steps {
                            sh returnStdout: false, label: "Start publishing ${DOCKER_REGISTRY_IP}:${DOCKER_REGISTRY_PORT}/stratum-bf:${SDE_VERSION}", script: ""
                            build job: "stratum-publish", parameters: [
                                string(name: 'DOCKER_IMAGE', value: "${DOCKER_REGISTRY_IP}:${DOCKER_REGISTRY_PORT}/stratum-bf"),
                                string(name: 'DOCKER_IMAGE_TAG', value: "${SDE_VERSION}"),
                            ]
                        }
                    }
                }
            }
        }
    }
    post {
        failure {
            slackSend color: 'danger', message: "Test failed: ${env.JOB_NAME} #${env.BUILD_NUMBER} (<${env.RUN_DISPLAY_URL}|Open>)"
        }
    }
}