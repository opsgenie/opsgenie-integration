@echo off
::####################################CONFIGURATION_SETTIGNS###############################
SET LAMP_HOME=C:\lamp
::#########################################################################################


CALL "%LAMP_PATH%\lamp" executeScript --name createAlertWithResults.groovy >> "%SPLUNK_HOME%/bin/scripts/opsgenie_output.txt"
