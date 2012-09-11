#!/bin/bash

####################################CONFIGURATION_SETTIGNS###############################
LAMP_HOME=/opt/lamp
#########################################################################################
$LAMP_PATH/lamp executeScript --name createAlertWithResults.groovy >> "$SPLUNK_HOME/bin/scripts/opsgenie_output.txt"


