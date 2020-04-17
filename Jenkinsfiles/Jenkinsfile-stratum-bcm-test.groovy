/*
Build Parameters
TEST_DRIVER: p4-dev
DOCKER_IMAGE: 10.128.13.253:5000/stratum-bcm
DOCKER_IMAGE_TAG: 3.16.56
PUBLISH: False (Boolean)
*/

import org.jenkins.plugins.lockableresources.LockableResourcesManager as LRM
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
				sh returnStdout: false, label: "Start testing ${DOCKER_IMAGE}:${DOCKER_IMAGE_TAG}", script: ""
				step([$class: 'WsCleanup'])
				script {
					try {
						sh returnStdout: false, label: "Get Test Vectors" , script: """
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
		stage('Test') {
			matrix {
				axes {
					axis {
						name 'SWITCH_NAME'
						//values 'x86-64-dell-z9100-c2538-r0','x86-64-quanta-ix1-rangeley-r0'
						values 'x86-64-dell-z9100-c2538-r0', 'x86-64-accton-as7712-32x-r0'
					}
				}
				stages {
					stage("Start Testing") {
						when { expression { !LRM.get().fromName("${SWITCH_NAME}").isReserved() } }
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
											sh returnStdout: false, label: "Starting Stratum with image ${DOCKER_IMAGE}:${DOCKER_IMAGE_TAG}", script: """
												sshpass -p $SWITCH_CREDS_PSW ssh $SWITCH_CREDS_USR@$SWITCH_IP "mkdir -p $CONFIG_DIR"
												sshpass -p $SWITCH_CREDS_PSW scp ${stratum_configs_dir}/dummy_serdes_db.pb.txt $SWITCH_CREDS_USR@$SWITCH_IP:${CONFIG_DIR}/dummy_serdes_db.pb.txt
												sshpass -p $SWITCH_CREDS_PSW scp ${stratum_configs_dir}/bcm_hardware_specs.pb.txt $SWITCH_CREDS_USR@$SWITCH_IP:${CONFIG_DIR}/bcm_hardware_specs.pb.txt
												sshpass -p $SWITCH_CREDS_PSW scp -r ${stratum_configs_dir}/${SWITCH_NAME} $SWITCH_CREDS_USR@$SWITCH_IP:${CONFIG_DIR}/${SWITCH_NAME}
												sshpass -p $SWITCH_CREDS_PSW ssh $SWITCH_CREDS_USR@$SWITCH_IP "docker stop stratum-bcm || true"
												sshpass -p $SWITCH_CREDS_PSW ssh $SWITCH_CREDS_USR@$SWITCH_IP "docker rm stratum-bcm || true"
												sshpass -p $SWITCH_CREDS_PSW ssh $SWITCH_CREDS_USR@$SWITCH_IP "tmux kill-session -t CI || true"
												sshpass -p $SWITCH_CREDS_PSW ssh $SWITCH_CREDS_USR@$SWITCH_IP "tmux new -d -s CI || true"
												sshpass -p $SWITCH_CREDS_PSW ssh $SWITCH_CREDS_USR@$SWITCH_IP "tmux send-keys -t CI.0 ENTER 'DOCKER_IMAGE=${DOCKER_IMAGE} DOCKER_IMAGE_TAG=${DOCKER_IMAGE_TAG} CONFIG_DIR=${CONFIG_DIR} ./start-stratum-container.sh' ENTER"
												sleep 30
											"""
										}
										stage('Get Test Vectors') {
											step([$class: 'WsCleanup'])
											sh returnStdout: false, label: "Get Test Vectors" , script: """
												git clone https://github.com/yoooou/testvectors-runner.git -b parametrization
												git clone https://github.com/yoooou/testvectors.git -b parametrization
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
											if( params.PUBLISH == true ) {
												sh returnStdout: false, label: "Triggering publish job for ${DOCKER_IMAGE}:${DOCKER_IMAGE_TAG}", script: ""
												build job: "stratum-publish", parameters: [
													string(name: 'DOCKER_IMAGE', value: "${DOCKER_IMAGE}"),
													string(name: 'DOCKER_IMAGE_TAG', value: "${DOCKER_IMAGE_TAG}"),
												]
											}
										}
									}
								}
							}
						}
					}
				}
			}
		}
	}
}