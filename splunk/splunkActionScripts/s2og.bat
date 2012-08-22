@echo off
::####################################CONFIGURATION_SETTIGNS###############################
set LAMP_PATH=lamp_path
set JAVA_HOME=java_home
::#########################################################################################


CALL "%LAMP_PATH%\lamp.bat" executeScript --name createAlertWithResults.groovy >> "%SPLUNK_HOME%/bin/scripts/opsgenie_output.txt"
