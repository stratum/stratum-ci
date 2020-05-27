/*
Build Parameters
BUILD_NODE: p4-dev
DOCKER_REGISTRY_IP: 10.128.13.253
DOCKER_REGISTRY_PORT: 5000
KERNEL_VERSION: 4.14.49
BAZEL_DISK_CACHE: /home/sdn/bazel-disk-cache
SDE: sdklt
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
                script{
                    if (SDE == 'sdklt') {
                        sh returnStdout: false, label: "Start building stratum-bcm:${KERNEL_VERSION}", script: """
                            git clone https://github.com/stratum/stratum.git
                            cd ${WORKSPACE}/stratum/
                            bazel build --define bcm_sdk=lt-${KERNEL_VERSION} //stratum/hal/bin/bcm/standalone:stratum_bcm_deb
                        """
                        sh returnStdout: false, label: "Start building stratum-bcm:${KERNEL_VERSION}", script: """
                            cp -f ${WORKSPACE}/stratum/bazel-bin/stratum/hal/bin/bcm/standalone/stratum_bcm_deb.deb /var/jenkins/stratum_bcm_sdklt_deb.deb
                        """
                        sh returnStdout: false, label: "Start building stratum-bcm:${KERNEL_VERSION}", script: """
                            cp ${WORKSPACE}/stratum/bazel-bin/stratum/hal/bin/bcm/standalone/stratum_bcm_deb.deb ${WORKSPACE}/stratum/stratum/hal/bin/bcm/standalone/docker/stratum_bcm_deb.deb
                            cd ${WORKSPACE}/stratum/stratum/hal/bin/bcm/standalone/docker
                            docker build -t ${DOCKER_REGISTRY_IP}:${DOCKER_REGISTRY_PORT}/stratum-bcm-sdklt:${KERNEL_VERSION} .
                            docker push ${DOCKER_REGISTRY_IP}:${DOCKER_REGISTRY_PORT}/stratum-bcm-sdklt:${KERNEL_VERSION}
                        """
                    } else if (SDE == 'opennsa') {
                        sh returnStdout: false, label: "Start building stratum-bcm:${KERNEL_VERSION}", script: """
                            git clone https://github.com/stratum/stratum.git -b dev/opennsa-wrapper
                            cd ${WORKSPACE}/stratum/
                            bazel build --define bcm_sdk=lt-${KERNEL_VERSION} //stratum/hal/bin/bcm/standalone:stratum_bcm_opennsa_deb
                        """
                        sh returnStdout: false, label: "Start building stratum-bcm:${KERNEL_VERSION}", script: """
                            cp -f ${WORKSPACE}/stratum/bazel-bin/stratum/hal/bin/bcm/standalone/stratum_bcm_opennsa_deb.deb /var/jenkins/stratum_bcm_opennsa_deb.deb
                        """
                        sh returnStdout: false, label: "Start building stratum-bcm:${KERNEL_VERSION}", script: """
                            cp ${WORKSPACE}/stratum/bazel-bin/stratum/hal/bin/bcm/standalone/stratum_bcm_opennsa_deb.deb ${WORKSPACE}/stratum/stratum/hal/bin/bcm/standalone/docker/stratum_bcm_deb.deb
                            cd ${WORKSPACE}/stratum/stratum/hal/bin/bcm/standalone/docker
                            docker build -t ${DOCKER_REGISTRY_IP}:${DOCKER_REGISTRY_PORT}/stratum-bcm-opennsa:${KERNEL_VERSION} .
                            docker push ${DOCKER_REGISTRY_IP}:${DOCKER_REGISTRY_PORT}/stratum-bcm-opennsa:${KERNEL_VERSION}
                        """
                    }
                }
            }
        }
        stage('Unit Test') {
            steps {
                sh returnStdout: false, label: "Run unit tests for stratum-bcm:${KERNEL_VERSION}", script: """
                    cd ${WORKSPACE}/stratum
                    sed -i '1i build --disk_cache=/tmp/bazel-disk-cache' .bazelrc
                    docker run --rm -v ${BAZEL_DISK_CACHE}:/tmp/bazel-disk-cache -v ${WORKSPACE}/stratum:/stratum ${DOCKER_REGISTRY_IP}:${DOCKER_REGISTRY_PORT}/stratum-unit
                """
            }
        }
    }
}
