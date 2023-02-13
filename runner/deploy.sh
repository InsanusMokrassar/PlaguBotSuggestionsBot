#!/bin/bash

function send_notification() {
    echo "$1"
}

function assert_success() {
    "${@}"
    local status=${?}
    if [ ${status} -ne 0 ]; then
        send_notification "### Error ${status} at: ${BASH_LINENO[*]} ###"
        exit ${status}
    fi
}

app=suggestionsbot
version="`grep ../gradle.properties -e "^docker_version=" | grep -e "[0-9.]*" -o`"
username=insanusmokrassar

assert_success ../gradlew build
assert_success sudo docker build -t $app:"$version" .
assert_success sudo docker tag $app:"$version" $username/$app:$version
assert_success sudo docker tag $app:"$version" $username/$app:latest
assert_success sudo docker push $username/$app:$version
assert_success sudo docker push $username/$app:latest
