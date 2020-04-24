/*
Build Parameters
BUILD_NODE: p4-dev
DOCKER_REGISTRY_IP: 10.128.13.253
DOCKER_REGISTRY_PORT: 5000
*/
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
                sh returnStdout: false, label: "Preparations", script: """
                cd /var/jenkins
                docker pull stratumproject/build:build
                docker build -t ${DOCKER_REGISTRY_IP}:${DOCKER_REGISTRY_PORT}/stratum-unit -f Dockerfile.unit .
                """
            }
        }
        stage('Build, Test and Publish') {
            matrix {
                axes {
                    axis {
                        name 'PLATFORM'
                        values 'bf', 'bcm', 'bmv2'
                    }
                }
                stages {
                    stage("Build") {
                        steps {
                            sh returnStdout: false, label: "Start building stratum for ${PLATFORM}", script: ""
                            build job: "stratum-${PLATFORM}", parameters: [
                                string(name: 'DOCKER_REGISTRY_IP', value: "${DOCKER_REGISTRY_IP}"),
                                string(name: 'DOCKER_REGISTRY_PORT', value: "${DOCKER_REGISTRY_PORT}"),
                            ]
                        }
                    }
                }
            }
        }
    }
    post {
        always {
            sh returnStdout: false, label: "Cleanup", script: """
                docker rmi -f \$(docker images -f "dangling=true" -q)
            """
        }
    }
}
