#!/bin/bash

#set -euo pipefail

PROJECT_VERSION=$(./ci/get-version.sh)

if [[ "$PROJECT_VERSION" == *SNAPSHOT ]]; then

  echo "Deploying snapshot to Artifactory"
  ./mvnw -s settings.xml -Pci,artifactory \
      -Dartifactory.server=https://repo.spring.io \
      -Dartifactory.username=${ARTIFACTORY_USR} \
      -Dartifactory.password=${ARTIFACTORY_PSW} \
      -Dartifactory.staging-repository=libs-snapshot-local \
      -Dartifactory.build-name=spring-vault \
      -Dartifactory.build-number=${BUILD_NUMBER} \
      -Dmaven.test.skip=true \
      clean deploy -U -B
else
  echo "Skipping Artifactory deployment, not a snapshot version."
fi


