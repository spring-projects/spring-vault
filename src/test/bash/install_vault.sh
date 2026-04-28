#!/usr/bin/env bash

###########################################################################
# Download and Install Vault                                              #
# This script is prepared for caching of the download directory           #
###########################################################################

set -o errexit
set -o pipefail

EDITION="${EDITION:-oss}"
VAULT_OSS="${VAULT_OSS:-1.15.1}"
VAULT_ENT="${VAULT_ENT:-1.15.1}"
VAULT_ENT_TYPE="${VAULT_ENT_TYPE:-ent}" #ent, ent.hsm, ent.hsm.fips1403
UNAME=$(uname -s | tr '[:upper:]' '[:lower:]')
VERBOSE=false
SCRIPT_DIR=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )
BASEDIR="${SCRIPT_DIR}/../../../"
VAULT_DIRECTORY="${BASEDIR}/vault"
DOWNLOAD_DIRECTORY="${BASEDIR}/download"
PLATFORM=amd64

function say() {
  echo "[INFO] $@"
}

function say_error() {
  echo "[ERROR] $@" >&2
}

function verbose() {
  if [[ ${VERBOSE} == true ]]; then
    echo "[TRACE] $@"
  fi
}

function verify_vault_zip() {
  [[ -f "$1" ]] && [[ -s "$1" ]] && unzip -tq "$1" vault >/dev/null 2>&1
}

function initialize() {
  # cleanup
  mkdir -p "${VAULT_DIRECTORY}"
  mkdir -p "${DOWNLOAD_DIRECTORY}"
}

function usage() {
  cat <<EOF
Usage: $(basename "${BASH_SOURCE[0]}") [OPTION]...
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
      say_error "Invalid argument was provided: ${option}"
      exit 1
      ;;
    esac
  done
}

function unpack() {
  local ZIP_PATH="${DOWNLOAD_DIRECTORY}/${VAULT_ZIP}"

  say "Verifying ${VAULT_ZIP} before unzipping..."
  if ! verify_vault_zip "${ZIP_PATH}"; then
    say_error "Cannot unpack: missing, empty, or invalid zip: ${ZIP_PATH}"
    ls -la "${DOWNLOAD_DIRECTORY}/"
    exit 1
  fi

  cd "${VAULT_DIRECTORY}"

  if [[ -f vault ]]; then
    rm vault
  fi

  say "Unzipping ${VAULT_ZIP}..."
  verbose " unzip ${ZIP_PATH}"
  if [[ ${VERBOSE} == true ]]; then
    unzip "${ZIP_PATH}" vault || {
      say_say_error "Failed to unzip ${VAULT_ZIP}"
      exit 1
    }
  else
    unzip -q "${ZIP_PATH}" vault || {
      say_error "Failed to unzip ${VAULT_ZIP}"
      exit 1
    }
  fi

  if [[ ! -f vault ]]; then
    say_error "vault binary missing after unzip"
    exit 1
  fi

  chmod a+x vault

  if [[ ! -x vault ]]; then
    say_error "vault binary not executable after chmod"
    exit 1
  fi

  if ! ./vault --version >/dev/null 2>&1; then
    say_error "vault binary does not run (--version failed)"
    exit 1
  fi
  ./vault --version

  cd ..
}

function download() {
  local ZIP_PATH="${DOWNLOAD_DIRECTORY}/${VAULT_ZIP}"

  if verify_vault_zip "${ZIP_PATH}"; then
    verbose "Using existing archive ${ZIP_PATH}"
    return 0
  fi

  say "Downloading Vault from ${VAULT_URL}"
  verbose "wget ${VAULT_URL} -O ${ZIP_PATH}"

  if ! command -v wget >/dev/null 2>&1; then
    echo "wget is required but was not found in PATH" >&2
    exit 1
  fi

  local WGET_STDERR_FILE
  WGET_STDERR_FILE=$(mktemp)

  if [[ ${VERBOSE} == true ]]; then
    wget --server-response --tries=3 --timeout=30 --read-timeout=60 "${VAULT_URL}" -O "${ZIP_PATH}" 2>"${WGET_STDERR_FILE}"
  else
    wget -q --server-response --tries=3 --timeout=30 --read-timeout=60 "${VAULT_URL}" -O "${ZIP_PATH}" 2>"${WGET_STDERR_FILE}"
  fi

  local WGET_EXIT=$?
  local HTTP_STATUS
  HTTP_STATUS=$(grep -oE 'HTTP/[0-9.]+ [0-9]+' "${WGET_STDERR_FILE}" | awk '{print $2}' | tail -1)

  if [[ ${VERBOSE} == true ]]; then
    cat "${WGET_STDERR_FILE}" >&2
  fi
  rm "${WGET_STDERR_FILE}" || true

  if [[ ${WGET_EXIT} -ne 0 ]]; then
    say_error "Failed to download Vault from ${VAULT_URL} (HTTP ${HTTP_STATUS:-unknown})"
    exit 1
  fi

  if ! verify_vault_zip "${ZIP_PATH}"; then
    say_error "Downloaded file is missing, empty, or not a valid Vault zip (wrong URL or corrupt transfer): ${ZIP_PATH}"
    exit 1
  fi
}

function download_oss() {

  VAULT_VER="${VAULT_VER:-${VAULT_OSS}}"
  VAULT_ZIP="vault_${VAULT_VER}_${UNAME}_${PLATFORM}.zip"
  VAULT_URL="https://releases.hashicorp.com/vault/${VAULT_VER}/${VAULT_ZIP}"

  download
  unpack
}

function download_enterprise() {

  VAULT_VER="${VAULT_VER:-${VAULT_ENT}+${VAULT_ENT_TYPE}}"
  VAULT_ZIP="vault_${VAULT_VER}_${UNAME}_${PLATFORM}.zip"
  VAULT_URL="https://releases.hashicorp.com/vault/${VAULT_VER}/${VAULT_ZIP}"

  download
  unpack
}

function main() {

  initialize
  parse_options "$@"
  if [ "$(uname -m)" == 'arm64' ]; then
    PLATFORM=arm64
  fi
  if [ "$(uname -m)" == 'aarch64' ]; then
    PLATFORM=arm64
  fi
  if [[ "${EDITION}" == 'oss' ]]; then
    download_oss
  elif [[ "${EDITION}" == 'enterprise' ]]; then
    download_enterprise
  else
    say_error "Ignoring edition option: ${EDITION} - oss and enterprise supported only"
    exit 1
  fi
}

main "$@"
