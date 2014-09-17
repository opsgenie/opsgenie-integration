if [  -z $(getent group opsgenie) ]
then
  groupadd opsgenie
fi

check_java_for_marid_only = "true"