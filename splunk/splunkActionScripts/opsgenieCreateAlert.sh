#!/bin/bash

####################################CONFIGURATION_SETTIGNS###############################
CUSTOMER_KEY=customer_key
RECIPIENTS=comma_separated_recipient_list
LAMP_PATH=lamp_path
export JAVA_HOME=java_home
#########################################################################################
###################################PARAMETER PASSED BY SPLUNK############################
#SPLUNK_ARG_0 Script name
#SPLUNK_ARG_1 Number of events returned
#SPLUNK_ARG_2 Search terms
#SPLUNK_ARG_3 Fully qualified query string
#SPLUNK_ARG_4 Name of saved search
#SPLUNK_ARG_5 Trigger reason (for example, "The number of events was greater than 1")
#SPLUNK_ARG_6 Browser URL to view the saved search
#SPLUNK_ARG_8 File in which the results for this search are stored (contains raw results)
#########################################################################################

$LAMP_PATH/lamp createAlert --customerKey $CUSTOMER_KEY --message "$SPLUNK_ARG_4 $SPLUNK_ARG_5" --source splunk \
--recipients $RECIPIENTS -DeventCount=$SPLUNK_ARG_1 \
-DsearchTerms=$SPLUNK_ARG_2 \
-DqueryString =$SPLUNK_ARG_3 \
-DbrowserUrl=$SPLUNK_ARG_6 >> "$SPLUNK_HOME/bin/scripts/opsgenie_output.txt"


