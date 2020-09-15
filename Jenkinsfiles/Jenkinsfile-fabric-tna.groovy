/*
Build Parameters
TEST_DRIVER: p4-dev
SWITCH_NAME: x86-64-stordis-bf2556x-1t-r0
DOCKER_IMAGE: 10.128.13.253:5000/stratum-bf
DOCKER_IMAGE_TAG: bf-sde-9.2.0-linux-4.14.49-OpenNetworkLinux
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
        stage('Get fabric-tna') {
            steps {
                step([$class: 'WsCleanup'])
                git branch: 'master', credentialsId: 'abhilash_github', url: 'https://github.com/stratum/fabric-tna.git'
                sh returnStdout: false, label: "Build fabric-tna", script: """
                    make fabric
                """
            }
        }
        stage("Get CI Configuration"){
            steps { 
                script {
                    try {
                        sh returnStdout: false, label: "Get Stratum CI repo" , script: """
                            git clone https://github.com/stratum/stratum-ci.git -b test-fabric-tna
                        """
                        test_config = readYaml file: "${WORKSPACE}/stratum-ci/resources/test-config.yaml"
                        test_list = readYaml file: "${WORKSPACE}/stratum-ci/ptf_tv_resources/converted-tests.yaml"
                        tv_dir = "${WORKSPACE}/ptf/tests/ptf/testvectors"
                        stratum_configs_dir = "${WORKSPACE}/stratum-ci/stratum_configs"
                        stratum_resources_dir = "${WORKSPACE}/stratum-ci/resources/barefoot"
                        ptf_configs_dir = "${WORKSPACE}/stratum-ci/ptf_configs"
                        ptf_tv_resources_dir = "${WORKSPACE}/stratum-ci/ptf_tv_resources"
                    } catch (err) {
                        echo "Error reading ${WORKSPACE}/stratum-ci/resources/test-config.yaml"
                        throw err
                    }
                }
            }
        }
        stage('Generate TestVectors') {
            environment {
                SWITCH_IP = """${test_config.switches["${SWITCH_NAME}"].ip}"""
				SWITCH_PORT = 28000
            }
            steps {
                sh returnStdout: false, label: "Generate TestVectors from fabric-tna ptf Tests", script: """
                    cp /var/jenkins/ptf-tv/run ${WORKSPACE}/ptf/run/tv
                    cp ${ptf_configs_dir}/${SWITCH_NAME}/port_map.json ${WORKSPACE}/ptf/tests/ptf
                    cd ${WORKSPACE}/ptf
                    run/tv/run fabric PORTMAP=port_map.json GRPCADDR=${SWITCH_IP}:${SWITCH_PORT}
                """
            }
        }
        stage("Start Testing") {
            environment {
                SWITCH_CREDS = credentials("${SWITCH_NAME}-credentials")
                SWITCH_IP = """${test_config.switches["${SWITCH_NAME}"].ip}"""
                CONFIG_DIR = '/tmp/stratum_configs'
                RESOURCE_DIR = '/tmp/barefoot'
                TV_RUNNER_IMAGE = 'stratumproject/tvrunner:fabric-tna-binary'
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
                                    sshpass -p $SWITCH_CREDS_PSW ssh $SWITCH_CREDS_USR@$SWITCH_IP "mkdir -p $RESOURCE_DIR"
                                    sshpass -p $SWITCH_CREDS_PSW scp -r ${stratum_resources_dir}/ $SWITCH_CREDS_USR@$SWITCH_IP:/tmp
                                """
                                sh returnStdout: false, label: "Starting Stratum with Image ${DOCKER_IMAGE}:${DOCKER_IMAGE_TAG}", script: """
                                    sshpass -p $SWITCH_CREDS_PSW ssh $SWITCH_CREDS_USR@$SWITCH_IP "tmux kill-session -t CI || true"
                                    sshpass -p $SWITCH_CREDS_PSW ssh $SWITCH_CREDS_USR@$SWITCH_IP "tmux new -d -s CI || true"
                                    sshpass -p $SWITCH_CREDS_PSW ssh $SWITCH_CREDS_USR@$SWITCH_IP "tmux send-keys -t CI.0 ENTER 'docker pull ${DOCKER_IMAGE}:${DOCKER_IMAGE_TAG}' ENTER"
                                    sshpass -p $SWITCH_CREDS_PSW ssh $SWITCH_CREDS_USR@$SWITCH_IP "tmux send-keys -t CI.0 ENTER 'cd ${RESOURCE_DIR}' ENTER"
                                    sshpass -p $SWITCH_CREDS_PSW ssh $SWITCH_CREDS_USR@$SWITCH_IP "tmux send-keys -t CI.0 ENTER 'DOCKER_IMAGE=${DOCKER_IMAGE} DOCKER_IMAGE_TAG=${DOCKER_IMAGE_TAG} CHASSIS_CONFIG=${CONFIG_DIR}/${SWITCH_NAME}/chassis_config.pb.txt ./restart-stratum.sh --bf-sim --bf-switchd-background=false' ENTER"
                                    sleep 60
                                """
                            }
                            stage('Get Test Vectors Runner') {
                                step([$class: 'WsCleanup'])
                                sh returnStdout: false, label: "Get Test Vectors Runner" , script: """
                                    git clone https://github.com/stratum/testvectors-runner.git -b support-fabric-tna
                                    cd testvectors-runner
                                    docker build -t ${TV_RUNNER_IMAGE} -f build/test/Dockerfile .
                                    sed -i 's/ -ti//g' tvrunner.sh
                                """
                            }
                            stage('Setup Loopback Mode') {
                                sh returnStdout: false, label: "Push pipeline config" , script: """
                                    cd testvectors-runner
                                    IMAGE_NAME=${TV_RUNNER_IMAGE} ./tvrunner.sh --target ${tv_dir}/target.pb.txt --portmap ${tv_dir}/portmap.pb.txt --tv-dir ${tv_dir} --dp-mode loopback --tv-name PipelineConfig
                                    IMAGE_NAME=${TV_RUNNER_IMAGE} ./tvrunner.sh --target ${tv_dir}/target.pb.txt --portmap ${tv_dir}/portmap.pb.txt --template-config ${ptf_configs_dir}/${SWITCH_NAME}/tv-template.json --dp-mode loopback --tv-dir ${ptf_tv_resources_dir} --tv-name Set_Loopback_Mode
                                """
                            }
                            stage('Run Test Vectors') {
                                script {
									sh "cd testvectors-runner"
									for (test_name in test_list.toSet()) {
										sh returnStdout: false, label:"Run ${test_name}", script: """
											IMAGE_NAME=${TV_RUNNER_IMAGE} ${WORKSPACE}/testvectors-runner/tvrunner.sh --dp-mode loopback --match-type in --target ${tv_dir}/target.pb.txt --portmap ${tv_dir}/portmap.pb.txt --tv-dir ${tv_dir}/${test_name}
										"""
									}
                                }
                            }
                            stage("Cleanup") {
                                sh returnStdout: false, label: "Clean up" , script: """
                                    cd testvectors-runner
                                    sshpass -p $SWITCH_CREDS_PSW ssh $SWITCH_CREDS_USR@$SWITCH_IP "${RESOURCE_DIR}/stop-stratum.sh"                                    
                                    sshpass -p $SWITCH_CREDS_PSW ssh $SWITCH_CREDS_USR@$SWITCH_IP "rm -rf ${CONFIG_DIR} || true"
                                    sshpass -p $SWITCH_CREDS_PSW ssh $SWITCH_CREDS_USR@$SWITCH_IP "rm -rf ${RESOURCE_DIR} || true"                                    
                                """
                            }
                        }
                    }
                }
            }
        }
	}
}