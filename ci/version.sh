#!/bin/bash

set -euo pipefail

MAVEN_OPTS="-Duser.name=jenkins -Duser.home=/tmp/jenkins-home -Dmaven.repo.local=/tmp/jenkins-home/.m2/spring-vault" ./ci/get-version.sh
