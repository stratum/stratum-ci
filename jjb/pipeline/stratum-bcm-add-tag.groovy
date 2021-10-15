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
                        DOCKER_IMAGE_TAG=sh(script:'date +%y.%m.%d', returnStdout:true).trim()+"-"+TARGET
                    }
                }
            }
        }
        stage('Pull Latest Image') {
            steps {
                withDockerRegistry([ credentialsId: "${ONF_REGISTRY_CREDENTIAL}", url: "https://${ONF_REGISTRY_URL}" ]) {
                    sh returnStdout: false, label: "Pull stratum-bcm:latest-${TARGET}", script: """
                        docker pull ${ONF_REGISTRY_URL}/docker.io/stratumproject/stratum-bcm:latest-${TARGET}
		            """
                }
            }
        }
	    stage('Push') {
	        steps {
                withDockerRegistry([ credentialsId: "${ONF_DOCKER_HUB_CREDENTIAL}", url: "" ]) {
                    sh returnStdout: false, label: "Start publishing stratum-bcm:${DOCKER_IMAGE_TAG}", script: """
                        docker tag ${ONF_REGISTRY_URL}/docker.io/stratumproject/stratum-bcm:latest-${TARGET} stratumproject/stratum-bcm:${DOCKER_IMAGE_TAG}
                        docker push stratumproject/stratum-bcm:${DOCKER_IMAGE_TAG}
		            """
                }
            }
        }
    }
}
