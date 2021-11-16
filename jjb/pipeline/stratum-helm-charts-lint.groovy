// SPDX-FileCopyrightText: 2020 Open Networking Foundation <info@opennetworking.org>
//
// SPDX-License-Identifier: LicenseRef-ONF-Member-Only-1.0
//
// Makefile for testing JJB jobs in a virtualenv
//

pipeline {
    agent {
        label "${BUILD_NODE}"
    }
    options {
        timeout(15)
    }
    environment {
        PATH="$WORKSPACE/linux-amd64:$PATH"
    }
    stages {
        stage ("Clean Workspace") {
            steps {
                sh 'rm -rf *'
            }
        }
        stage ("Checkout Pull Request") {
            when {
                expression {return params.ghprbPullId != ""}
            }
            steps {
                checkout([
                    $class: 'GitSCM',
                    userRemoteConfigs: [[ url: "https://github.com/${params.ghprbGhRepository}", refspec: "pull/${params.ghprbPullId}/head" ]],
                    extensions: [[$class: 'RelativeTargetDirectory', relativeTargetDir: "${params.project}"]],
                    ],
                )
            }
        }
        stage ("Checkout Repo (manual)") {
            when {
                expression {return params.ghprbPullId == ""}
            }
            steps {
                checkout([
                    $class: 'GitSCM',
                    userRemoteConfigs: [[ url: "https://github.com/${params.ghprbGhRepository}" ]],
                    extensions: [[$class: 'RelativeTargetDirectory', relativeTargetDir: "${params.project}"]],
                    ],
                )
            }
        }
        stage ('Install tools') {
            steps {
                sh """
                #!/bin/bash
                set -x
                # Install Helm3
                wget https://get.helm.sh/helm-v3.4.1-linux-amd64.tar.gz
                tar -xvf helm-v3.4.1-linux-amd64.tar.gz

                helm version

                mkdir -p ~/.ssh
                ssh-keyscan -t rsa github.com >> ~/.ssh/known_hosts
                git clone "https://gerrit.opencord.org/helm-repo-tools"
                """
            }
        }
        stage ('Lint') {
            steps {
                sh """#!/bin/bash
                set -x
                ./helm-repo-tools/helmlint.sh clean
                """
            }
        }
    }
}
