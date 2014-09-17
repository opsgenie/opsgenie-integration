if [  -z $(getent group opsgenie) ]
then
  groupadd opsgenie
fi

set check_java_for_marid_only "true"