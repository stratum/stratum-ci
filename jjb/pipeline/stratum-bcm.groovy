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
                sh returnStdout: false, label: "Start building stratum-bcm:${TARGET}", script: """
                    git clone https://github.com/stratum/stratum.git
                    cd ${WORKSPACE}/stratum/stratum/hal/bin/bcm/standalone/docker
                    STRATUM_TARGET=stratum_bcm_${TARGET} RELEASE_BUILD=true ./build-stratum-bcm-container.sh 
                """
            }
        }
        stage('Push'){
            steps {
                withDockerRegistry([ credentialsId: "${ONF_DOCKER_HUB_CREDENTIAL}", url: "" ]) {
                    sh returnStdout: false, label: "Start publishing stratumproject/stratum-bcm-${TARGET}:latest", script: """
                        docker push stratumproject/stratum-bcm-${TARGET}:latest
		            """   
                }
            }
        }
    }
}