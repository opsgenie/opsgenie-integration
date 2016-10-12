#!/bin/bash

if [ ! -d "/var/log/opsgenie" ]; then
    mkdir /var/log/opsgenie
fi

chmod -R 775 /var/log/opsgenie
chmod -R g+s /var/log/opsgenie
chmod -R 775 /etc/opsgenie

chown -R opsgenie:opsgenie /etc/opsgenie
chown -R opsgenie:opsgenie /var/log/opsgenie

chmod 755 /etc/opsgenie/oem2opsgenie

if id -u oracle >/dev/null 2>&1; then
        usermod -a -G opsgenie oracle
        chown -R oracle:opsgenie /var/log/opsgenie
else
        echo "WARNING : oracle user does not exist. Please don't forget to add your oracle user to opsgenie group!"
fi
