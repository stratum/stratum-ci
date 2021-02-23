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
                SWITCH_CREDS = credentials("${SWITCH_NAME}-credentials")
		        DOCKER_CREDS = credentials("abhilash_docker_access")
                SWITCH_IP = '' 
		        SWITCH_PORT = 28000
                CONFIG_DIR = '/tmp/stratum_configs'
                RESOURCE_DIR = '/tmp/barefoot'
                TV_RUNNER_IMAGE = 'stratumproject/tvrunner:fabric-tna-binary'
                test_config = ''
                converted_tests = ''
                test_list = ''
                tv_dir = ''
                stratum_configs_dir = ''
                stratum_resources_dir = ''
                ptf_configs_dir = ''
                ptf_tv_resources_dir = ''
            }
            steps {
                script {
                    lock("${SWITCH_NAME}") {
                        node("${BUILD_NODE}") {
                            def WORKSPACE = pwd()
                            stage('Get fabric-tna') {
                                step([$class: 'WsCleanup'])
                                git branch: 'main', credentialsId: 'abhilash_github', url: 'https://github.com/stratum/fabric-tna.git'
                                sh returnStdout: false, label: "Build fabric-tna", script: """
                                    docker login -u ${DOCKER_CREDS_USR} -p ${DOCKER_CREDS_PSW}
                                    make ${PROFILE} SDE_DOCKER_IMG=${SDE_DOCKER_IMAGE}:${SDE_DOCKER_IMAGE_TAG}
                                """
                            }
                            stage("Get CI Configuration"){
                                script {
                                    try {
                                        sh returnStdout: false, label: "Get Stratum CI repo" , script: """
                                            git clone https://github.com/stratum/stratum-ci.git 
                                        """
                                        test_config = readYaml file: "${WORKSPACE}/stratum-ci/resources/test-config.yaml"
                                        converted_tests = readYaml file: "${WORKSPACE}/stratum-ci/ptf_tv_resources/converted-tests.yaml"
                                        test_list = converted_tests."${PROFILE}"
                                        tv_dir = "${WORKSPACE}/ptf/tests/ptf/testvectors"
                                        SWITCH_IP = """${test_config.switches["${SWITCH_NAME}"].ip}"""
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
                            stage("Generate TestVectors for ${PROFILE} profile") {
                                sh returnStdout: false, label: "Generate TestVectors from fabric-tna ptf Tests", script: """
                                    cp ${ptf_configs_dir}/${SWITCH_NAME}/port_map.json ${WORKSPACE}/ptf/tests/ptf
                                    cd ${WORKSPACE}/ptf
                                    SDE_VERSION=${SDE_VERSION} run/tv/run ${PROFILE} PORTMAP=port_map.json GRPCADDR=${SWITCH_IP}:${SWITCH_PORT} CPUPORT=${CPU_PORT}
                                """
                            }
                            stage("Start Stratum on ${SWITCH_NAME}") {
                                sh returnStdout: false, label: "Copy Config Files", script: """
                                    ssh-keyscan ${SWITCH_IP} >> ~/.ssh/known_hosts
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
                                    sshpass -p $SWITCH_CREDS_PSW ssh $SWITCH_CREDS_USR@$SWITCH_IP "tmux send-keys -t CI.0 ENTER 'docker pull ${REGISTRY_URL}/${DOCKER_IMAGE}:${DOCKER_IMAGE_TAG}' ENTER"
                                    sshpass -p $SWITCH_CREDS_PSW ssh $SWITCH_CREDS_USR@$SWITCH_IP "tmux send-keys -t CI.0 ENTER 'cd ${RESOURCE_DIR}' ENTER"
                                    sshpass -p $SWITCH_CREDS_PSW ssh $SWITCH_CREDS_USR@$SWITCH_IP "tmux send-keys -t CI.0 ENTER 'DOCKER_IMAGE=${REGISTRY_URL}/${DOCKER_IMAGE} DOCKER_IMAGE_TAG=${DOCKER_IMAGE_TAG} CHASSIS_CONFIG=${CONFIG_DIR}/${SWITCH_NAME}/chassis_config.pb.txt ./restart-stratum.sh --bf-sim --bf-switchd-background=false' ENTER"
                                    sleep 60
                                """
                            }
                            stage("Get Test Vectors Runner") {
                                sh returnStdout: false, label: "Get Test Vectors Runner" , script: """
                                    git clone https://github.com/abhilashendurthi/testvectors-runner.git -b support-fabric-tna-results
                                    cd testvectors-runner
                                    docker build -t ${TV_RUNNER_IMAGE} -f build/test/Dockerfile .
                                    sed -i 's/ -ti//g' tvrunner.sh
                                """
                            }
                            stage("Run Test Vectors") {
                                script {
                                    try {
                                        sh "mkdir -p ${WORKSPACE}/testvectors-runner/results"
                                        for (test_name in test_list.toSet()) {
                                	    sh returnStdout: false, label: "Push pipeline config" , script: """
                                    		cd testvectors-runner
                                    		IMAGE_NAME=${TV_RUNNER_IMAGE} ./tvrunner.sh --target ${tv_dir}/target.pb.txt --portmap ${tv_dir}/portmap.pb.txt --tv-dir ${tv_dir} --dp-mode loopback --tv-name PipelineConfig
                                    		IMAGE_NAME=${TV_RUNNER_IMAGE} ./tvrunner.sh --target ${tv_dir}/target.pb.txt --portmap ${tv_dir}/portmap.pb.txt --template-config ${ptf_configs_dir}/${SWITCH_NAME}/tv-template.json --dp-mode loopback --tv-dir ${ptf_tv_resources_dir} --tv-name Set_Loopback_Mode
                                	    """
                                            sh returnStdout: false, label:"Run ${test_name}", script: """
                                                IMAGE_NAME=${TV_RUNNER_IMAGE} ${WORKSPACE}/testvectors-runner/tvrunner.sh --dp-mode loopback --match-type in --target ${tv_dir}/target.pb.txt --portmap ${tv_dir}/portmap.pb.txt --tv-dir ${tv_dir}/${test_name}/setup
                                                IMAGE_NAME=${TV_RUNNER_IMAGE} ${WORKSPACE}/testvectors-runner/tvrunner.sh --dp-mode loopback --match-type in --target ${tv_dir}/target.pb.txt --portmap ${tv_dir}/portmap.pb.txt --tv-dir ${tv_dir}/${test_name} --tv-name ${test_name}.* --result-dir ${WORKSPACE}/testvectors-runner/results --result-file ${test_name}
                                                IMAGE_NAME=${TV_RUNNER_IMAGE} ${WORKSPACE}/testvectors-runner/tvrunner.sh --dp-mode loopback --match-type in --target ${tv_dir}/target.pb.txt --portmap ${tv_dir}/portmap.pb.txt --tv-dir ${tv_dir}/${test_name}/teardown
                                            """
                                        }
                                        currentBuild.result = 'SUCCESS'
                                    } catch(err) {
					                    throw err
                                        currentBuild.result = 'FAILURE'
                                    } finally {
                                        script {
                                            sh label: "Generate Results", script: """
                                                [ -d "omec-project-ci" ] || git clone https://github.com/omec-project/omec-project-ci
                                                ${WORKSPACE}/stratum-ci/resources/process-csv.sh ${WORKSPACE}/testvectors-runner/results
                                            """
                                            // Get csv files
                                            csv_list = sh returnStdout: true, script: """
                                                cd ${WORKSPACE}/testvectors-runner/results
                                                ls fabric_tna_hw_results*.csv
                                            """
          
                                            csv_list = csv_list.trim()
                                            for( String csv_name : csv_list.split() ) {
                                                sh label: "Dummy", script: """
                                                    echo "place holder for rscript"
                                                """
                                            }
                                            archiveArtifacts artifacts: "testvectors-runner/results/tv_result*.csv", allowEmptyArchive: true
                                        }
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
