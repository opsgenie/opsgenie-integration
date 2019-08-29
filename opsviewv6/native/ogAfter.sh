if [ ! -d "/var/log/opsgenie" ]; then
    mkdir /var/log/opsgenie
fi
chmod -R 775 /var/log/opsgenie
chmod -R g+s /var/log/opsgenie

chmod 755 /opt/opsview/monitoringscripts/notifications/nagios2opsgenie

if id -u opsview >/dev/null 2>&1; then
        usermod -a -G opsgenie opsview
        chown -R opsview:opsgenie /var/log/opsgenie
else
        echo "WARNING : opsview user does not exist. Please don't forget to add your opsview user to opsgenie group!"
fi
