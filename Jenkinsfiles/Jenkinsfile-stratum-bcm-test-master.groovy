/*
Build Parameters
TEST_DRIVER: p4-dev
SWITCH_NAME: x86-64-inventec-d7032q28b-r0
DOCKER_IMAGE: 10.128.13.253:5000/stratum-bcm
DOCKER_IMAGE_TAG: 3.16.56
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
            }
            steps {
                script {
                    lock("${SWITCH_NAME}") {
                        node("${TEST_DRIVER}") {
                            def WORKSPACE = pwd()
                            stage("Start Stratum on ${SWITCH_NAME}") {
                                sh returnStdout: false, label: "Copy Config Files", script: """
                                    sshpass -p $SWITCH_CREDS_PSW ssh $SWITCH_CREDS_USR@$SWITCH_IP "mkdir -p $CONFIG_DIR"
                                    sshpass -p $SWITCH_CREDS_PSW scp ${stratum_configs_dir}/dummy_serdes_db.pb.txt $SWITCH_CREDS_USR@$SWITCH_IP:${CONFIG_DIR}/dummy_serdes_db.pb.txt
                                    sshpass -p $SWITCH_CREDS_PSW scp ${stratum_configs_dir}/bcm_hardware_specs.pb.txt $SWITCH_CREDS_USR@$SWITCH_IP:${CONFIG_DIR}/bcm_hardware_specs.pb.txt
                                    sshpass -p $SWITCH_CREDS_PSW scp -r ${stratum_configs_dir}/${SWITCH_NAME} $SWITCH_CREDS_USR@$SWITCH_IP:${CONFIG_DIR}/${SWITCH_NAME}
                                """
                                if (params.DEBIAN_PACKAGE_NAME != '' && params.DEBIAN_PACKAGE_PATH != '') {
                                    sh returnStdout: false, label: "Install Stratum Debian Package", script: """
                                        sshpass -p $SWITCH_CREDS_PSW ssh $SWITCH_CREDS_USR@$SWITCH_IP "pkill stratum || true"
                                        sshpass -p $SWITCH_CREDS_PSW scp ${DEBIAN_PACKAGE_PATH}/${DEBIAN_PACKAGE_NAME} $SWITCH_CREDS_USR@$SWITCH_IP:/tmp
                                        
                                        sshpass -p $SWITCH_CREDS_PSW ssh $SWITCH_CREDS_USR@$SWITCH_IP "tmux kill-session -t CI || true"
                                        sshpass -p $SWITCH_CREDS_PSW ssh $SWITCH_CREDS_USR@$SWITCH_IP "tmux new -d -s CI || true"
                                        sshpass -p $SWITCH_CREDS_PSW ssh $SWITCH_CREDS_USR@$SWITCH_IP "tmux send-keys -t CI.0 ENTER 'DEBIAN_PACKAGE_NAME=${DEBIAN_PACKAGE_NAME} /tmp/install_bcm_debian_package.sh; tmux wait-for -S install' C-m\\; wait-for install"
                                    """
                                    sh returnStdout: false, label: "Starting Stratum with Debian Package", script: """
                                        sshpass -p $SWITCH_CREDS_PSW ssh $SWITCH_CREDS_USR@$SWITCH_IP "tmux send-keys -t CI.0 ENTER 'CONFIG_DIR=${CONFIG_DIR} /tmp/start-stratum.sh' C-m"
                                        sleep 30
                                    """
                                } else if (params.DOCKER_IMAGE != '' && params.DOCKER_IMAGE_TAG != '') {
                                sh returnStdout: false, label: "Starting Stratum with image ${DOCKER_IMAGE}:${DOCKER_IMAGE_TAG}", script: """
                                        sshpass -p $SWITCH_CREDS_PSW ssh $SWITCH_CREDS_USR@$SWITCH_IP "pkill stratum || true"
                                        sshpass -p $SWITCH_CREDS_PSW ssh $SWITCH_CREDS_USR@$SWITCH_IP "docker stop stratum-bcm || true"
                                        sshpass -p $SWITCH_CREDS_PSW ssh $SWITCH_CREDS_USR@$SWITCH_IP "docker rm stratum-bcm || true"
                                        sshpass -p $SWITCH_CREDS_PSW ssh $SWITCH_CREDS_USR@$SWITCH_IP "tmux kill-session -t CI || true"
                                        sshpass -p $SWITCH_CREDS_PSW ssh $SWITCH_CREDS_USR@$SWITCH_IP "tmux new -d -s CI || true"
                                        sshpass -p $SWITCH_CREDS_PSW ssh $SWITCH_CREDS_USR@$SWITCH_IP "tmux send-keys -t CI.0 ENTER 'docker pull ${DOCKER_IMAGE}:${DOCKER_IMAGE_TAG}' ENTER"
                                        sshpass -p $SWITCH_CREDS_PSW ssh $SWITCH_CREDS_USR@$SWITCH_IP "tmux send-keys -t CI.0 ENTER 'DOCKER_IMAGE=${DOCKER_IMAGE} DOCKER_IMAGE_TAG=${DOCKER_IMAGE_TAG} CONFIG_DIR=${CONFIG_DIR} ./start-stratum-container.sh' ENTER"
                                        sleep 30
                                    """
                                } else {
                                    script {
                                        error "Invalid Parameters"
                                    }
                                }
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
                                sh returnStdout: false, label: "Setup loopback mode" , script: """
                                    cd testvectors-runner
                                    ./tvrunner.sh --target ${tv_config_dir}/$SWITCH_NAME/target.pb.txt --portmap ${tv_config_dir}/$SWITCH_NAME/loopback-portmap.pb.txt --tv-dir ${WORKSPACE}/testvectors/bcm --tv-name PipelineConfig
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
                                    ./tvrunner.sh --target ${tv_config_dir}/$SWITCH_NAME/target.pb.txt --portmap ${tv_config_dir}/$SWITCH_NAME/loopback-portmap.pb.txt --template-config ${tv_config_dir}/$SWITCH_NAME/template_config.json --tv-dir ${WORKSPACE}/testvectors/templates/p4runtime --dp-mode loopback --tv-name L3ForwardTest
                                    ./tvrunner.sh --target ${tv_config_dir}/$SWITCH_NAME/target.pb.txt --portmap ${tv_config_dir}/$SWITCH_NAME/loopback-portmap.pb.txt --template-config ${tv_config_dir}/$SWITCH_NAME/template_config.json --tv-dir ${WORKSPACE}/testvectors/templates/p4runtime --dp-mode loopback --tv-name PktIoOutDirectToDataPlaneTest
                                    ./tvrunner.sh --target ${tv_config_dir}/$SWITCH_NAME/target.pb.txt --portmap ${tv_config_dir}/$SWITCH_NAME/loopback-portmap.pb.txt --template-config ${tv_config_dir}/$SWITCH_NAME/template_config.json --tv-dir ${WORKSPACE}/testvectors/templates/p4runtime --dp-mode loopback --tv-name PktIoOutToIngressPipelineAclRedirectToPortTest
                                    ./tvrunner.sh --target ${tv_config_dir}/$SWITCH_NAME/target.pb.txt --portmap ${tv_config_dir}/$SWITCH_NAME/loopback-portmap.pb.txt --template-config ${tv_config_dir}/$SWITCH_NAME/template_config.json --tv-dir ${WORKSPACE}/testvectors/templates/p4runtime --dp-mode loopback --tv-name RedirectDataplaneToDataplaneTest
                                """
                            }
                            stage("Cleanup") {
                                sh returnStdout: false, label: "Clean up" , script: """
                                    cd testvectors-runner
                                    sshpass -p $SWITCH_CREDS_PSW ssh $SWITCH_CREDS_USR@$SWITCH_IP "rm -rf $CONFIG_DIR || true"
                                    ./tvrunner.sh --target ${tv_config_dir}/$SWITCH_NAME/target.pb.txt --portmap ${tv_config_dir}/$SWITCH_NAME/loopback-portmap.pb.txt --template-config ${tv_config_dir}/$SWITCH_NAME/template_config.json --tv-dir ${WORKSPACE}/testvectors/templates/setup --dp-mode loopback --tv-name DeleteSendToCPU
                                """
                            }
                        }
                    }
                }
            }
        }
    }
}