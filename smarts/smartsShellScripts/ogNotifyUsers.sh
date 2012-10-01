#!/bin/sh

# Specify the location of the lamp utility
LAMP_HOME=/opt/lamp

MESSAGE="Smarts notification: $SM_OBJ_ClassName $SM_OBJ_InstanceDisplayName $SM_OBJ_EventName"
RECIPIENTS="$SM_POBJ_Recipients"
DESCRIPTION="$SM_OBJ_EventText"
ACTIONS="acknowledge,unacknowledge,take ownership,release ownership"

if [[ "$SM_POBJ_Recipients" == "" ]] ; then
     RECIPIENTS="all"
fi

echo "Creating Alert"
$LAMP_HOME/lamp createAlert --message "$MESSAGE" --recipients "$RECIPIENTS" --description "$DESCRIPTION" --source Smarts --alias "$SM_OBJ_Name" --actions "$ACTIONS" -DElementName="$SM_OBJ_ElementName" -DDomain="$SM_OBJ_SourceDomainName" -DCount="$SM_OBJ_OccurrenceCount" -DDomainName="$SM_SERVER_NAME"


