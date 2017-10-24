#!/bin/bash

CMD_MINIKUBE=${1:-minikube}
CMD_KUBECTL=${2:-kubectl}

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

if [ ! -d "work" ]; then
  echo "work directory could not be found."
  exit 1
fi

mkdir -p work/minikube
SERVICE_ACCOUNT_TOKEN_FILE=work/minikube/hello-minikube-token

function is_cluster_running() {
    local _running=$(${CMD_MINIKUBE} status | grep "cluster: Running" || true)
    echo "$_running"
}

if [[ -z "$(is_cluster_running)" ]]; then
    ${CMD_MINIKUBE} start --vm-driver=none --extra-config=apiserver.InsecureServingOptions.BindAddress="127.0.0.1" --extra-config=apiserver.InsecureServingOptions.BindPort="8080"
    while [[ -z "$(is_cluster_running)" ]]; do
        echo "Wait for minikube cluster to be up"
        sleep 1
    done
fi

export MINIKUBE_IP=$(${CMD_MINIKUBE} ip)
echo "MINIKUBE_IP ${MINIKUBE_IP}"

# https://kubernetes.io/docs/getting-started-guides/minikube/
${CMD_KUBECTL} run hello-minikube --image=gcr.io/google_containers/echoserver:1.4 --port=8080
${CMD_KUBECTL} expose deployment hello-minikube --type=NodePort

# Wait for service to be ready
echo "Wait for hello-minikube service to be ready"
HELLO_MINIKUBE_URL=$(${CMD_MINIKUBE} service hello-minikube --url)
while [[ "$(curl -s -o /dev/null -w ''%{http_code}'' ${HELLO_MINIKUBE_URL})" != "200" ]]; do
    sleep 3
done

# Copy service account token
POD_NAME=$(${CMD_KUBECTL} get pod --selector=run=hello-minikube -o jsonpath='{.items..metadata.name}')
${CMD_KUBECTL} exec ${POD_NAME} -- cat /var/run/secrets/kubernetes.io/serviceaccount/token > ${SERVICE_ACCOUNT_TOKEN_FILE}
if [ $? != 0 ] ; then
   echo "Error while retrieving service account token file"
   exit 1
fi

# Copy ca cert
cp $HOME/.minikube/ca.crt work/minikube

#BASEDIR=`dirname $0`/../../..
#sh <(
#cat <<-EOF
#cd ${BASEDIR} && ${BASEDIR}/src/test/bash/env.sh
#vault auth-enable kubernetes
#vault write auth/kubernetes/config kubernetes_host=https://$(minikube ip):8443 kubernetes_ca_cert=@$HOME/.minikube/ca.crt
#EOF
#)
