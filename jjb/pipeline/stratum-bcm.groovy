pipeline {
    agent {
        label "${BUILD_NODE}"
    }
    options {
        timeout(time: 60, unit: 'MINUTES')
    }
    stages {
        stage('Build Debian Package') {
            steps {
                step([$class: 'WsCleanup'])
                sh returnStdout: false, label: "Start building stratum-bcm_${SDE}_deb", script: """
                    git clone https://github.com/stratum/stratum.git
                    cd ${WORKSPACE}/stratum
                    sed -i 's/ -ti/ --tty/g' setup_dev_env.sh
                    ./setup_dev_env.sh -- --name stratum & 
                    sleep 120
                    docker exec -t stratum bazel build //stratum/hal/bin/bcm/standalone:stratum_bcm_${SDE}_deb
                """
            }
        }
        stage('Save Debian Package') {
            steps {
                withAWS(credentials:"${AWS_S3_CREDENTIAL}") {
                    s3Upload(file:"${WORKSPACE}/stratum/bazel-bin/stratum/hal/bin/bcm/standalone/stratum_bcm_${SDE}_deb.deb", bucket:'stratum-artifacts', path:"stratum_bcm_${SDE}_deb.deb")
                }
            }
        }
        stage('Build Docker Image') {
            steps {
                sh returnStdout: false, label: "Start building stratum-bcm:${SDE}", script: """
                    cp ${WORKSPACE}/stratum/bazel-bin/stratum/hal/bin/bcm/standalone/stratum_bcm_${SDE}_deb.deb ${WORKSPACE}/stratum/stratum/hal/bin/bcm/standalone/docker/stratum_bcm_deb.deb
                    cd ${WORKSPACE}/stratum/stratum/hal/bin/bcm/standalone/docker
                    docker build -t stratumproject/stratum-bcm:latest-${SDE} .
                """
            }
        }
        stage('Push'){
            steps {
                withDockerRegistry([ credentialsId: "${ONF_DOCKER_HUB_CREDENTIAL}", url: "" ]) {
                    sh returnStdout: false, label: "Start publishing stratumproject/stratum-bcm:latest-${SDE}", script: """
                        docker push stratumproject/stratum-bcm:latest-${SDE}
		            """   
                }
            }
        }
    }
}