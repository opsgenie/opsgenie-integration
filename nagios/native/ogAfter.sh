chmod 755 /usr/local/bin/nagios2opsgenie
usermod -a -G opsgenie nagios
if [ -d "/usr/local/nagios/etc/objects" ]; then
    cp /etc/opsgenie/opsgenie.cfg  /usr/local/nagios/etc/objects/
fi