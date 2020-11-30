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
                    docker tag opennetworking/mn-stratum ${DOCKER_REGISTRY_IP}:${DOCKER_REGISTRY_PORT}/mn-stratum
                    docker push ${DOCKER_REGISTRY_IP}:${DOCKER_REGISTRY_PORT}/mn-stratum
                """
                sh returnStdout: false, label: "Start building $IMAGE_NAME", script: """
                    cd ${WORKSPACE}
                    git clone https://github.com/stratum/testvectors-runner.git
                    cd ${WORKSPACE}/testvectors-runner/
                    sed -i 's/opennetworkinglab/${DOCKER_REGISTRY_IP}:${DOCKER_REGISTRY_PORT}/g' build/bmv2/Dockerfile
                    docker build -t stratumproject/tvrunner:bmv2 -f build/bmv2/Dockerfile .
                    docker tag stratumproject/tvrunner:bmv2 ${DOCKER_REGISTRY_IP}:${DOCKER_REGISTRY_PORT}/${IMAGE_NAME}
                    docker push ${DOCKER_REGISTRY_IP}:${DOCKER_REGISTRY_PORT}/${IMAGE_NAME}
                """
            }
        }
    }
}
