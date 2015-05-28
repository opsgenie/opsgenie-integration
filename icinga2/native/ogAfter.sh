if [ ! -d "/tmp/marid" ]; then
    mkdir /tmp/marid
fi

chmod -R 775 /tmp/marid
chmod 755 /usr/bin/icinga2opsgenie

chown -R opsgenie:opsgenie /tmp/marid

if id -u icinga >/dev/null 2>&1; then
        usermod -a -G opsgenie icinga
        chown -R icinga:opsgenie /var/log/opsgenie
else
        echo "WARNING : icinga user does not exist. Please don't forget to add your icinga user to opsgenie group!"
fi

if [ -d "/etc/icinga2/conf.d" ]; then
    cp /etc/opsgenie/opsgenie.conf  /etc/icinga2/conf.d/
else
        echo "WARNING : Could not find your icinga conf.d directory. Please copy /etc/opsgenie/opsgenie.conf file to your Icinga conf.d directory manually!"
fi