if [ ! -d "/tmp/marid" ]; then
    mkdir /tmp/marid
fi

chmod -R 775 /tmp/marid
chmod 755 /usr/bin/icinga2opsgenie

chown -R opsgenie:opsgenie /tmp/marid

if id -u icinga >/dev/null 2>&1; then
        usermod -a -G opsgenie icinga
        chown -R icinga:opsgenie /var/log/opsgenie
elif id -u nagios >/dev/null 2>&1; then
        usermod -a -G opsgenie nagios
        chown -R nagios:opsgenie /var/log/opsgenie
else
        echo "WARNING : Neither icinga nor nagios user exists. Please don't forget to add your icinga or nagios user to opsgenie group!"
fi

if [ -d "/etc/icinga2/conf.d" ]; then
    cp /etc/opsgenie/opsgenie.conf  /etc/icinga2/conf.d/
else
        echo "WARNING : Could not find your icinga conf.d directory. Please copy /etc/opsgenie/opsgenie.conf file to your Icinga conf.d directory manually!"
fi
echo "WARNING : If you're updating the integration from version 1.​*.*​, please update your /etc/opsgenie/conf/opsgenie-integration.conf file accordingly: the old configuration will not work with this version of the integration."
