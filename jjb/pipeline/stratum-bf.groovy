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
                sh returnStdout: false, label: "Start building stratum-${TARGET}:${SDE_VERSION}", script: """
                    git clone https://github.com/stratum/stratum.git
                    cd ${WORKSPACE}/stratum && git checkout ${GIT_REFS}
                    cd ${WORKSPACE}/stratum/stratum/hal/bin/barefoot/docker
                    STRATUM_TARGET=stratum_${TARGET} SDE_INSTALL_TAR=${WORKSPACE}/${SDE_TAR} RELEASE_BUILD=true ./build-stratum-bf-container.sh
                """
            }
        }
	    stage('Push') {
	        steps {
                withDockerRegistry([ credentialsId: "${ONF_DOCKER_HUB_CREDENTIAL}", url: "" ]) {
                    sh returnStdout: false, label: "Start publishing stratum-${TARGET}:latest-${SDE_VERSION}", script: """
                        docker tag stratumproject/stratum-${TARGET}:${SDE_VERSION} stratumproject/stratum-${TARGET}:latest-${SDE_VERSION}
                        docker push stratumproject/stratum-${TARGET}:latest-${SDE_VERSION}
		            """
                }
            }
        }
    }
}
