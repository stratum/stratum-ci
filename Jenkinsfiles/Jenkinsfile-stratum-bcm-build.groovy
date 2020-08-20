/*
Build Parameters
BUILD_NODE: p4-dev
DOCKER_REGISTRY_IP: 10.128.13.253
DOCKER_REGISTRY_PORT: 5000
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
                        sh returnStdout: false, label: "Start building stratum-bcm_${SDE}_deb", script: """
                            git clone https://github.com/stratum/stratum.git
                            cd ${WORKSPACE}/stratum/
                            bazel build //stratum/hal/bin/bcm/standalone:stratum_bcm_sdklt_deb
                            cp -f ${WORKSPACE}/stratum/bazel-bin/stratum/hal/bin/bcm/standalone/stratum_bcm_sdklt_deb.deb /var/jenkins/stratum_bcm_sdklt_deb.deb
                        """
                        sh returnStdout: false, label: "Start building stratum-bcm:${SDE}", script: """
                            cp ${WORKSPACE}/stratum/bazel-bin/stratum/hal/bin/bcm/standalone/stratum_bcm_sdklt_deb.deb ${WORKSPACE}/stratum/stratum/hal/bin/bcm/standalone/docker/stratum_bcm_deb.deb
                            cd ${WORKSPACE}/stratum/stratum/hal/bin/bcm/standalone/docker
                            docker build -t ${DOCKER_REGISTRY_IP}:${DOCKER_REGISTRY_PORT}/stratum-bcm:${SDE} .
                            docker push ${DOCKER_REGISTRY_IP}:${DOCKER_REGISTRY_PORT}/stratum-bcm:${SDE}
                        """
                    } else if (SDE == 'opennsa') {
                        sh returnStdout: false, label: "Start building stratum-bcm_${SDE}_deb", script: """
                            git clone https://github.com/stratum/stratum.git
                            cd ${WORKSPACE}/stratum/
                            bazel build //stratum/hal/bin/bcm/standalone:stratum_bcm_opennsa_deb
                            cp -f ${WORKSPACE}/stratum/bazel-bin/stratum/hal/bin/bcm/standalone/stratum_bcm_opennsa_deb.deb /var/jenkins/stratum_bcm_opennsa_deb.deb
                        """
                        sh returnStdout: false, label: "Start building stratum-bcm:${SDE}", script: """
                            cp ${WORKSPACE}/stratum/bazel-bin/stratum/hal/bin/bcm/standalone/stratum_bcm_opennsa_deb.deb ${WORKSPACE}/stratum/stratum/hal/bin/bcm/standalone/docker/stratum_bcm_deb.deb
                            cd ${WORKSPACE}/stratum/stratum/hal/bin/bcm/standalone/docker
                            docker build -t ${DOCKER_REGISTRY_IP}:${DOCKER_REGISTRY_PORT}/stratum-bcm:${SDE} .
                            docker push ${DOCKER_REGISTRY_IP}:${DOCKER_REGISTRY_PORT}/stratum-bcm:${SDE}
                        """
                    }
                }
            }
        }
        stage('Unit Test') {
            steps {
                sh returnStdout: false, label: "Run unit tests for stratum-bcm:${SDE}", script: """
                    cd ${WORKSPACE}/stratum
                    sed -i '1i build --disk_cache=/tmp/bazel-disk-cache' .bazelrc
                    sed -i '1i startup --output_user_root=/tmp/bazel-cache/output-root' .bazelrc
                    docker run --rm -v ${BAZEL_DISK_CACHE}:/tmp/bazel-disk-cache -v ${WORKSPACE}/stratum:/stratum ${DOCKER_REGISTRY_IP}:${DOCKER_REGISTRY_PORT}/stratum-unit
                """
            }
        }
    }
}
