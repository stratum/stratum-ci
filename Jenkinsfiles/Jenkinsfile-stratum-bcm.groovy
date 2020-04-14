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
						//values '4.9.75', '3.16.56', '4.14.49'
						values '4.14.49'
					}
				}
				agent {
					label "${BUILD_NODE}"
				}
				stages {
					stage("Build") {
						steps {
							sh returnStdout: false, label: "Start building stratum-bcm:${KERNEL_VERSION}", script: ""
							build job: "stratum-bcm-build", parameters: [
								string(name: 'KERNEL_VERSION', value: "${KERNEL_VERSION}"),
								string(name: 'DOCKER_REGISTRY_IP', value: "${DOCKER_REGISTRY_IP}"),
								string(name: 'DOCKER_REGISTRY_PORT', value: "${DOCKER_REGISTRY_PORT}"),
							]
						}
					}
					stage('Test') {
						steps {
							sh returnStdout: false, label: "Start testing ${DOCKER_REGISTRY_IP}:${DOCKER_REGISTRY_PORT}/stratum-bcm:${KERNEL_VERSION}", script: ""
							build job: "stratum-bcm-test", parameters: [
								string(name: 'DOCKER_IMAGE', value: "${DOCKER_REGISTRY_IP}:${DOCKER_REGISTRY_PORT}/stratum-bcm"),
								string(name: 'DOCKER_IMAGE_TAG', value: "${KERNEL_VERSION}"),
							]
						}
					}
					stage('Publish') {
						when { expression { KERNEL_VERSION == '3.16.56' } }
						steps {
							sh returnStdout: false, label: "Start publishing ${DOCKER_REGISTRY_IP}:${DOCKER_REGISTRY_PORT}/stratum-bcm:${KERNEL_VERSION}", script: ""
							//build job: "stratum-publish", parameters: [
								//string(name: 'DOCKER_IMAGE', value: "${DOCKER_REGISTRY_IP}:${DOCKER_REGISTRY_PORT}/stratum-bcm"),
								//string(name: 'DOCKER_IMAGE_TAG', value: "${KERNEL_VERSION}"),
							//]
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