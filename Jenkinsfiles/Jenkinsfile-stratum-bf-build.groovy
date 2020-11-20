/*
Build Parameters
BUILD_NODE: p4-dev
DOCKER_REGISTRY_IP: 10.128.13.253
DOCKER_REGISTRY_PORT: 5000
SDE_VERSION: 8.9.2
BAZEL_DISK_CACHE: /home/sdn/bazel-disk-cache
*/

pipeline {
    agent {
        label "${BUILD_NODE}"
    }
    options {
        timeout(time: 60, unit: 'MINUTES')
    }
    stages {
        stage('Build') {
            steps {
                step([$class: 'WsCleanup'])
                sh returnStdout: false, label: "Start building stratum-bf:${SDE_VERSION}", script: """
                    git clone https://github.com/stratum/stratum.git
                    cd ${WORKSPACE}/stratum
                    cd ${WORKSPACE}/stratum/stratum/hal/bin/barefoot/docker
                    if [ -f /var/jenkins/stratum-contents/bf-sde-${SDE_VERSION}-install.tgz ]; then
                        SDE_INSTALL_TAR=/var/jenkins/stratum-contents/bf-sde-${SDE_VERSION}-install.tgz \
                            ./build-stratum-bf-container.sh
                    else
                        ./build-stratum-bf-container.sh /var/jenkins/stratum-contents/bf-sde-${SDE_VERSION}.tgz \
                            /var/jenkins/stratum-contents/linux-3.16.56-OpenNetworkLinux.tar.xz \
                            /var/jenkins/stratum-contents/linux-4.9.75-OpenNetworkLinux.tar.xz \
                            /var/jenkins/stratum-contents/linux-4.14.49-OpenNetworkLinux.tar.xz
                    fi


                """
            }
        }
        stage('Push to local registry') {
            sh returnStdout: false, label: "Push stratum-bf:${SDE_VERSION} to local registry", script: """
                docker tag stratumproject/stratum-bf:${SDE_VERSION} \
                    ${DOCKER_REGISTRY_IP}:${DOCKER_REGISTRY_PORT}/stratum-bf:${SDE_VERSION}
                docker push ${DOCKER_REGISTRY_IP}:${DOCKER_REGISTRY_PORT}/stratum-bf:${SDE_VERSION}
            """
        }
        stage('Unit Test') {
            steps {
                sh returnStdout: false, label: "Run unit tests for stratum-bf:${SDE_VERSION}", script: """
                    cd ${WORKSPACE}/stratum/
                    sed -i '1i build --disk_cache=/tmp/bazel-disk-cache' .bazelrc
                    sed -i '1i startup --output_user_root=/tmp/bazel-cache/output-root' .bazelrc
                    docker run --rm -v ${BAZEL_DISK_CACHE}:/tmp/bazel-disk-cache -v ${WORKSPACE}/stratum:/stratum ${DOCKER_REGISTRY_IP}:${DOCKER_REGISTRY_PORT}/stratum-unit
                """
            }
        }
    }
}