ARG BASE
FROM ${BASE}
# Any ARG statements before FROM are cleared.

ENV TZ=Etc/UTC
ENV DEBIAN_FRONTEND=noninteractive

# Copy Vault's install file into the container

COPY ./src/test/bash/install_vault.sh /opt

ARG VAULT

RUN set -eux && \
	sed -i -e 's/archive.ubuntu.com/mirror.one.com/g' /etc/apt/sources.list && \
	sed -i -e 's/security.ubuntu.com/mirror.one.com/g' /etc/apt/sources.list && \
	sed -i -e 's/http/https/g' /etc/apt/sources.list && \
	apt-get update && apt-get install -y apt-transport-https apt-utils gnupg2 wget unzip && \
	echo ${TZ} > /etc/timezone && \
	rm -rf /var/lib/apt/lists/*

RUN set -eux && \
	cd /opt && \
	/opt/install_vault.sh -v ${VAULT}
