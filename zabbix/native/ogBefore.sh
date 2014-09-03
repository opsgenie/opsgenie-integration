if [ $(getent group opsgenie) ]
then
  groupadd opsgenie
fi
