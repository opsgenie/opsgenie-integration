if [  -z $(getent group opsgenie) ]
then
  groupadd opsgenie
fi

%define check_java_for_marid_only "true"