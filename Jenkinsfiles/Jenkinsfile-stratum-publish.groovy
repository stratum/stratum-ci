/*
Build Parameters
BUILD_NODE: p4-dev
DOCKER_IMAGE: 10.128.13.253:5000/stratum-bf
DOCKER_IMAGE_TAG: 8.9.2-4.14.49-OpenNetworkLinux
*/

pipeline {
	agent {
		label "${BUILD_NODE}"
	}
	options {
		timeout(time: 10, unit: 'MINUTES')
	}
	stages {
		stage('Publish') {
			steps {
				sh returnStdout: false, label: "Start publishing ${DOCKER_IMAGE}:${DOCKER_IMAGE_TAG}", script: """
					image="stratumproject/"\$(echo $DOCKER_IMAGE | cut -d'/' -f2)
					docker tag ${DOCKER_IMAGE}:${DOCKER_IMAGE_TAG} \$image:${DOCKER_IMAGE_TAG}
					docker push \$image:${DOCKER_IMAGE_TAG}
				"""
			}
		}
	}
}