chmod 755 /usr/local/bin/smarts2opsgenie

if id -u smarts >/dev/null 2>&1; then
        usermod -a -G opsgenie smarts
else
        echo "WARNING : smarts user does not exist. Please don't forget to add your smarts user to opsgenie group!"
fi