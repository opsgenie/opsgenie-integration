@echo off
::####################################CONFIGURATION_SETTIGNS###############################
set CUSTOMER_KEY=customer_key
set RECIPIENTS=comma_separated_recipient_list
set LAMP_PATH=lamp_path
set JAVA_HOME=java_home
::#########################################################################################

::###################################PARAMETER PASSED BY SPLUNK############################
::SPLUNK_ARG_0 Script name
::SPLUNK_ARG_1 Number of events returned
::SPLUNK_ARG_2 Search terms
::SPLUNK_ARG_3 Fully qualified query string
::SPLUNK_ARG_4 Name of saved search
::SPLUNK_ARG_5 Trigger reason (for example, "The number of events was greater than 1")
::SPLUNK_ARG_6 Browser URL to view the saved search
::SPLUNK_ARG_8 File in which the results for this search are stored (contains raw results)
::#########################################################################################


CALL "%LAMP_PATH%\lamp.bat" createAlert --customerKey %CUSTOMER_KEY% --message "%SPLUNK_ARG_4% %SPLUNK_ARG_5%" --source splunk ^
--recipients %RECIPIENTS% -DeventCount=%SPLUNK_ARG_1% ^
-DsearchTerms=%SPLUNK_ARG_2% ^
-DqueryString=%SPLUNK_ARG_3% ^
-DbrowserUrl=%SPLUNK_ARG_6% >> "%SPLUNK_HOME%/bin/scripts/opsgenie_output.txt"
