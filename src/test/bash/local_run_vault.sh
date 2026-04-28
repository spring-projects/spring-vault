#!/bin/bash

###########################################################################
# Start Vault on localhost:8200                                           #
###########################################################################

set -o errexit
set -o pipefail

BASEDIR="$(cd "$(dirname "$0")/../../.." && pwd)"

exec "${BASEDIR}/vault/vault" server -config="${BASEDIR}/src/test/bash/vault.conf"
