#!/bin/bash

###########################################################################
# Start Vault on localhost:8200                                           #
###########################################################################

BASEDIR=`dirname $0`/../../..

./vault/vault server -config=${BASEDIR}/src/test/bash/vault.conf

exit $?
