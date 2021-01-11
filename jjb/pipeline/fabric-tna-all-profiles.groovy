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
                        name 'PROFILE'
                        values 'fabric', 'fabric-spgw'
                    }
                }
                agent {
                    label "${BUILD_NODE}"
                }
                stages {
                    stage('Test "${PROFILE}"') {
                        steps {
                            sh returnStdout: false, label: "Start testing ${PROFILE}", script: ""
                            build job: "fabric-tna-${SWITCH_NAME}", parameters: [
                                string(name: 'REGISTRY_URL', value: "${REGISTRY_URL}"),
                                string(name: 'DOCKER_IMAGE', value: "${DOCKER_IMAGE}"),
                                string(name: 'DOCKER_IMAGE_TAG', value: "${DOCKER_IMAGE_TAG}"),
                                string(name: 'SDE_DOCKER_IMAGE', value: "${SDE_DOCKER_IMAGE}"),
                                string(name: 'SDE_DOCKER_IMAGE_TAG', value: "${SDE_DOCKER_IMAGE_TAG}"),
                                string(name: 'SDE_VERSION', value: "${SDE_VERSION}"),
                                string(name: 'PROFILE', value: "${PROFILE}"),
                                string(name: 'CPU_PORT', value: "${CPU_PORT}"),
                            ]
                        }
                    }
                    stage('Publish') {
                        steps {
                            sh returnStdout: false, label: "Start publishing results for fabric-tna-${SWITCH_NAME}-all-profiles", script: ""
			    copyArtifacts filter: 'tv_result*.csv', fingerprintArtifacts: true, projectName: 'fabric-tna-${SWITCH_NAME}', selector: lastSuccessful(), target: '${WORKSPACE}'
                        }
                    }
                }
            }
        }
	stage('Publish Artifacts') {
	    steps{
		archiveArtifacts artifacts: "${WORKSPACE}/tv_result*.csv", allowEmptyArchive: true
	    }
	}
    }
    /*post {
        failure {
            slackSend color: 'danger', message: "Test failed: ${env.JOB_NAME} #${env.BUILD_NUMBER} (<${env.RUN_DISPLAY_URL}|Open>)"
        }
    }*/
}
