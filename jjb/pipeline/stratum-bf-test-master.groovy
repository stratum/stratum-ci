def test_config = null

pipeline {
    agent {
        label "${BUILD_NODE}"
    }
    options {
        timeout(time: 60, unit: 'MINUTES')
    }
    stages {
        stage("Start Testing") {
            environment {
                REGISTRY_CREDS = credentials("${REGISTRY_CREDENTIAL}")
                SWITCH_CREDS = credentials("${SWITCH_NAME}-credentials")
                SWITCH_IP = ''
                CONFIG_DIR = '/tmp/stratum_configs'
                RESOURCE_DIR = '/tmp/barefoot'
                CPU_PORT = ''
            }
            steps {
                script {
                    lock("${SWITCH_NAME}") {
                        node("${BUILD_NODE}") {
                            def WORKSPACE = pwd()
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
                        	    		stratum_resources_dir = "${WORKSPACE}/stratum-ci/resources/barefoot"
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
                                    sshpass -p $SWITCH_CREDS_PSW ssh $SWITCH_CREDS_USR@$SWITCH_IP "rm -rf ${CONFIG_DIR} || true"
                                    sshpass -p $SWITCH_CREDS_PSW ssh $SWITCH_CREDS_USR@$SWITCH_IP "mkdir -p ${CONFIG_DIR}"
                                    sshpass -p $SWITCH_CREDS_PSW scp -r ${stratum_configs_dir}/${SWITCH_NAME} $SWITCH_CREDS_USR@$SWITCH_IP:${CONFIG_DIR}/${SWITCH_NAME}
                                """
                                sh returnStdout: false, label: "Copy Stratum Scripts", script: """
                                    sshpass -p $SWITCH_CREDS_PSW ssh $SWITCH_CREDS_USR@$SWITCH_IP "rm -rf ${RESOURCE_DIR} || true" 
                                    sshpass -p $SWITCH_CREDS_PSW ssh $SWITCH_CREDS_USR@$SWITCH_IP "mkdir -p ${RESOURCE_DIR}"
                                    sshpass -p $SWITCH_CREDS_PSW scp -r ${stratum_resources_dir} $SWITCH_CREDS_USR@$SWITCH_IP:${RESOURCE_DIR}
                                """
                                sh returnStdout: false, label: "Starting Stratum with image ${DOCKER_IMAGE}:${DOCKER_IMAGE_TAG}", script: """
                                    sshpass -p $SWITCH_CREDS_PSW ssh $SWITCH_CREDS_USR@$SWITCH_IP "tmux kill-session -t CI || true"
                                    sshpass -p $SWITCH_CREDS_PSW ssh $SWITCH_CREDS_USR@$SWITCH_IP "tmux new -d -s CI || true"
                                    sshpass -p $SWITCH_CREDS_PSW ssh $SWITCH_CREDS_USR@$SWITCH_IP "tmux send-keys -t CI.0 ENTER 'docker login ${REGISTRY_URL} -u ${REGISTRY_CREDS_USR} -p ${REGISTRY_CREDS_PSW}' ENTER"
                                    sshpass -p $SWITCH_CREDS_PSW ssh $SWITCH_CREDS_USR@$SWITCH_IP "tmux send-keys -t CI.0 ENTER 'docker pull ${REGISTRY_URL}/${DOCKER_IMAGE}:${DOCKER_IMAGE_TAG}' ENTER"
                                    sshpass -p $SWITCH_CREDS_PSW ssh $SWITCH_CREDS_USR@$SWITCH_IP "tmux send-keys -t CI.0 ENTER 'cd ${RESOURCE_DIR}' ENTER"
                                    sshpass -p $SWITCH_CREDS_PSW ssh $SWITCH_CREDS_USR@$SWITCH_IP "tmux send-keys -t CI.0 ENTER 'DOCKER_IMAGE=${REGISTRY_URL}/${DOCKER_IMAGE} DOCKER_IMAGE_TAG=${DOCKER_IMAGE_TAG} CHASSIS_CONFIG=${CONFIG_DIR}/${SWITCH_NAME}/chassis_config.pb.txt ./restart-stratum.sh --bf-sim' ENTER"
                                    sleep 180
                                """
                                script {
                                    ports =sh returnStdout: true, label: "Find number of ports", script: """
                                        sshpass -p $SWITCH_CREDS_PSW ssh $SWITCH_CREDS_USR@$SWITCH_IP "/lib/platform-config/current/onl/bin/onlps sfp inventory |wc -l"
                                    """
                                    if (ports.toInteger() == 66) {
                                        CPU_PORT = '320'
                                    } else {
                                        CPU_PORT = '192'
                                    }
                                }
                            }
                            stage('Run gNMI Tests') {sh returnStdout: false, label: "", script: ""
                                build job: "stratum-${TARGET}-gnmi-test", parameters: [
                                    string(name: 'SWITCH_NAME', value: "${SWITCH_NAME}"),
                                ]
                            }
                            stage('Run P4Runtime Tests') {sh returnStdout: false, label: "", script: ""
                                build job: "stratum-${TARGET}-p4rt-test", parameters: [
                                    string(name: 'SWITCH_NAME', value: "${SWITCH_NAME}"),
                                    string(name: 'CPU_PORT', value: "${CPU_PORT}"),
                                    string(name: 'SDE_VERSION', value: "${DOCKER_IMAGE_TAG}")
                                ]
                            }
                            stage("Cleanup") {
                                sh returnStdout: false, label: "Clean up" , script: """
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
