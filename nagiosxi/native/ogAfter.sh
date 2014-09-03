if [ ! -d "/tmp/marid" ]; then
    mkdir /tmp/marid
fi

chmod -R 775 /tmp/marid
chmod 755 /usr/local/bin/nagios2opsgenie

chown -R opsgenie:opsgenie /tmp/marid

if id -u nagios >/dev/null 2>&1; then
        usermod -a -G opsgenie nagios
else
        echo "WARNING : nagios user does not exist. Please don't forget to add your nagios user to opsgenie group!"
fi