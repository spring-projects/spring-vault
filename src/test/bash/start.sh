#!/bin/bash

###########################################################################
# Prepare all the things required to run Vault                            #
###########################################################################

set -o errexit
set -o pipefail

SCRIPT_DIR=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )
BASEDIR="${SCRIPT_DIR}/../../../"

# Just in case...
pkill vault || true

mkdir -p "${BASEDIR}/download"
mkdir -p "${BASEDIR}/work"

"${BASEDIR}/src/test/bash/install_vault.sh"
"${BASEDIR}/src/test/bash/create_certificates.sh"
"${BASEDIR}/src/test/bash/env.sh"
"${BASEDIR}/src/test/bash/local_run_vault.sh" &
