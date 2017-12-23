if [ ! -d "/tmp/marid" ]; then
    mkdir /tmp/marid
fi

chmod -R 775 /tmp/marid
chmod 755 /usr/bin/bmcFootPrints2opsgenie

chown -R opsgenie:opsgenie /tmp/marid
