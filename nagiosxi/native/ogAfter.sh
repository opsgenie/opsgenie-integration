chmod 755 /usr/local/bin/nagios2opsgenie
if id -u nagios >/dev/null 2>&1; then
        usermod -a -G opsgenie nagios
else
        echo "-   nagios user does not exist. Please don't forget to add your nagios user to opsgenie group!"
fi