chmod 755 /usr/bin/zenoss2opsgenie

if id -u zenoss >/dev/null 2>&1; then
        usermod -a -G opsgenie zenoss
        chown -R zenoss:opsgenie /var/log/opsgenie
else
        echo "WARNING : zenoss user does not exist. Please don't forget to add your zenoss user to opsgenie group!"
fi