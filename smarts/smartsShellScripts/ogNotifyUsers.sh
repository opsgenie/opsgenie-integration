#!/bin/sh

# Specify the location of the lamp utility
LAMP_HOME=/opt/lamp

export CLASSPATH=$CLASSPATH:$SM_HOME/classes/*

# To use the groovy script (to attach object details, etc.) uncomment the following line and comment out the rest of the script.
# $LAMP_HOME/lamp executeScript --name ogNotifyUsers.groovy

# Comment out rest of the lines if the groovy script above is being used.
MESSAGE="Smarts notification: $SM_OBJ_ClassName $SM_OBJ_InstanceDisplayName $SM_OBJ_EventName"
RECIPIENTS=$SM_POBJ_Recipients
DESCRIPTION=$SM_OBJ_EventText

$LAMP_HOME/lamp createAlert --message $MESSAGE --recipients $RECIPIENTS --description $DESCRIPTION --source Smarts -DElementName=$SM_OBJ_ElementName -DDomain=$SM_OBJ_SourceDomainName -DCount=$SM_OBJ_OccurrenceCount


