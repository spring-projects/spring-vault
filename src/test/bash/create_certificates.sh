#!/bin/bash

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
CA_DIR=work/ca
KEYSTORE_FILE=work/keystore.jks
CLIENT_CERT_KEYSTORE=work/client-cert.jks

if [[ -d work/ca ]] ; then
    rm -Rf ${CA_DIR}
fi

if [[ -f ${KEYSTORE_FILE} ]] ; then
    rm -Rf ${KEYSTORE_FILE}
fi

if [[ -f ${CLIENT_CERT_KEYSTORE} ]] ; then
    rm -Rf ${CLIENT_CERT_KEYSTORE}
fi

if [ ! -x "$(which openssl)" ] ; then
   echo "[ERROR] No openssl in PATH"
   exit 1
fi

KEYTOOL=keytool

if [  ! -x "${KEYTOOL}" ] ; then
   KEYTOOL=${JAVA_HOME}/bin/keytool
fi

if [  ! -x "${KEYTOOL}" ] ; then
   echo "[ERROR] No keytool in PATH/JAVA_HOME"
   exit 1
fi

mkdir -p ${CA_DIR}/private ${CA_DIR}/certs ${CA_DIR}/crl ${CA_DIR}/csr ${CA_DIR}/newcerts ${CA_DIR}/intermediate

echo "[INFO] Generating CA private key"
# Less bits = less secure = faster to generate
openssl genrsa -passout pass:changeit -aes256 -out ${CA_DIR}/private/ca.key.pem 2048

chmod 400 ${CA_DIR}/private/ca.key.pem

echo "[INFO] Generating CA certificate"
openssl req -config ${DIR}/openssl.cnf \
      -key ${CA_DIR}/private/ca.key.pem \
      -new -x509 -days 7300 -sha256 -extensions v3_ca \
      -out ${CA_DIR}/certs/ca.cert.pem \
      -passin pass:changeit \
      -subj "/C=NN/ST=Unknown/L=Unknown/O=spring-cloud-vault-config/CN=CA Certificate"

echo "[INFO] Prepare CA database"
echo 1000 > ${CA_DIR}/serial
touch ${CA_DIR}/index.txt

echo "[INFO] Generating server private key"
openssl genrsa -aes256 \
      -passout pass:changeit \
      -out ${CA_DIR}/private/localhost.key.pem 2048

openssl rsa -in ${CA_DIR}/private/localhost.key.pem \
      -out ${CA_DIR}/private/localhost.decrypted.key.pem \
      -passin pass:changeit

chmod 400 ${CA_DIR}/private/localhost.key.pem
chmod 400 ${CA_DIR}/private/localhost.decrypted.key.pem

echo "[INFO] Generating server certificate request"
openssl req -config <(cat ${DIR}/openssl.cnf \
        <(printf "\n[SAN]\nsubjectAltName=DNS:localhost,IP:127.0.0.1")) \
      -reqexts SAN \
      -key ${CA_DIR}/private/localhost.key.pem \
      -passin pass:changeit \
      -new -sha256 -out ${CA_DIR}/csr/localhost.csr.pem \
      -subj "/C=NN/ST=Unknown/L=Unknown/O=spring-cloud-vault-config/CN=localhost"

echo "[INFO] Signing certificate request"
openssl ca -config ${DIR}/openssl.cnf \
      -extensions server_cert -days 7300 -notext -md sha256 \
      -passin pass:changeit \
      -batch \
      -in ${CA_DIR}/csr/localhost.csr.pem \
      -out ${CA_DIR}/certs/localhost.cert.pem


echo "[INFO] Generating client auth private key"
openssl genrsa -aes256 \
      -passout pass:changeit \
      -out ${CA_DIR}/private/client.key.pem 2048

openssl rsa -in ${CA_DIR}/private/client.key.pem \
      -out ${CA_DIR}/private/client.decrypted.key.pem \
      -passin pass:changeit

chmod 400 ${CA_DIR}/private/client.key.pem

echo "[INFO] Generating client certificate request"
openssl req -config ${DIR}/openssl.cnf \
      -key ${CA_DIR}/private/client.key.pem \
      -passin pass:changeit \
      -new -sha256 -out ${CA_DIR}/csr/client.csr.pem \
      -subj "/C=NN/ST=Unknown/L=Unknown/O=spring-cloud-vault-config/CN=client"

echo "[INFO] Signing certificate request"
openssl ca -config ${DIR}/openssl.cnf \
      -extensions usr_cert -days 7300 -notext -md sha256 \
      -passin pass:changeit \
      -batch \
      -in ${CA_DIR}/csr/client.csr.pem \
      -out ${CA_DIR}/certs/client.cert.pem

echo "[INFO] Creating  PKCS12 file with client certificate"
openssl pkcs12 -export -clcerts \
      -in ${CA_DIR}/certs/client.cert.pem \
      -inkey ${CA_DIR}/private/client.decrypted.key.pem \
      -passout pass:changeit \
      -out ${CA_DIR}/client.p12

${KEYTOOL} -importcert -keystore ${KEYSTORE_FILE} -file ${CA_DIR}/certs/ca.cert.pem -noprompt -storepass changeit
${KEYTOOL} -importkeystore \
                              -srckeystore ${CA_DIR}/client.p12 -srcstoretype PKCS12 -srcstorepass changeit\
                              -destkeystore ${CLIENT_CERT_KEYSTORE} -deststoretype JKS \
                              -noprompt -storepass changeit

echo "[INFO] Generating intermediate CA private key"
# Less bits = less secure = faster to generate
openssl genrsa -passout pass:changeit -aes256 -out ${CA_DIR}/private/intermediate.key.pem 2048

openssl rsa -in ${CA_DIR}/private/intermediate.key.pem \
      -out ${CA_DIR}/private/intermediate.decrypted.key.pem \
      -passin pass:changeit

chmod 400 ${CA_DIR}/private/intermediate.key.pem
chmod 400 ${CA_DIR}/private/intermediate.decrypted.key.pem

echo "[INFO] Generating intermediate certificate"
openssl req -config ${DIR}/intermediate.cnf \
      -key ${CA_DIR}/private/intermediate.key.pem \
      -new -sha256 \
      -out ${CA_DIR}/csr/intermediate.csr.pem \
      -passin pass:changeit \
      -subj "/C=NN/ST=Unknown/L=Unknown/O=spring-cloud-vault-config/CN=Intermediate CA Certificate"

echo "[INFO] Signing intermediate certificate request"
openssl ca -config ${DIR}/openssl.cnf \
      -days 7300 -notext -md sha256 -extensions v3_intermediate_ca \
      -passin pass:changeit \
      -batch \
      -in ${CA_DIR}/csr/intermediate.csr.pem \
      -out ${CA_DIR}/certs/intermediate.cert.pem
