def test_config = null

pipeline {
    agent {
        label "${BUILD_NODE}"
    }
    options {
        timeout(time: 120, unit: 'MINUTES')
    }
    stages {
        stage("Start Testing") {
            environment {
                SWITCH_CREDS = credentials("${SWITCH_NAME}-credentials")
                DOCKER_CREDS = credentials("abhilash_docker_access")
                SWITCH_IP = '' 
		        SWITCH_PORT = 9339
                CONFIG_DIR = '/tmp/stratum_configs'
                RESOURCE_DIR = '/tmp/barefoot'
                TV_RUNNER_IMAGE = 'stratumproject/tvrunner:fabric-tna-binary'
				PROFILE = 'fabric'
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
					node("${BUILD_NODE}") {
						def WORKSPACE = pwd()
						stage('Get fabric-tna') {
							step([$class: 'WsCleanup'])
							git branch: 'main', credentialsId: 'abhilash_github', url: 'https://github.com/stratum/fabric-tna.git'
							sh returnStdout: false, label: "Build fabric-tna", script: """
								docker login -u ${DOCKER_CREDS_USR} -p ${DOCKER_CREDS_PSW}
								make ${PROFILE}
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
							}
						}
					}
                }
            }
        }
    }
}
