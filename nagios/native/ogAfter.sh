chmod 755 /usr/local/bin/nagios2opsgenie
if id -u nagios >/dev/null 2>&1; then
        usermod -a -G opsgenie nagios
else
        echo "-   nagios user does not exist. Please don't forget to add your nagios user to opsgenie group!"
fi

if [ -d "/usr/local/nagios/etc/objects" ]; then
    cp /etc/opsgenie/opsgenie.cfg  /usr/local/nagios/etc/objects/
else
        echo "-   Could not find your NAGIOS_HOME directory. Please copy /etc/opsgenie/opsgenie.cfg file to your <NAGIOS_HOME>/etc/objects directory manually!"
fi