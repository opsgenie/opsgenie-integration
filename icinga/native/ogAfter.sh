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

if [ -d "/usr/local/icinga/etc/objects" ]; then
    cp /etc/opsgenie/opsgenie.cfg  /usr/local/icinga/etc/objects/
else
        echo "WARNING : Could not find your ICINGA_HOME directory. Please copy /etc/opsgenie/opsgenie.cfg file to your <ICINGA_HOME>/etc/objects directory manually!"
fi