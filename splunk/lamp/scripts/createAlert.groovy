numberOfEvents = System.getenv("SPLUNK_ARG_1");
searchTerms = System.getenv("SPLUNK_ARG_2");
queryString = System.getenv("SPLUNK_ARG_3");
nameOfSearch = System.getenv("SPLUNK_ARG_4");
reason = System.getenv("SPLUNK_ARG_5");
browserUrl = System.getenv("SPLUNK_ARG_6");
rawFilePath = System.getenv("SPLUNK_ARG_8");

def alertProps = [:]
alertProps.message = nameOfSearch + " has " + numberOfEvents + " events"
alertProps.recipients = "Operations"
alertProps.details = [:]
alertProps.details.searchTerms = searchTerms
alertProps.details.browserUrl = browserUrl
alertProps.details.queryString = queryString
alertProps.details.numberOfEvents = numberOfEvents
alertProps.details.nameOfSearch = nameOfSearch

logger.warn("Creating alert with message ${alertProps.message}");
def response = opsgenie.createAlert(alertProps)
def alertId =  response.alertId;
logger.warn("Alert is created with id :"+alertId);