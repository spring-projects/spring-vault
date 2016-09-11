#!/bin/bash

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

###########################################################################
# Vault environment settings. Source this file.                           #
###########################################################################

export VAULT_TOKEN=00000000-0000-0000-0000-000000000000
export VAULT_ADDR=https://localhost:8200
export VAULT_SKIP_VERIFY=false
export VAULT_CAPATH=${DIR}/work/ca/certs/ca.cert.pem
