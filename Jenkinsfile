def p = [:]
node {
	checkout scm
	p = readProperties interpolate: true, file: 'ci/pipeline.properties'
}

pipeline {
	agent none

	triggers {
		pollSCM 'H/10 * * * *'
	}

	options {
		disableConcurrentBuilds()
		buildDiscarder(logRotator(numToKeepStr: '14'))
	}

	stages {
		stage("Docker images") {
			parallel {
				stage('Publish JDK 17 + Vault Docker image') {
					when {
						anyOf {
							changeset "ci/openjdk17-vault/Dockerfile"
							changeset "src/test/bash/install_vault.sh"
							changeset "ci/pipeline.properties"
						}
					}
					agent { label 'data' }
					options { timeout(time: 20, unit: 'MINUTES') }

					steps {
						script {
							def image = docker.build("${p['docker.build.image.name']}", "--build-arg BASE=${p['docker.java.main.image']} --build-arg VAULT=${p['docker.vault.version']} -f ci/openjdk17-vault/Dockerfile .")
							docker.withRegistry(p['docker.registry'], p['docker.credentials']) {
								image.push()
							}
						}
					}
				}
			}
		}

		stage("test: baseline (Java 17)") {
			when {
				beforeAgent(true)
				anyOf {
					branch(pattern: "main|(\\d\\.\\d\\.x)", comparator: "REGEXP")
					not { triggeredBy 'UpstreamCause' }
				}
			}
			agent {
				label 'data'
			}
			options { timeout(time: 30, unit: 'MINUTES') }
			environment {
				ARTIFACTORY = credentials("${p['artifactory.credentials']}")
			}
			steps {
				script {
					docker.withRegistry(p['docker.proxy.registry'], p['docker.proxy.credentials']) {
						docker.image("${p['docker.image']}").inside(p['docker.java.inside.docker']) {
							sh 'src/test/bash/create_certificates.sh'
							sh '/opt/vault/vault server -config=$(pwd)/src/test/bash/vault.conf &'
							sh 'MAVEN_OPTS="-Duser.name=jenkins -Duser.home=/tmp/jenkins-home -Dmaven.repo.local=/tmp/jenkins-home/.m2/spring-vault" ./mvnw -s settings.xml clean dependency:list verify -Dsort -U -B'
						}
					}
				}
			}
		}

		stage('Deploy') {
			when {
				beforeAgent(true)
				anyOf {
					branch(pattern: "main|(\\d\\.\\d\\.x)|v(\\d\\.\\d\\.d)|", comparator: "REGEXP")
					not { triggeredBy 'UpstreamCause' }
				}
			}
			agent {
				docker {
					label 'data'
					image "${p['docker.image']}"
					args "${p['docker.java.inside.docker']}"
					registryUrl "${p['docker.proxy.registry']}"
					registryCredentialsId "${p['docker.proxy.credentials']}"
				}
			}
			options { timeout(time: 20, unit: 'MINUTES') }

			environment {
				ARTIFACTORY = credentials("${p['artifactory.credentials']}")
				CENTRAL_TOKEN = credentials('central-token')
				KEYRING = credentials('spring-signing-secring.gpg')
				PASSPHRASE = credentials('spring-gpg-passphrase')
			}

			steps {
				script {
					PROJECT_VERSION = sh(
							script: "ci/version.sh",
							returnStdout: true
					).trim()

					RELEASE_TYPE = 'snapshot'

					if (PROJECT_VERSION.matches(/.*-RC[0-9]+$/) || PROJECT_VERSION.matches(/.*-M[0-9]+$/)) {
						RELEASE_TYPE = "milestone"
					} else if (PROJECT_VERSION.endsWith('SNAPSHOT')) {
						RELEASE_TYPE = 'snapshot'
					} else if (PROJECT_VERSION.matches(/.*\.[0-9]+$/)) {
						RELEASE_TYPE = 'release'
					}

					sh "ci/deploy-${RELEASE_TYPE}.sh"
				}
			}
		}
	}

	post {
		changed {
			script {
				emailext(
						subject: "[${currentBuild.fullDisplayName}] ${currentBuild.currentResult}",
						mimeType: 'text/html',
						recipientProviders: [[$class: 'CulpritsRecipientProvider'], [$class: 'RequesterRecipientProvider']],
						body: "<a href=\"${env.BUILD_URL}\">${currentBuild.fullDisplayName} is reported as ${currentBuild.currentResult}</a>")
			}
		}
	}
}
