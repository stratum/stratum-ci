import org.jenkins.plugins.lockableresources.LockableResourcesManager as LRM
def test_config = null

pipeline {
    agent {
        label "${BUILD_NODE}"
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
                        def switch_ip = test_config.switches[switch_name].ip
                        if (test_config.switches[switch_name].platform == 'bcm' && (LRM.get().fromName(switch_name) == null || !LRM.get().fromName(switch_name).isReserved())) {
                            for (String sdk : test_config.switches[switch_name].supported_sdks) {
                                if (sdk == DOCKER_IMAGE_TAG ){
                                    // TODO: Re-enable after updating stratum-bcm-test to fetch latest debian package
                                    // tests[switch_name+"-debian"] = {
                                    //     node("${BUILD_NODE}") {
                                    //         stage(switch_name) {sh returnStdout: false, label: "Start testing on "+switch_name+" with image ${DOCKER_IMAGE}:${DOCKER_IMAGE_TAG}", script: ""
                                    //             build job: "stratum-bcm-test", parameters: [
                                    //                 string(name: 'SWITCH_NAME', value: switch_name),
                                    //                 string(name: 'DEBIAN_PACKAGE_PATH', value: "${DEBIAN_PACKAGE_PATH}"),
                                    //                 string(name: 'DEBIAN_PACKAGE_NAME', value: "${DEBIAN_PACKAGE_NAME}"),
                                    //             ]
                                    //         }
                                    //     }
                                    // }
                                    node("${BUILD_NODE}") {
                                        stage('Docker'){
                                            script {
                                                withCredentials([
                                                    usernamePassword(credentialsId: switch_name+"-credentials",
                                                    usernameVariable: 'username',
                                                    passwordVariable: 'password')
                                                ]) {
                                                    try {
							                            sh(script:'ssh-keyscan '+switch_ip+' >> ~/.ssh/known_hosts')
							                            def hasDocker = sh(script:'sshpass -p $password ssh $username@'+switch_ip+' "which docker"',returnStdout:true).trim()
                                                        tests[switch_name+"-docker"] = {
                                                            node {
                                                                stage(switch_name) {sh returnStdout: false, label: "Start testing on "+switch_name+" with image ${DOCKER_IMAGE}:${DOCKER_IMAGE_TAG}", script: ""
                                                                    build job: "stratum-bcm-test", parameters: [
                                                                        string(name: 'SWITCH_NAME', value: switch_name),
                                                                        string(name: 'REGISTRY_URL', value: "${REGISTRY_URL}"),
                                                                        string(name: 'REGISTRY_CREDENTIAL', value: "${REGISTRY_CREDENTIAL}"),
                                                                        string(name: 'DOCKER_IMAGE', value: "${DOCKER_IMAGE}"),
                                                                        string(name: 'DOCKER_IMAGE_TAG', value: "${DOCKER_IMAGE_TAG}"),
                                                                    ]
                                                                }
                                                            }
                                                        }
                                                    } catch (Exception ex) {
                                                        println("No docker installation found on "+switch_name)
                                                        println("Exception :"+ex)
                                                    }
                                                }
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
        stage('Publish') {
            steps {
                    build job: "stratum-bcm-publish", parameters: [
                                                                        string(name: 'TARGET', value: "${TARGET}"),
                                                                        string(name: 'DOCKER_IMAGE_TAG', value: "${TARGET}"),
                                                                    ]
            }
        }
    }
}
