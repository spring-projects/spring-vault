#!/bin/bash

###########################################################################
# Prepare all the things required to run Vault                            #
###########################################################################

BASEDIR=`dirname $0`/../../..

# Just in case...
pkill vault

mkdir -p ${BASEDIR}/download
mkdir -p ${BASEDIR}/work
${BASEDIR}/src/test/bash/install_vault.sh
${BASEDIR}/src/test/bash/create_certificates.sh
${BASEDIR}/src/test/bash/env.sh
${BASEDIR}/src/test/bash/local_run_vault.sh &
