numberOfEvents = ENV['SPLUNK_ARG_1'];
searchTerms = ENV['SPLUNK_ARG_2'];
queryString = ENV['SPLUNK_ARG_3'];
nameOfSearch = ENV['SPLUNK_ARG_4'];
reason = ENV['SPLUNK_ARG_5'];
browserUrl = ENV['SPLUNK_ARG_6'];
rawFilePath = ENV['SPLUNK_ARG_8'];

alertProps = {}
alertProps.message = "#{nameOfSearch} has ${numberOfEvents} events"
alertProps.recipients = "Operations"
alertProps["details"] = {}
alertProps["details"]["searchTerms"] = searchTerms
alertProps["details"]["browserUrl"] = browserUrl
alertProps["details"]["queryString"] = queryString
alertProps["details"]["numberOfEvents"] = numberOfEvents
alertProps["details"]["nameOfSearch"] = nameOfSearch

$logger.warn("Creating alert with message #{alertProps.message}");
response = $opsgenie.createAlert(alertProps)
$logger.warn("Alert is created with id #{response['alertId']}");