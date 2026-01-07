#!/bin/bash

set -euo pipefail

RAW_VERSION=`./mvnw \
  org.apache.maven.plugins:maven-help-plugin:3.5.1:evaluate \
  -Ddevelocity.scan.disabled=true -Dexpression=project.version -q -DforceStdout`

# Split things up
VERSION_PARTS=($RAW_VERSION)

# Grab the last part, which is the actual version number.
echo ${VERSION_PARTS[${#VERSION_PARTS[@]}-1]}
