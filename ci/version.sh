#!/bin/bash

set -euo pipefail

RAW_VERSION=`MAVEN_OPTS="-Duser.name=jenkins -Duser.home=/tmp/jenkins-home -Dmaven.repo.local=/tmp/jenkins-home/.m2/spring-vault" ./mvnw \
  org.apache.maven.plugins:maven-help-plugin:3.5.1:evaluate \
  -Dexpression=project.version -q -DforceStdout`

# Split things up
VERSION_PARTS=($RAW_VERSION)

# Grab the last part, which is the actual version number.
echo ${VERSION_PARTS[${#VERSION_PARTS[@]}-1]}
