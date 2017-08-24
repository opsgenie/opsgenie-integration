if [ ! -d "/var/log/opsgenie" ]; then
    mkdir /var/log/opsgenie
fi
chmod -R 665 /var/log/opsgenie
chmod -R g+s /var/log/opsgenie

chmod 755 /usr/local/nagios/libexec/notifications/nagios2opsgenie

if id -u nagios >/dev/null 2>&1; then
        usermod -a -G opsgenie nagios
        chown -R nagios:opsgenie /var/log/opsgenie
else
        echo "WARNING : nagios user does not exist. Please don't forget to add your nagios user to opsgenie group!"
fi
