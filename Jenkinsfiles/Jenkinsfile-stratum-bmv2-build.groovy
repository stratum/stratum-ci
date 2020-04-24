/*
Build Parameters
BUILD_NODE: p4-dev
DOCKER_REGISTRY_IP: 10.128.13.253
DOCKER_REGISTRY_PORT: 5000
IMAGE_NAME: tvrunner:bmv2
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
                sh returnStdout: false, label: "Start building opennetworking/mn-stratum", script: """
                    git clone https://github.com/stratum/stratum.git
                    docker pull stratumproject/build:build
                    cd ${WORKSPACE}/stratum/
                    docker build -t opennetworking/mn-stratum -f tools/mininet/Dockerfile .
                """
                sh returnStdout: false, label: "Start building $IMAGE_NAME", script: """
                    cd ${WORKSPACE}
                    git clone https://github.com/stratum/testvectors-runner.git
                    cd ${WORKSPACE}/testvectors-runner/
                    docker build -t stratumproject/tvrunner:bmv2 -f build/bmv2/Dockerfile .
                    docker tag stratumproject/tvrunner:bmv2 ${DOCKER_REGISTRY_IP}:${DOCKER_REGISTRY_PORT}/${IMAGE_NAME}
                    docker push ${DOCKER_REGISTRY_IP}:${DOCKER_REGISTRY_PORT}/${IMAGE_NAME}
                """
            }
        }
        stage('Unit Test') {
            steps {
                sh returnStdout: false, label: "Run unit tests for stratum-bmv2", script: """
                    cd ${WORKSPACE}/stratum
                    sed -i '1i build --disk_cache=/tmp/bazel-disk-cache' .bazelrc
                    docker run --rm -v ${BAZEL_DISK_CACHE}:/tmp/bazel-disk-cache -v ${WORKSPACE}/stratum:/stratum ${DOCKER_REGISTRY_IP}:${DOCKER_REGISTRY_PORT}/stratum-unit
                """
            }
        }
    }
}
