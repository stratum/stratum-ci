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
	    environment {
                REGISTRY_CREDS = credentials("aether-registry-credentials")
            }
            steps {
                step([$class: 'WsCleanup'])
                script{
                    sh returnStdout: false, label: "Start building stratum-bcm_${SDE}_deb", script: """
                        git clone https://github.com/stratum/stratum.git
                        cd ${WORKSPACE}/stratum
                        sed -i 's/ -ti/ --tty/g' setup_dev_env.sh
                        ./setup_dev_env.sh -- --name stratum & 
                        sleep 120
                        docker exec -t stratum bazel build //stratum/hal/bin/bcm/standalone:stratum_bcm_${SDE}_deb
                    """
                    sh returnStdout: false, label: "Start building stratum-bcm:${SDE}", script: """
                        cp ${WORKSPACE}/stratum/bazel-bin/stratum/hal/bin/bcm/standalone/stratum_bcm_${SDE}_deb.deb ${WORKSPACE}/stratum/stratum/hal/bin/bcm/standalone/docker/stratum_bcm_deb.deb
                        cd ${WORKSPACE}/stratum/stratum/hal/bin/bcm/standalone/docker
                        docker build -t ${REGISTRY_URL}/stratum-bcm:${SDE} .
                        docker login ${REGISTRY_URL} -u ${REGISTRY_CREDS_USR} -p ${REGISTRY_CREDS_PSW}
                        docker push ${REGISTRY_URL}/stratum-bcm:${SDE}
                    """
                }
            }
        }
    }
}
