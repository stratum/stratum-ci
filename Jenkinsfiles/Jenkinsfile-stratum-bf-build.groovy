/*
Build Parameters
BUILD_NODE: p4-dev
DOCKER_REGISTRY_IP: 10.128.13.253
DOCKER_REGISTRY_PORT: 5000
SDE_VERSION: 8.9.2
KERNEL_VERSION: 4.14.49
BAZEL_DISK_CACHE: /home/sdn/bazel-disk-cache
*/

pipeline {
	agent {
		label "${BUILD_NODE}"
	}
	options {
		timeout(time: 60, unit: 'MINUTES')
	}
	stages {
		stage('Build') {
			steps {
				step([$class: 'WsCleanup'])
				sh returnStdout: false, label: "Start building stratum-bf:bf-sde-${SDE_VERSION}-linux-${KERNEL_VERSION}-OpenNetworkLinux", script: """
					git clone https://github.com/stratum/stratum.git
					cd ${WORKSPACE}/stratum
					cd ${WORKSPACE}/stratum/stratum/hal/bin/barefoot/docker
					./build-stratum-bf-container.sh /var/jenkins/stratum-contents/bf-sde-${SDE_VERSION}.tgz /var/jenkins/stratum-contents/linux-${KERNEL_VERSION}-OpenNetworkLinux.tar.xz
					docker tag stratumproject/stratum-bf:bf-sde-${SDE_VERSION}-linux-${KERNEL_VERSION}-OpenNetworkLinux ${DOCKER_REGISTRY_IP}:${DOCKER_REGISTRY_PORT}/stratum-bf:bf-sde-${SDE_VERSION}-linux-${KERNEL_VERSION}-OpenNetworkLinux
					docker push ${DOCKER_REGISTRY_IP}:${DOCKER_REGISTRY_PORT}/stratum-bf:bf-sde-${SDE_VERSION}-linux-${KERNEL_VERSION}-OpenNetworkLinux
				"""
			}
		}
		stage('Unit Test') {
			steps {
				sh returnStdout: false, label: "Run unit tests for stratum-bf:bf-sde-${SDE_VERSION}-linux-${KERNEL_VERSION}-OpenNetworkLinux", script: """
					cd ${WORKSPACE}/stratum/
					sed -i '1i build --disk_cache=/tmp/bazel-disk-cache' .bazelrc
					docker run --rm -v ${BAZEL_DISK_CACHE}:/tmp/bazel-disk-cache -v ${WORKSPACE}/stratum:/stratum ${DOCKER_REGISTRY_IP}:${DOCKER_REGISTRY_PORT}/stratum-unit
				"""
			}
		}
	}
}