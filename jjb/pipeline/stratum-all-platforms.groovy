pipeline {
    agent {
        label "${BUILD_NODE}"
    }
    options {
        timeout(time: 60, unit: 'MINUTES')
    }
    stages {
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
