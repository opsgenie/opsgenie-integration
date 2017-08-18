#!/bin/bash

if [ ! -d "/var/log/opsgenie" ]; then
    mkdir /var/log/opsgenie
fi

chmod -R 775 /var/log/opsgenie
chmod -R g+s /var/log/opsgenie
chmod -R 775 /etc/opsgenie

chown -R opsgenie:opsgenie /etc/opsgenie
chown -R opsgenie:opsgenie /var/log/opsgenie

chmod 755 /etc/opsgenie/vcsa2opsgenie


