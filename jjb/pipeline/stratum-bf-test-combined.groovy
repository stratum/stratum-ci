import org.jenkins.plugins.lockableresources.LockableResourcesManager as LRM
def test_config = null

pipeline {
    agent {
        label "${BUILD_NODE}"
    }
    options {
        timeout(time: 180, unit: 'MINUTES')
    }
    stages {
        stage('Preparations') {
            steps {
                sh returnStdout: false, label: "Start testing ${DOCKER_IMAGE}:${DOCKER_IMAGE_TAG}", script: ""
                step([$class: 'WsCleanup'])
                script {
                    try {
                        sh returnStdout: false, label: "Get Stratum CI repo" , script: """
                            git clone https://github.com/stratum/stratum-ci.git
                        """
                        test_config = readYaml file: "${WORKSPACE}/stratum-ci/resources/test-config.yaml"
                    } catch (err) {
                        echo "Error reading test-config.yaml"
                        throw err
                    }
                }
            }
        }
        stage('Test') {
            steps {
                script {
                    def tests = [:]
                    for (name in test_config.switches.keySet()) {
                        def switch_name = name
                        if (test_config.switches[switch_name].platform == 'bf' && (LRM.get().fromName(switch_name) == null || !LRM.get().fromName(switch_name).isReserved())) {
                            for (String sdk : test_config.switches[switch_name].supported_sdks) {
                                if(sdk == DOCKER_IMAGE_TAG) {
                                    tests[switch_name] = {
                                        node {
                                            stage(switch_name) {sh returnStdout: false, label: "Start testing on "+switch_name+" with image ${DOCKER_IMAGE}:${DOCKER_IMAGE_TAG}", script: ""
                                                build job: "stratum-${TARGET}-test", parameters: [
                                                    string(name: 'SWITCH_NAME', value: switch_name),
                                                    string(name: 'REGISTRY_URL', value: "${REGISTRY_URL}"),
                                                    string(name: 'REGISTRY_CREDENTIAL', value: "${REGISTRY_CREDENTIAL}"),
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
                    parallel tests
                }
            }
        }
    }
}
