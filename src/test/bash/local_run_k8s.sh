#!/bin/bash

# minikube - https://github.com/kubernetes/minikube/releases
# kubectl -  https://kubernetes.io/docs/tasks/tools/install-kubectl

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

if [ ! -d "work" ]; then
  echo "work directory could not be found."
  exit 1
fi

mkdir -p work/minikube
SERVICE_ACCOUNT_TOKEN_FILE=work/minikube/hello-minikube-token

function is_cluster_running() {
    local _running=$(minikube status | grep "cluster: Running" || true)
    echo "$_running"
}

if [[ -z "$(is_cluster_running)" ]]; then
    minikube start
    while [[ -z "$(is_cluster_running)" ]]; do
        echo "Wait for minikube cluster to be up"
        sleep 1
    done
fi

# https://kubernetes.io/docs/getting-started-guides/minikube/
kubectl run hello-minikube --image=gcr.io/google_containers/echoserver:1.4 --port=8080
kubectl expose deployment hello-minikube --type=NodePort

# Wait for service to be ready
echo "Wait for hello-minikube service to be ready"
HELLO_MINIKUBE_URL=$(minikube service hello-minikube --url)
while [[ "$(curl -s -o /dev/null -w ''%{http_code}'' ${HELLO_MINIKUBE_URL})" != "200" ]]; do
    sleep 3
done

# Copy service account token
POD_NAME=$(kubectl get pod --selector=run=hello-minikube -o jsonpath='{.items..metadata.name}')
kubectl exec ${POD_NAME} -- cat /var/run/secrets/kubernetes.io/serviceaccount/token > ${SERVICE_ACCOUNT_TOKEN_FILE}
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
