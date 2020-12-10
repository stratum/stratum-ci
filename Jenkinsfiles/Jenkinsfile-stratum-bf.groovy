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
                        values '9.1.0', '9.2.0', '9.3.0'
                    }
                    axis {
                        name 'KERNEL_VERSION'
                        values '4.14.49'
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
                                string(name: 'KERNEL_VERSION', value: "${KERNEL_VERSION}"),
                                string(name: 'REGISTRY_URL', value: "${REGISTRY_URL}"),
                            ]
                        }
                    }
                    stage('Test') {
                        steps {
                            sh returnStdout: false, label: "Start testing ${REGISTRY_URL}/stratum-bf:${SDE_VERSION}", script: ""
                            build job: "stratum-bf-test-combined", parameters: [
                                string(name: 'REGISTRY_URL', value: "${REGISTRY_URL}"),
                                string(name: 'DOCKER_IMAGE', value: "stratum-bf"),
                                string(name: 'DOCKER_IMAGE_TAG', value: "${SDE_VERSION}"),
                            ]
                        }
                    }
                    stage('Publish') {
                        steps {
                            sh returnStdout: false, label: "Start publishing ${REGISTRY_URL}/stratum-bf:${SDE_VERSION}", script: ""
                            build job: "stratum-publish", parameters: [
                                string(name: 'REGISTRY_URL', value: "${REGISTRY_URL}"),
                                string(name: 'DOCKER_REPOSITORY_NAME', value: "stratum-bf"),
                                string(name: 'DOCKER_IMAGE_TAG', value: "${SDE_VERSION}"),
                            ]
                        }
                    }
                }
            }
        }
    }
    /*post {
        failure {
            slackSend color: 'danger', message: "Test failed: ${env.JOB_NAME} #${env.BUILD_NUMBER} (<${env.RUN_DISPLAY_URL}|Open>)"
        }
    }*/
}
