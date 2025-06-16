#!/bin/bash

set -euo pipefail

GNUPGHOME=/tmp/gpghome
export GNUPGHOME

mkdir $GNUPGHOME
cp $KEYRING $GNUPGHOME

MAVEN_OPTS="-Duser.name=jenkins -Duser.home=/tmp/jenkins-home -Dmaven.repo.local=/tmp/jenkins-home/.m2/spring-vault" ./mvnw -s settings.xml -Pci,central,release \
      -Dgpg.passphrase=${PASSPHRASE} \
      -Dgpg.secretKeyring=${GNUPGHOME}/secring.gpg \
      clean deploy -U -B

MAVEN_OPTS="-Duser.name=jenkins -Duser.home=/tmp/jenkins-home -Dmaven.repo.local=/tmp/jenkins-home/.m2/spring-vault" ./mvnw -s settings.xml -Pdistribute \
      -Dartifactory.server=https://repo.spring.io \
      -Dartifactory.username=${ARTIFACTORY_USR} \
      -Dartifactory.password=${ARTIFACTORY_PSW} \
      -Dartifactory.staging-repository=temp-private-local \
      clean deploy -U -B
