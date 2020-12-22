#!/usr/bin/env bash

###########################################################################
# Download and Install Vault                                              #
# This script is prepared for caching of the download directory           #
###########################################################################

set -o errexit

EDITION="${EDITION:-oss}"
VAULT_OSS="${VAULT_OSS:-1.6.1}"
VAULT_ENT="${VAULT_ENT:-1.6.1}"
UNAME=$(uname -s | tr '[:upper:]' '[:lower:]')
VERBOSE=false
VAULT_DIRECTORY=vault
DOWNLOAD_DIRECTORY=download
PLATFORM=amd64
readonly script_name="$(basename "${BASH_SOURCE[0]}")"

function say() {
  echo "$@"
}

function verbose() {
  if [[ ${VERBOSE} == true ]]; then
    echo "$@"
  fi
}

function initialize() {
  # cleanup
  mkdir -p ${VAULT_DIRECTORY}
  mkdir -p ${DOWNLOAD_DIRECTORY}
}

function usage() {
  cat <<EOF
Usage: ${script_name} [OPTION]...
Download and extract HashiCorp Vault
Options:
  -h|--help                    Displays this help
  -v|--version                 Vault version number
  -e|--edition oss|enterprise  Vault Edition
EOF
}

function parse_options() {
  local option
  while [[ $# -gt 0 ]]; do
    option="$1"
    shift
    case ${option} in
    -h | -H | --help)
      usage
      exit 0
      ;;
    --verbose)
      VERBOSE=true
      ;;
    -v | --version)
      VAULT_VER="$1"
      verbose "VAULT_VER=${VAULT_VER}"
      shift
      ;;
    -e | --edition)
      EDITION="$1"
      verbose "EDITION=${EDITION}"
      shift
      ;;

    *)
      script_exit "Invalid argument was provided: ${option}" 2
      ;;
    esac
  done
}

function unpack() {

  cd ${VAULT_DIRECTORY}

  if [[ -f vault ]]; then
    rm vault
  fi

  say "Unzipping ${VAULT_FILE}..."
  verbose " unzip ../${DOWNLOAD_DIRECTORY}/${VAULT_FILE}"
  if [[ ${VERBOSE} == true ]]; then
    unzip "../${DOWNLOAD_DIRECTORY}/${VAULT_FILE}"
  else
    unzip -q "../${DOWNLOAD_DIRECTORY}/${VAULT_FILE}"
  fi

  chmod a+x vault

  # check
  ./vault --version
  cd ..
}

function download() {

  if [[ ! -f "${DOWNLOAD_DIRECTORY}/${VAULT_FILE}" ]]; then
    cd ${DOWNLOAD_DIRECTORY}
    # install Vault
    say "Downloading Vault from ${VAULT_URL}"

    verbose "wget ${VAULT_URL} -O ${VAULT_FILE}"

    if [[ ${VERBOSE} == true ]]; then
      wget "${VAULT_URL}" -O "${VAULT_FILE}"
    else
      wget "${VAULT_URL}" -q -O "${VAULT_FILE}"
    fi

    if [[ $? != 0 ]]; then
      echo "Cannot download Vault"
      exit 1
    fi
    cd ..
  fi
}

function download_oss() {

  VAULT_VER="${VAULT_VER:-${VAULT_OSS}}"
  VAULT_ZIP="vault_${VAULT_VER}_${UNAME}_${PLATFORM}.zip"
  VAULT_FILE=${VAULT_ZIP}
  VAULT_URL="https://releases.hashicorp.com/vault/${VAULT_VER}/${VAULT_ZIP}"

  download
  unpack
}

function download_enterprise() {

  VAULT_VER="${VAULT_VER:-${VAULT_ENT}}"
  VAULT_ZIP="vault_${VAULT_VER}%2Bent_${UNAME}_${PLATFORM}.zip"
  VAULT_FILE="vault_${VAULT_VER}+ent_${UNAME}_${PLATFORM}.zip"
  VAULT_URL="https://releases.hashicorp.com/vault/${VAULT_VER}%2Bent/${VAULT_ZIP}"

  download
  unpack
}

function main() {

  initialize
  parse_options "$@"
  if [ "$(uname -m)" == aarch64 ]; then
    PLATFORM=arm64
  fi
  if [[ ${EDITION} == 'oss' ]]; then
    download_oss
  elif [[ ${EDITION} == 'enterprise' ]]; then
    download_enterprise
  else
    say "Ignoring edition option: ${EDITION} - oss and enterprise supported only"
    exit 1
  fi
}

main "$@"
