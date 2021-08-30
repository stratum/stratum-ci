pipeline {
    agent {
        label "${BUILD_NODE}"
    }
    options {
        timeout(time: 60, unit: 'MINUTES')
    }
    stages {
        stage('Preparations') {
            steps {
                step([$class: 'WsCleanup'])
                script {
                    if(params.DOCKER_IMAGE_TAG == '') {
                        DOCKER_IMAGE_TAG=sh(script:'date +%y.%m.%d', returnStdout:true).trim()+"-"+SDE_VERSION
                    }
                }
            }
        }
        stage('Pull Latest Image') {
            steps {
                withDockerRegistry([ credentialsId: "${ONF_REGISTRY_CREDENTIAL}", url: "https://${ONF_REGISTRY_URL}" ]) {
                    sh returnStdout: false, label: "Pull stratum-${TARGET}:latest-${SDE_VERSION}", script: """
                        docker pull ${ONF_REGISTRY_URL}/docker.io/stratumproject/stratum-${TARGET}:latest-${SDE_VERSION}
		            """
                }
            }
        }
	    stage('Push') {
	        steps {
                withDockerRegistry([ credentialsId: "${ONF_DOCKER_HUB_CREDENTIAL}", url: "" ]) {
                    sh returnStdout: false, label: "Start publishing stratum-${TARGET}:latest-${SDE_VERSION}", script: """
                        docker tag ${ONF_REGISTRY_URL}/docker.io/stratumproject/stratum-${TARGET}:latest-${SDE_VERSION} stratumproject/stratum-${TARGET}:${DOCKER_IMAGE_TAG}
                        docker push stratumproject/stratum-${TARGET}:${DOCKER_IMAGE_TAG}
		            """
                }
            }
        }
    }
}
