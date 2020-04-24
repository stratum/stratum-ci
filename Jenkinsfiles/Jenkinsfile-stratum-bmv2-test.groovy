/*
Build Parameters
TEST_DRIVER: p4-dev
DOCKER_REGISTRY_IP: 10.128.13.253
DOCKER_REGISTRY_PORT: 5000
IMAGE_NAME: tvrunner:bmv2
*/

def test_config = null

pipeline {
    agent {
        label "${TEST_DRIVER}"
    }
    options {
        timeout(time: 60, unit: 'MINUTES')
    }
    environment {
        SWITCH_NAME = "bmv2"
    }
    stages {
        stage('Preparations') {
            steps {
                sh returnStdout: false, label: "Start testing ${IMAGE_NAME}", script: ""
                sh returnStdout: false, label: "Get Test Vectors" , script: """
                    rm -rf testvectors testvectors-runner
                    git clone https://github.com/stratum/testvectors-runner.git
                    git clone https://github.com/abhilashendurthi/testvectors.git
                """
                sh returnStdout: false, script: """
                    cd testvectors-runner
                    sed -i 's/ -ti//g' tvrunner.sh
                """
            }
        }
        stage("Pull Stratum Docker Image") {
            steps {
                sh returnStdout: false, label: "Pull Stratum Docker Image" , script: """
                    docker pull $DOCKER_REGISTRY_IP:$DOCKER_REGISTRY_PORT/$IMAGE_NAME
                """
            }
        }
        stage("Restart Stratum on Switch") {
            steps {
                sh returnStdout: false, label: "Restart Stratum on Switch" , script: """
                    docker rm -f $SWITCH_NAME || true
                    tmux new -d -s CI-BMv2 || true
                    tmux send-keys -t CI-BMv2.0 ENTER 'docker run --rm -it --privileged --name bmv2 --network host $DOCKER_REGISTRY_IP:$DOCKER_REGISTRY_PORT/$IMAGE_NAME' ENTER
                """
            }
        }
        stage ('Setup') {
            steps {
                sh returnStdout: false, label: "Setup Loopback Mode" , script: """
                    cd testvectors-runner
                    ./tvrunner.sh --target ${env.WORKSPACE}/testvectors/$SWITCH_NAME/target.pb.txt --portmap ${env.WORKSPACE}/testvectors/$SWITCH_NAME/portmap.pb.txt  --tv-dir ${env.WORKSPACE}/testvectors/$SWITCH_NAME --tv-name PipelineConfig
                """
            }
        }
        stage ('Run Test Vectors') {
            steps {
                sh returnStdout: false, label: "Run Test Vectors" , script: """
                    cd testvectors-runner
                    ./tvrunner.sh --target ${env.WORKSPACE}/testvectors/$SWITCH_NAME/target.pb.txt --portmap ${env.WORKSPACE}/testvectors/$SWITCH_NAME/portmap.pb.txt  --tv-dir ${env.WORKSPACE}/testvectors/$SWITCH_NAME/gnmi
                    ./tvrunner.sh --target ${env.WORKSPACE}/testvectors/$SWITCH_NAME/target.pb.txt --portmap ${env.WORKSPACE}/testvectors/$SWITCH_NAME/portmap.pb.txt  --tv-dir ${env.WORKSPACE}/testvectors/$SWITCH_NAME/e2e
                    ./tvrunner.sh --target ${env.WORKSPACE}/testvectors/$SWITCH_NAME/target.pb.txt --portmap ${env.WORKSPACE}/testvectors/$SWITCH_NAME/portmap.pb.txt  --tv-dir ${env.WORKSPACE}/testvectors/$SWITCH_NAME/p4runtime
                """
            }
        }
        stage ('Cleanup') {
            steps {
                sh returnStdout: false, label: "Cleanup" , script: """
                """
            }
        }
    }
}
