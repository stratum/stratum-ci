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
        timeout(time: 60, unit: 'MINUTES')
    }
    stages {
        stage('Build, Test and Publish') {
            matrix {
                axes {
                    axis {
                        name 'KERNEL_VERSION'
                        values '4.14.49'
                    }
                    axis {
                        name 'SDE'
                        values 'sdklt', 'opennsa'
                    }
                }
                agent {
                    label "${BUILD_NODE}"
                }
                stages {
                    stage("Build") {
                        steps {
                            sh returnStdout: false, label: "Start building stratum-bcm:${SDE}", script: ""
                            build job: "stratum-bcm-build", parameters: [
				string(name: 'KERNEL_VERSION', value: "${KERNEL_VERSION}"),
                                string(name: 'SDE', value: "${SDE}"),
                                string(name: 'REGISTRY_URL', value: "${REGISTRY_URL}"),
                            ]
                        }
                    }
                    stage('Test') {
                        steps {
                            sh returnStdout: false, label: "Start testing ${REGISTRY_URL}/stratum-bcm:${SDE}", script: ""
                            build job: "stratum-bcm-test-combined", parameters: [
                                string(name: 'REGISTRY_URL', value: "${REGISTRY_URL}"),
                                string(name: 'DOCKER_IMAGE', value: "stratum-bcm"),
                                string(name: 'DOCKER_IMAGE_TAG', value: "${SDE}"),
                            ]
                        }
                    }
                    stage('Publish') {
                        steps {
                            sh returnStdout: false, label: "Start publishing ${REGISTRY_URL}/stratum-bcm:${SDE}", script: ""
                            build job: "stratum-publish", parameters: [
                                string(name: 'REGISTRY_URL', value: "${REGISTRY_URL}"),
                                string(name: 'DOCKER_REPOSITORY_NAME', value: "stratum-bcm"),
                                string(name: 'DOCKER_IMAGE_TAG', value: "${SDE}"),
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
