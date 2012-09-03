#!/bin/sh

# Specify the location of the lamp utility
LAMP_HOME=/opt/lamp

MESSAGE="Smarts notification: $SM_OBJ_ClassName $SM_OBJ_InstanceDisplayName $SM_OBJ_EventName"
RECIPIENTS="$SM_POBJ_Recipients"
DESCRIPTION="$SM_OBJ_EventText"
ACTIONS="acknowledge,unacknowledge,take ownership,release ownership"

if [[ -n "$SM_POBJ_Recipients" ]] ; then
     RECIPIENTS="all"
fi

$LAMP_HOME/lamp createAlert --message "$MESSAGE" --recipients "$RECIPIENTS" --description "$DESCRIPTION" --source Smarts --actions "$ACTIONS" -DElementName="$SM_OBJ_ElementName" -DDomain="$SM_OBJ_SourceDomainName" -DCount="$SM_OBJ_OccurrenceCount" -DNotificationName="$SM_OBJ_Name" -DDomainName="$SM_SERVER_NAME"


