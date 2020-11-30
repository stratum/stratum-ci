/*
Build Parameters
BUILD_NODE: p4-dev
DOCKER_REGISTRY_IP: 10.128.13.253
DOCKER_REGISTRY_PORT: 5000
SDE_VERSION: 8.9.2
KERNEL_VERSION: 4.14.49
BAZEL_DISK_CACHE: /home/sdn/bazel-disk-cache
*/

pipeline {
    agent {
        label "${BUILD_NODE}"
    }
    options {
        timeout(time: 60, unit: 'MINUTES')
        withAWS(credentials:'AKIAR6Z4ESHHIXKSMOXU')
    }
    stages {
        stage('Preparations') {
            steps {
                step([$class: 'WsCleanup'])
                s3Download(file:'bf-sde-9.1.0-install.tgz', bucket:'stratum-artifacts', path:'bf-sde-9.1.0-install.tgz', force:true)
            }
        }
        stage('Build') {
	    environment {
                REGISTRY_CREDS = credentials("aether-registry-credentials")
            }
            steps {
                sh returnStdout: false, label: "Start building stratum-bf:bf-sde-${SDE_VERSION}-linux-${KERNEL_VERSION}-OpenNetworkLinux", script: """
                    git clone https://github.com/stratum/stratum.git
                    cd ${WORKSPACE}/stratum
                    cd ${WORKSPACE}/stratum/stratum/hal/bin/barefoot/docker
                    SDE_INSTALL_TAR=${WORKSPACE}/bf-sde-${SDE_VERSION}-install.tgz ./build-stratum-bf-container.sh
                    docker tag stratumproject/stratum-bf:${SDE_VERSION} ${REGISTRY_URL}/stratum-bf:${SDE_VERSION}
                    docker login ${REGISTRY_URL} -u ${REGISTRY_CREDS_USR} -p ${REGISTRY_CREDS_PSW}
                    docker push ${REGISTRY_URL}/stratum-bf:${SDE_VERSION}
                """
            }
        }
    }
}