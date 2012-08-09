#!/bin/bash

####################################CONFIGURATION_SETTIGNS###############################
LAMP_PATH=lamp_path
export JAVA_HOME=java_home
#########################################################################################
$LAMP_PATH/lamp executeScript --name createAlertWithAttachment.groovy >> "$SPLUNK_HOME/bin/scripts/opsgenie_output.txt"


