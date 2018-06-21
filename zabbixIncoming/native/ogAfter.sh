chmod 755 /etc/opsgenie/zabbix2opsgenie

if id -u zabbix >/dev/null 2>&1; then
        usermod -a -G opsgenie zabbix
        chown -R zabbix:opsgenie /var/log/opsgenie
else
        echo "WARNING : zabbix user does not exist. Please don't forget to add your zabbix user to opsgenie group!"
fi