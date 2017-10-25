#!/bin/bash

MINIKUBE_IP=$(./minikube ip)
if [ $? != 0 ] ; then
   echo "WARNING: Error while getting minikube ip. KubeAuthentication integration tests will be skipped"
fi

echo "MINIKUBE_IP $MINIKUBE_IP"
echo "PROFILE ${PROFILE:-ci}"
./mvnw clean verify -DMINIKUBE_IP=${MINIKUBE_IP} -P${PROFILE:-ci}
