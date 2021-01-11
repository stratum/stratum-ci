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
                withCredentials([file(credentialsId: 'JJB_INI', variable: 'jjb_ini')]) {
                    sh returnStdout: false, label: "Git Pull Stratum CI", script: """
                        git clone https://github.com/stratum/stratum-ci.git -b jjb
                        cd ${WORKSPACE}/stratum-ci
			cp ${jjb_ini} jenkins.ini
			virtualenv venv
			. venv/bin/activate 
			pip install jenkins-job-builder===${JJB_VERSION}
			jenkins-jobs --conf jenkins.ini test jjb
                    """
                }
            }
        }
    }
}
