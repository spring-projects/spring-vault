#!/bin/bash

set -euo pipefail

GNUPGHOME=/tmp/gpghome
export GNUPGHOME

mkdir $GNUPGHOME
cp $KEYRING $GNUPGHOME
chmod -R go-rwx $GNUPGHOME

export MAVEN_GPG_PASSPHRASE="${PASSPHRASE}"

MAVEN_OPTS="-Duser.name=jenkins -Duser.home=/tmp/jenkins-home -Dmaven.repo.local=/tmp/jenkins-home/.m2/spring-vault" ./ci/deploy-maven-central.sh
