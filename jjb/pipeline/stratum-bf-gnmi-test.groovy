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
                SWITCH_IP = ''
            }
            steps {
                script {
					node("${BUILD_NODE}") {
						def WORKSPACE = pwd()
						stage('Preparations') {
							sh returnStdout: false, label: "Start testing on ${SWITCH_NAME}", script: ""
							step([$class: 'WsCleanup'])
							script {
								try {
									sh returnStdout: false, label: "Get Stratum CI repo" , script: """
										git clone https://github.com/stratum/stratum-ci.git
									"""
									test_config = readYaml file: "${WORKSPACE}/stratum-ci/resources/test-config.yaml"
									tv_config_dir = "${WORKSPACE}/stratum-ci/tv_configs"
									SWITCH_IP = """${test_config.switches["${SWITCH_NAME}"].ip}"""
								} catch (err) {
									echo "Error reading ${WORKSPACE}/stratum-ci/resources/test-config.yaml"
									throw err
								}
							}
						}
						stage('Get Test Vectors') {
							sh returnStdout: false, label: "Get Test Vectors" , script: """
								git clone https://github.com/stratum/testvectors-runner.git
								git clone https://github.com/stratum/testvectors.git
								cd testvectors-runner
								sed -i 's/ -ti//g' tvrunner.sh
							"""
						}
						stage('Run gNMI Test Vectors') {
							sh returnStdout: false, label: "Run gNMI Test Vectors" , script: """
								cd testvectors-runner
								./tvrunner.sh --target ${tv_config_dir}/$SWITCH_NAME/target.pb.txt --portmap ${tv_config_dir}/$SWITCH_NAME/loopback-portmap.pb.txt --template-config ${tv_config_dir}/$SWITCH_NAME/template_config.json --tv-dir ${WORKSPACE}/testvectors/templates/gnmi --dp-mode loopback
							"""
						}
					}
                }
            }
        }
    }
}
