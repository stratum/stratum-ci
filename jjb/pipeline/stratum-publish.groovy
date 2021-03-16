pipeline {
    agent {
        label "${BUILD_NODE}"
    }
    options {
        timeout(time: 10, unit: 'MINUTES')
    }
    environment {
        pullImageName = "${REGISTRY_URL}/${DOCKER_REPOSITORY_NAME}:${DOCKER_IMAGE_TAG}"
        pushImageName = "stratumproject/${DOCKER_REPOSITORY_NAME}:${DOCKER_IMAGE_TAG}"
    }
    stages {
        stage('Pull') {
            environment {
                REGISTRY_CREDS = credentials("${REGISTRY_CREDENTIAL}")
            }
            steps {
                sh returnStdout: false, label: "Start publishing ${DOCKER_REPOSITORY_NAME}:${DOCKER_IMAGE_TAG}", script: """
                    docker login ${REGISTRY_URL} -u ${REGISTRY_CREDS_USR} -p ${REGISTRY_CREDS_PSW}
                    docker pull ${pullImageName}
                    docker tag ${pullImageName} ${pushImageName}
                """
            }
        }
        stage('Publish') {
           steps {
                withDockerRegistry([ credentialsId: "onf_docker_hub", url: "" ]) {
                    sh returnStdout: false, label: "Start publishing ${DOCKER_REPOSITORY_NAME}:${DOCKER_IMAGE_TAG}", script: """
                        docker push ${pushImageName}
		    """
                }
            } 
        }
    }
}

