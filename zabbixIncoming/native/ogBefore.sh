if [  -z $(getent group opsgenie) ]
then
  groupadd opsgenie
fi