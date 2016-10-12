#!/bin/bash
if [  -z $(getent passwd opsgenie) ]
then
    groupadd opsgenie -r
    useradd -g opsgenie opsgenie -r -d /var/opsgenie/
fi

if [  -z $(getent group opsgenie) ]
then
  groupadd opsgenie
fi
