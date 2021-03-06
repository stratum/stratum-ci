pipeline {
    agent {
        label "${BUILD_NODE}"
    }
    options {
        timeout(time: 60, unit: 'MINUTES')
    }
    stages {
        stage("Build") {
            steps {
                sh returnStdout: false, label: "Start building stratum-bmv2", script: ""
                build job: "stratum-bmv2-build", parameters: [
                    string(name: 'IMAGE_NAME', value: "${IMAGE_NAME}"),
                    string(name: 'DOCKER_REGISTRY_IP', value: "${DOCKER_REGISTRY_IP}"),
                    string(name: 'DOCKER_REGISTRY_PORT', value: "${DOCKER_REGISTRY_PORT}"),
                ]
            }
        }
        stage ('Test') {
            steps {
                sh returnStdout: false, label: "Start testing opennetworking/mn-stratum", script: ""
                build job: "stratum-bmv2-test", parameters: [
                    string(name: 'DOCKER_REGISTRY_IP', value: """${DOCKER_REGISTRY_IP}"""),
                    string(name: 'DOCKER_REGISTRY_PORT', value: """${DOCKER_REGISTRY_PORT}"""),
                    string(name: 'IMAGE_NAME', value: """${IMAGE_NAME}"""),
                ]
            }
        }
        stage ('Publish') {
            steps {
                sh returnStdout: false, label: "Start publishing opennetworking/mn-stratum", script: ""
            }
        }
    }
    post {
        failure {
            slackSend color: 'danger', message: "Test failed: ${env.JOB_NAME} #${env.BUILD_NUMBER} (<${env.RUN_DISPLAY_URL}|Open>)"
        }
    }
}
