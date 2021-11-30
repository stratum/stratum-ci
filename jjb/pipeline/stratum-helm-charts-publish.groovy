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
    environment {
        PATH="$WORKSPACE/linux-amd64:$PATH"
        GITHUB_ACCESS = credentials('stratum_github_access_token')
        PUBLISH_URL ="charts.stratumproject.org/"
        OLD_REPO_DIR ="stratum-helm-repo"
        NEW_REPO_DIR ="chart_repo"
    }
    stages {
      stage ("Checkout Repo") {
            steps {
                checkout([
                    $class: 'GitSCM',
                    userRemoteConfigs: [[ url: "https://github.com/stratum/stratum-helm-charts" ]],
                    ],
                )
            }
       }
      stage('Install tools') {
          steps {
              sh """#!/bin/bash
                  set -x
                  # Install Helm3
                  wget https://get.helm.sh/helm-v3.4.1-linux-amd64.tar.gz
                  tar -xvf helm-v3.4.1-linux-amd64.tar.gz

                  helm version
                  git clone "https://gerrit.opencord.org/helm-repo-tools"
                  git clone "https://${GITHUB_ACCESS}@github.com/stratum/stratum-helm-repo"
                  """
          }
      }
      stage('Update Repo') {
            steps {
                sh """#!/bin/bash
                set -x
                ./helm-repo-tools/helmrepo.sh
                git config --global user.email "do-not-reply@opennetworking.org"
                git config --global user.name "Jenkins"
                # Tag and push to git the charts repo
                pushd stratum-helm-repo

                  # update if charts are changed or the first change
                  set +e
                  if ! git ls-files --others --exclude-standard | grep index.yaml && git diff --exit-code index.yaml > /dev/null; then
                    echo "No changes to charts in patchset"
                    exit 0
                  fi
                  set -e

                  # version tag is the current date in RFC3339 format
                  NEW_VERSION=\$(date -u +%Y%m%dT%H%M%SZ)

                  # Add changes and create commit
                  git add -A
                  git commit -m "Changed by Stratum Jenkins stratum-helm-chart-publish job"

                  # create tag on new commit
                  git tag "\$NEW_VERSION"

                  echo "Tags including new tag:"
                  git tag -n

                  git push origin
                  git push origin "\$NEW_VERSION"
                popd
                """
            }
        }
        stage('Publish') {
            steps {
                withCredentials([sshUserPrivateKey(credentialsId: "charts.stratumproject.org", keyFileVariable: 'keyfile')]) {
                sh """#!/bin/bash
                set -x
                ssh-keyscan -t rsa charts.stratumproject.org >> ~/.ssh/known_hosts
                rsync -e "ssh -i ${keyfile}"  -rvzh --delete-after --exclude=.git stratum-helm-repo/ jenkins@charts.stratumproject.org:/srv/sites/charts.stratumproject.org/
                """
                }
            }
        }
    }
    post {
        always {
            cleanWs()
        }
    }
}
