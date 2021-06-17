pipeline {
    agent {
        label "${BUILD_NODE}"
    }
    options {
        timeout(time: 240, unit: 'MINUTES')
    }
    stages {
		stage("Build") {
			steps {
				sh returnStdout: false, label: "Start building stratum-${TARGET}:${SDE_VERSION}", script: ""
				build job: "stratum-${TARGET}-build", parameters: [
					string(name: 'SDE_VERSION', value: "${SDE_VERSION}"),
					string(name: 'KERNEL_VERSION', value: "${KERNEL_VERSION}"),
					string(name: 'REGISTRY_URL', value: "${REGISTRY_URL}"),
					string(name: 'REGISTRY_CREDENTIAL', value: "${REGISTRY_CREDENTIAL}"),
				]
			}
		}
		stage('Test') {
			steps {
				sh returnStdout: false, label: "Start testing ${REGISTRY_URL}/stratum-${TARGET}:${SDE_VERSION}", script: ""
				build job: "stratum-${TARGET}-test-combined", parameters: [
					string(name: 'REGISTRY_URL', value: "${REGISTRY_URL}"),
					string(name: 'REGISTRY_CREDENTIAL', value: "${REGISTRY_CREDENTIAL}"),
					string(name: 'DOCKER_IMAGE', value: "stratum-${TARGET}"),
					string(name: 'DOCKER_IMAGE_TAG', value: "${SDE_VERSION}"),
					string(name: 'TARGET', value: "${TARGET}"),
				]
			}
		}
		stage('Publish') {
			steps {
				sh returnStdout: false, label: "Start publishing ${REGISTRY_URL}/stratum-${TARGET}:${SDE_VERSION}", script: ""
				build job: "stratum-publish", parameters: [
					string(name: 'REGISTRY_URL', value: "${REGISTRY_URL}"),
					string(name: 'REGISTRY_CREDENTIAL', value: "${REGISTRY_CREDENTIAL}"),
					string(name: 'DOCKER_REPOSITORY_NAME', value: "stratum-${TARGET}"),
					string(name: 'DOCKER_IMAGE_TAG', value: "${SDE_VERSION}"),
				]
			}
		}
    }
    /*post {
        failure {
            slackSend color: 'danger', message: "Test failed: ${env.JOB_NAME} #${env.BUILD_NUMBER} (<${env.RUN_DISPLAY_URL}|Open>)"
        }
    }*/
}
