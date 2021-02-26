pipeline {
    agent {
        label "${BUILD_NODE}"
    }
    options {
        timeout(time: 60, unit: 'MINUTES')
        withAWS(credentials:"${AWS_S3_CREDENTIAL}")
    }
    environment {
        SDE_TAR = "bf-sde-${SDE_VERSION}-install.tgz"
    }
    stages {
        stage('Preparations') {
            steps {
                step([$class: 'WsCleanup'])
                s3Download(file:"${SDE_TAR}", bucket:'stratum-artifacts', path:"${SDE_TAR}", force:true)
            }
        }
        stage('Build') {
            steps {
                sh returnStdout: false, label: "Start building stratum-${STRATUM_TARGET}:${SDE_VERSION}", script: """
                    git clone https://github.com/stratum/stratum.git
                    cd ${WORKSPACE}/stratum
                    cd ${WORKSPACE}/stratum/stratum/hal/bin/barefoot/docker
                    STRATUM_TARGET=stratum_${STRATUM_TARGET} SDE_INSTALL_TAR=${WORKSPACE}/${SDE_TAR} ./build-stratum-bf-container.sh
                """
            }
        }
	stage('Push') {
	    environment {
                REGISTRY_CREDS = credentials("${REGISTRY_CREDENTIAL}")
            }
            steps {
                sh returnStdout: false, label: "Push stratum-${STRATUM_TARGET}:${SDE_VERSION} to ${REGISTRY_URL}", script: """
                    docker login ${REGISTRY_URL} -u ${REGISTRY_CREDS_USR} -p ${REGISTRY_CREDS_PSW}
                    docker tag stratumproject/stratum-${STRATUM_TARGET}:${SDE_VERSION} ${REGISTRY_URL}/stratum-${STRATUM_TARGET}:${DOCKER_IMAGE_TAG}
                    docker push ${REGISTRY_URL}/stratum-${STRATUM_TARGET}:${DOCKER_IMAGE_TAG}
                """
            }
        }
    }
}
