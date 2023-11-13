#!/usr/bin/env bash

set -e
DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null 2>&1 && pwd )"
. ${DIR}/../../version.sh

THIS=$DIR
CONTEXT=`realpath ${DIR}/../..`

docker build  \
    --build-arg VERSION=${version} \
    -t ${remote_tag} \
    --build-context this=${THIS} \
    -f ${DIR}/Dockerfile \
    ${CONTEXT}