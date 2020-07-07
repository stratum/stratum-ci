/*
Build Parameters
TEST_DRIVER: p4-dev
SWITCH_NAME: x86-64-stordis-bf2556x-1t-r0
DOCKER_IMAGE: 10.128.13.253:5000/stratum-bf
DOCKER_IMAGE_TAG: bf-sde-8.9.2-linux-4.14.49-OpenNetworkLinux
*/

def test_config = null

pipeline {
    agent {
        label "${TEST_DRIVER}"
    }
    options {
        timeout(time: 60, unit: 'MINUTES')
    }
    stages {
        stage('Preparations') {
            steps {
                sh returnStdout: false, label: "Start testing on ${SWITCH_NAME} with ${DOCKER_IMAGE}:${DOCKER_IMAGE_TAG}", script: ""
                step([$class: 'WsCleanup'])
                script {
                    try {
                        sh returnStdout: false, label: "Get Stratum CI repo" , script: """
                            git clone https://github.com/stratum/stratum-ci.git
                        """
                        test_config = readYaml file: "${WORKSPACE}/stratum-ci/resources/test-config.yaml"
                        tv_config_dir = "${WORKSPACE}/stratum-ci/tv_configs"
                        stratum_configs_dir = "${WORKSPACE}/stratum-ci/stratum_configs"
                        stratum_resources_dir = "${WORKSPACE}/stratum-ci/resources/barefoot"
                    } catch (err) {
                        echo "Error reading ${WORKSPACE}/stratum-ci/resources/test-config.yaml"
                        throw err
                    }
                }
            }
        }
        stage("Start Testing") {
            environment {
                SWITCH_CREDS = credentials("${SWITCH_NAME}-credentials")
                SWITCH_IP = """${test_config.switches["${SWITCH_NAME}"].ip}"""
                CONFIG_DIR = '/tmp/stratum_configs'
                RESOURCE_DIR = '/tmp/barefoot'
            }
            steps {
                script {
                    lock("${SWITCH_NAME}") {
                        node("${TEST_DRIVER}") {
                            def WORKSPACE = pwd()
                            stage("Start Stratum on ${SWITCH_NAME}") {
                                sh returnStdout: false, label: "Copy Config Files", script: """
                                    sshpass -p $SWITCH_CREDS_PSW ssh $SWITCH_CREDS_USR@$SWITCH_IP "mkdir -p $CONFIG_DIR"
                                    sshpass -p $SWITCH_CREDS_PSW scp -r ${stratum_configs_dir}/${SWITCH_NAME} $SWITCH_CREDS_USR@$SWITCH_IP:${CONFIG_DIR}/${SWITCH_NAME}
                                """
                                sh returnStdout: false, label: "Copy Stratum Scripts", script: """
                                    sshpass -p $SWITCH_CREDS_PSW ssh $SWITCH_CREDS_USR@$SWITCH_IP "mkdir -p $CONFIG_DIR"
                                    sshpass -p $SWITCH_CREDS_PSW scp -r ${stratum_resources_dir} $SWITCH_CREDS_USR@$SWITCH_IP:${RESOURCE_DIR}
                                """
                                sh returnStdout: false, label: "Starting Stratum with image ${DOCKER_IMAGE}:${DOCKER_IMAGE_TAG}", script: """
                                    sshpass -p $SWITCH_CREDS_PSW ssh $SWITCH_CREDS_USR@$SWITCH_IP "tmux kill-session -t CI || true"
                                    sshpass -p $SWITCH_CREDS_PSW ssh $SWITCH_CREDS_USR@$SWITCH_IP "tmux new -d -s CI || true"
                                    sshpass -p $SWITCH_CREDS_PSW ssh $SWITCH_CREDS_USR@$SWITCH_IP "tmux send-keys -t CI.0 ENTER 'docker pull ${DOCKER_IMAGE}:${DOCKER_IMAGE_TAG}' ENTER"
                                    sshpass -p $SWITCH_CREDS_PSW ssh $SWITCH_CREDS_USR@$SWITCH_IP "tmux send-keys -t CI.0 ENTER 'cd ${RESOURCE_DIR}' ENTER"
                                    sshpass -p $SWITCH_CREDS_PSW ssh $SWITCH_CREDS_USR@$SWITCH_IP "tmux send-keys -t CI.0 ENTER 'DOCKER_IMAGE=${DOCKER_IMAGE} DOCKER_IMAGE_TAG=${DOCKER_IMAGE_TAG} CHASSIS_CONFIG=${CONFIG_DIR}/${SWITCH_NAME}/chassis_config.pb.txt ./restart-stratum.sh --bf-sim' ENTER"
                                    sleep 180
                                """
                            }
                            stage('Get Test Vectors') {
                                step([$class: 'WsCleanup'])
                                sh returnStdout: false, label: "Get Test Vectors" , script: """
                                    git clone https://github.com/stratum/testvectors-runner.git
                                    git clone https://github.com/stratum/testvectors.git
                                    cd testvectors-runner
                                    sed -i 's/ -ti//g' tvrunner.sh
                                """
                            }
                            stage('Setup Loopback Mode') {
                                script {
                                    ports =sh returnStdout: true, label: "Find number of ports", script: """
                                        sshpass -p $SWITCH_CREDS_PSW ssh $SWITCH_CREDS_USR@$SWITCH_IP "/lib/platform-config/current/onl/bin/onlps sfp inventory |wc -l"
                                    """
                                    echo ports
                                    if (ports.toInteger() == 66) {
                                        sh returnStdout: false, label: "Push pipeline config 64" , script: """
                                            cd testvectors-runner
                                            ./tvrunner.sh --target ${tv_config_dir}/$SWITCH_NAME/target.pb.txt --portmap ${tv_config_dir}/$SWITCH_NAME/loopback-portmap.pb.txt --template-config ${tv_config_dir}/$SWITCH_NAME/template_config.json --tv-dir ${WORKSPACE}/testvectors/tofino --dp-mode loopback --tv-name PipelineConfig64
                                        """
                                    } else {
                                        sh returnStdout: false, label: "Push pipeline config" , script: """
                                            cd testvectors-runner
                                            ./tvrunner.sh --target ${tv_config_dir}/$SWITCH_NAME/target.pb.txt --portmap ${tv_config_dir}/$SWITCH_NAME/loopback-portmap.pb.txt --template-config ${tv_config_dir}/$SWITCH_NAME/template_config.json --tv-dir ${WORKSPACE}/testvectors/tofino --dp-mode loopback --tv-name PipelineConfig
                                        """
                                    }
                                }
                                sh returnStdout: false, label: "Setup loopback mode" , script: """
                                    cd testvectors-runner
                                    ./tvrunner.sh --target ${tv_config_dir}/$SWITCH_NAME/target.pb.txt --portmap ${tv_config_dir}/$SWITCH_NAME/loopback-portmap.pb.txt --template-config ${tv_config_dir}/$SWITCH_NAME/template_config.json --tv-dir ${WORKSPACE}/testvectors/templates/setup --dp-mode loopback --tv-name Set_Loopback_Mode || true
                                    ./tvrunner.sh --target ${tv_config_dir}/$SWITCH_NAME/target.pb.txt --portmap ${tv_config_dir}/$SWITCH_NAME/loopback-portmap.pb.txt --template-config ${tv_config_dir}/$SWITCH_NAME/template_config.json --tv-dir ${WORKSPACE}/testvectors/templates/setup --dp-mode loopback --tv-name Get_Loopback_Mode
                                    ./tvrunner.sh --target ${tv_config_dir}/$SWITCH_NAME/target.pb.txt --portmap ${tv_config_dir}/$SWITCH_NAME/loopback-portmap.pb.txt --template-config ${tv_config_dir}/$SWITCH_NAME/template_config.json --tv-dir ${WORKSPACE}/testvectors/templates/setup --dp-mode loopback --tv-name InsertSendToCPU
                                """
                            }
                            stage('Run Test Vectors') {
                                sh returnStdout: false, label: "Run Test Vectors" , script: """
                                    cd testvectors-runner
                                    ./tvrunner.sh --target ${tv_config_dir}/$SWITCH_NAME/target.pb.txt --portmap ${tv_config_dir}/$SWITCH_NAME/loopback-portmap.pb.txt --template-config ${tv_config_dir}/$SWITCH_NAME/template_config.json --tv-dir ${WORKSPACE}/testvectors/templates/gnmi --dp-mode loopback
                                    ./tvrunner.sh --target ${tv_config_dir}/$SWITCH_NAME/target.pb.txt --portmap ${tv_config_dir}/$SWITCH_NAME/loopback-portmap.pb.txt --template-config ${tv_config_dir}/$SWITCH_NAME/template_config.json --tv-dir ${WORKSPACE}/testvectors/templates/e2e --dp-mode loopback
                                    ./tvrunner.sh --target ${tv_config_dir}/$SWITCH_NAME/target.pb.txt --portmap ${tv_config_dir}/$SWITCH_NAME/loopback-portmap.pb.txt --template-config ${tv_config_dir}/$SWITCH_NAME/template_config.json --tv-dir ${WORKSPACE}/testvectors/templates/p4runtime --dp-mode loopback
                                """
                            }
                            stage("Cleanup") {
                                sh returnStdout: false, label: "Clean up" , script: """
                                    cd testvectors-runner
                                    ./tvrunner.sh --target ${tv_config_dir}/$SWITCH_NAME/target.pb.txt --portmap ${tv_config_dir}/$SWITCH_NAME/loopback-portmap.pb.txt --template-config ${tv_config_dir}/$SWITCH_NAME/template_config.json --tv-dir ${WORKSPACE}/testvectors/templates/setup --dp-mode loopback --tv-name DeleteSendToCPU
                                    sshpass -p $SWITCH_CREDS_PSW ssh $SWITCH_CREDS_USR@$SWITCH_IP "${RESOURCE_DIR}/stop-stratum.sh"                                    
                                """
                            }
                        }
                    }
                }
            }
        }
    }
}