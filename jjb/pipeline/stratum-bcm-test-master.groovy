def test_config = null

pipeline {
    agent {
        label "${BUILD_NODE}"
    }
    options {
        timeout(time: 60, unit: 'MINUTES')
        withAWS(credentials:"${AWS_S3_CREDENTIAL}")
    }
    stages {
        stage("Start Testing") {
            environment {
                REGISTRY_CREDS = credentials("${REGISTRY_CREDENTIAL}")
                SWITCH_CREDS = credentials("${SWITCH_NAME}-credentials")
                SWITCH_IP = ''
                CONFIG_DIR = '/tmp/stratum_configs'
                RESOURCE_DIR = '/tmp/bcm'
		        tv_config_dir = ''
		        stratum_configs_dir = ''
		        stratum_resources_dir = ''
		        install_debian_script = ''
            }
            steps {
                script {
                    lock("${SWITCH_NAME}") {
                        node("${BUILD_NODE}") {
                            stage('Preparations') {
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
                                        stratum_resources_dir = "${WORKSPACE}/stratum-ci/resources/bcm"
                                        install_debian_script = "install_bcm_debian_package.sh"
					                    SWITCH_IP = """${test_config.switches["${SWITCH_NAME}"].ip}"""
                                    } catch (err) {
                                        echo "Error reading ${WORKSPACE}/stratum-ci/resources/test-config.yaml"
                                        throw err
                                    }
                                }
                            }
                            stage("Start Stratum on ${SWITCH_NAME}") {
                                    sh returnStdout: false, label: "Copy Config Files", script: """
                                        ssh-keyscan ${SWITCH_IP} >> ~/.ssh/known_hosts
                                        sshpass -p $SWITCH_CREDS_PSW ssh $SWITCH_CREDS_USR@$SWITCH_IP "mkdir -p $CONFIG_DIR"
                                        sshpass -p $SWITCH_CREDS_PSW scp ${stratum_configs_dir}/dummy_serdes_db.pb.txt $SWITCH_CREDS_USR@$SWITCH_IP:${CONFIG_DIR}/dummy_serdes_db.pb.txt
                                        sshpass -p $SWITCH_CREDS_PSW scp ${stratum_configs_dir}/bcm_hardware_specs.pb.txt $SWITCH_CREDS_USR@$SWITCH_IP:${CONFIG_DIR}/bcm_hardware_specs.pb.txt
                                        sshpass -p $SWITCH_CREDS_PSW scp -r ${stratum_configs_dir}/${SWITCH_NAME} $SWITCH_CREDS_USR@$SWITCH_IP:${CONFIG_DIR}
                                    """
                                    sh returnStdout: false, label: "Copy Stratum Scripts", script: """
                                        sshpass -p $SWITCH_CREDS_PSW ssh $SWITCH_CREDS_USR@$SWITCH_IP "mkdir -p $CONFIG_DIR"
                                        sshpass -p $SWITCH_CREDS_PSW scp -r ${stratum_resources_dir} $SWITCH_CREDS_USR@$SWITCH_IP:${RESOURCE_DIR}
                                    """
                                    if (params.DEBIAN_PACKAGE_NAME != '' && params.DEBIAN_PACKAGE_PATH != '') {
                                        s3Download(file:"${DEBIAN_PACKAGE_NAME}", bucket:'stratum-artifacts', path:"${DEBIAN_PACKAGE_NAME}", force:true)
                                        sh returnStdout: false, label: "Install Stratum Debian Package", script: """
                                            sshpass -p $SWITCH_CREDS_PSW ssh $SWITCH_CREDS_USR@$SWITCH_IP "pkill stratum || true"
                                            sshpass -p $SWITCH_CREDS_PSW scp ${WORKSPACE}/${DEBIAN_PACKAGE_NAME} $SWITCH_CREDS_USR@$SWITCH_IP:/tmp               
                                            sshpass -p $SWITCH_CREDS_PSW ssh $SWITCH_CREDS_USR@$SWITCH_IP "tmux kill-session -t CI || true"
                                            sshpass -p $SWITCH_CREDS_PSW ssh $SWITCH_CREDS_USR@$SWITCH_IP "tmux new -d -s CI || true"
                                            sshpass -p $SWITCH_CREDS_PSW ssh $SWITCH_CREDS_USR@$SWITCH_IP "tmux send-keys -t CI.0 ENTER 'DEBIAN_PACKAGE_NAME=${DEBIAN_PACKAGE_NAME} ${RESOURCE_DIR}/install_bcm_debian_package.sh; tmux wait-for -S install' C-m\\; wait-for install"
                                        """
                                        sh returnStdout: false, label: "Starting Stratum with Debian Package", script: """
                                            sshpass -p $SWITCH_CREDS_PSW ssh $SWITCH_CREDS_USR@$SWITCH_IP "tmux send-keys -t CI.0 ENTER 'CONFIG_DIR=${CONFIG_DIR} /tmp/start-stratum.sh' C-m"
                                            sleep 120
                                        """
                                    } else if (params.DOCKER_IMAGE != '' && params.DOCKER_IMAGE_TAG != '') {
                                        sh returnStdout: false, label: "Starting Stratum with image ${DOCKER_IMAGE}:${DOCKER_IMAGE_TAG}", script: """
                                            sshpass -p $SWITCH_CREDS_PSW ssh $SWITCH_CREDS_USR@$SWITCH_IP "pkill stratum || true"
                                            sshpass -p $SWITCH_CREDS_PSW ssh $SWITCH_CREDS_USR@$SWITCH_IP "tmux kill-session -t CI || true"
                                            sshpass -p $SWITCH_CREDS_PSW ssh $SWITCH_CREDS_USR@$SWITCH_IP "tmux new -d -s CI || true"
                                            sshpass -p $SWITCH_CREDS_PSW ssh $SWITCH_CREDS_USR@$SWITCH_IP "tmux send-keys -t CI.0 ENTER 'docker login ${REGISTRY_URL} -u ${REGISTRY_CREDS_USR} -p ${REGISTRY_CREDS_PSW}' ENTER"
                                            sshpass -p $SWITCH_CREDS_PSW ssh $SWITCH_CREDS_USR@$SWITCH_IP "tmux send-keys -t CI.0 ENTER 'docker pull ${REGISTRY_URL}/docker.io/stratumproject/${DOCKER_IMAGE}:${DOCKER_IMAGE_TAG}' ENTER"
                                            sshpass -p $SWITCH_CREDS_PSW ssh $SWITCH_CREDS_USR@$SWITCH_IP "tmux send-keys -t CI.0 ENTER 'cd ${RESOURCE_DIR}' ENTER"
                                            sshpass -p $SWITCH_CREDS_PSW ssh $SWITCH_CREDS_USR@$SWITCH_IP "tmux send-keys -t CI.0 ENTER 'DOCKER_IMAGE=${REGISTRY_URL}/docker.io/stratumproject/${DOCKER_IMAGE} DOCKER_IMAGE_TAG=${DOCKER_IMAGE_TAG} CHASSIS_CONFIG=${CONFIG_DIR}/${SWITCH_NAME}/chassis_config.pb.txt ./restart-stratum.sh' ENTER"
                                            sleep 120
                                        """
                                    } else {
                                        script {
                                            error "Invalid Parameters"
                                        }
                                    }
                            }
                            stage('Get Test Vectors') {
                                sh returnStdout: false, label: "Get Test Vectors" , script: """
                                    git clone https://github.com/stratum/testvectors-runner.git ${WORKSPACE}/testvectors-runner
                                    git clone https://github.com/stratum/testvectors.git ${WORKSPACE}/testvectors
                                    cd ${WORKSPACE}/testvectors-runner
                                    sed -i 's/ -ti//g' tvrunner.sh
                                """
                            }
                            stage('Setup Loopback Mode') {
                                sh returnStdout: false, label: "Setup loopback mode" , script: """
                                    cd ${WORKSPACE}/testvectors-runner
                                    ./tvrunner.sh --target ${tv_config_dir}/$SWITCH_NAME/target.pb.txt --portmap ${tv_config_dir}/$SWITCH_NAME/loopback-portmap.pb.txt --tv-dir ${WORKSPACE}/testvectors/bcm --dp-mode loopback --tv-name PipelineConfig
                                    ./tvrunner.sh --target ${tv_config_dir}/$SWITCH_NAME/target.pb.txt --portmap ${tv_config_dir}/$SWITCH_NAME/loopback-portmap.pb.txt --template-config ${tv_config_dir}/$SWITCH_NAME/template_config.json --tv-dir ${WORKSPACE}/testvectors/templates/setup --dp-mode loopback --tv-name Set_Loopback_Mode || true
                                    ./tvrunner.sh --target ${tv_config_dir}/$SWITCH_NAME/target.pb.txt --portmap ${tv_config_dir}/$SWITCH_NAME/loopback-portmap.pb.txt --template-config ${tv_config_dir}/$SWITCH_NAME/template_config.json --tv-dir ${WORKSPACE}/testvectors/templates/setup --dp-mode loopback --tv-name Get_Loopback_Mode
                                    ./tvrunner.sh --target ${tv_config_dir}/$SWITCH_NAME/target.pb.txt --portmap ${tv_config_dir}/$SWITCH_NAME/loopback-portmap.pb.txt --template-config ${tv_config_dir}/$SWITCH_NAME/template_config.json --tv-dir ${WORKSPACE}/testvectors/templates/setup --dp-mode loopback --tv-name InsertSendToCPU
                                """
                            }
                            stage('Run Test Vectors') {
                                sh returnStdout: false, label: "Run Test Vectors" , script: """
                                    cd ${WORKSPACE}/testvectors-runner
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
                                    cd ${WORKSPACE}/testvectors-runner
                                    sshpass -p $SWITCH_CREDS_PSW ssh $SWITCH_CREDS_USR@$SWITCH_IP "rm -rf $CONFIG_DIR || true"
                                    ./tvrunner.sh --target ${tv_config_dir}/$SWITCH_NAME/target.pb.txt --portmap ${tv_config_dir}/$SWITCH_NAME/loopback-portmap.pb.txt --template-config ${tv_config_dir}/$SWITCH_NAME/template_config.json --tv-dir ${WORKSPACE}/testvectors/templates/setup --dp-mode loopback --tv-name DeleteSendToCPU
                                    sshpass -p $SWITCH_CREDS_PSW ssh $SWITCH_CREDS_USR@$SWITCH_IP "pkill stratum || true"
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
