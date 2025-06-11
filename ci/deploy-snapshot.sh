#!/bin/bash

set -euo pipefail
MAVEN_OPTS="-Duser.name=jenkins -Duser.home=/tmp/jenkins-home -Dmaven.repo.local=/tmp/jenkins-home/.m2/spring-vault" ./mvnw -s settings.xml -Pci,snapshot,artifactory \
      -Dartifactory.server=https://repo.spring.io \
      -Dartifactory.username=${ARTIFACTORY_USR} \
      -Dartifactory.password=${ARTIFACTORY_PSW} \
      -Dartifactory.staging-repository=libs-snapshot-local \
      -Dartifactory.build-name=spring-vault \
      -Dartifactory.build-number=${BUILD_NUMBER} \
      -Dmaven.test.skip=true \
      clean deploy -U -B
