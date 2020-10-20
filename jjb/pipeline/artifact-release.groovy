// Copyright 2020-present Open Networking Foundation
//
// SPDX-License-Identifier: LicenseRef-ONF-Member-Only-1.0

def project = '${project}'
def version = '${version}'
def nextVersion = '${nextVersion}'
def branch = '${branch}'
def snapshot = '${nextVersion}-SNAPSHOT'

// This pipeline updates the <version> tag according to the
// given repo, and pushes two new Gerrit changes:
//   1) With version the given ${version} (e.g., 1.0.0)
//   2) With ${nextVersion}-SNAPSHOT (e.g., 1.1.0-SNAPSHOT)
//
// Users must manually approve and merge these changes on Gerrit. Once merged,
// it's up to the maven-publish and version-tag jobs to complete the release by
// uploading artifacts to Sonatype and creating Git tags.

def changeVersion(def newVersion) {
  /* Update the top-level <version> tag. */
  sh( script: """
     #!/usr/bin/env bash
     set -eu -o pipefail

     # Artifact with VERSION file
     if [ -f "VERSION" ]
     then
        echo "${newVersion}" > VERSION
     # Maven artifact
     elif [ -f "pom.xml" ]
     then
        mvn versions:set -DnewVersion="${newVersion}" versions:commit
     else
        echo "ERROR: No versioning file found!"
        exit 1
     fi
     """)
}

/* artifact-release pipeline */
pipeline {

  /* executor is determined by JJB */
  agent {
    label "${params.buildNode}"
  }

  stages {

    /* clone the artifact and install the commit hook */
    stage('Checkout') {
      steps {
        sshagent (credentials: ['onos-jenkins-ssh']) {
          git branch: branch, url: "ssh://jenkins@gerrit.onosproject.org:29418/${params.project}", credentialsId: 'onos-jenkins-ssh'
          sh 'gitdir=$(git rev-parse --git-dir); scp -p -P 29418 jenkins@gerrit.onosproject.org:hooks/commit-msg ${gitdir}/hooks/'
        }
      }
    }

    /* configure the repository */
    stage('Configure') {
      steps {
        sh 'echo Releasing ' + project + ' repository on ' + branch + ' branch'
        sh 'echo Releasing version ' + version + ' and starting ' + nextVersion + '-SNAPSHOT'
        sh 'git config --global user.name "Jenkins"'
        sh 'git config --global user.email "jenkins@onlab.us"'
      }
    }

    /* release commit */
    stage ('Move to release version') {
      steps {
        changeVersion(version)
        sh 'git add -A && git commit -m "Release version ' + version + '"'
      }
    }

    /* verify step */
    stage ('Verify code') {
      steps {
        script {
          found = sh(script:"egrep -R SNAPSHOT . --exclude=$egrepExclude || true", returnStdout: true).trim()
        }
        sh( script: """
           #!/usr/bin/env bash
           set -eu -o pipefail
           if [ -n "$found" ]; then
              echo "Found references to SNAPSHOT in the code. Are you sure you want to release?"
              echo "$found"
              exit 1
           fi
          """)
      }
    }

    /* push to gerrit the release/tag commit */
    stage ('Push to Gerrit') {
      steps {
        sshagent (credentials: ['onos-jenkins-ssh']) {
          sh 'git push origin HEAD:refs/for/' + branch
        }
      }
    }

    /* snapshot commit */
    stage ('Move to next SNAPSHOT version') {
      steps {
        changeVersion(snapshot)
        sh 'git add -A && git commit -m "Starting snapshot ' + snapshot + '"'
        sshagent (credentials: ['onos-jenkins-ssh']) {
          sh 'git push origin HEAD:refs/for/' + branch
        }
      }
    }

    /* finish step */
    stage ('Finish') {
      steps {
        sh 'echo "Release done!"'
        sh 'echo "Go to Gerrit and merge new changes"'
        sh 'echo "Go to http://oss.sonatype.org and release the artifacts (after the maven-publish job completes)"'
      }
    }
  }

}
