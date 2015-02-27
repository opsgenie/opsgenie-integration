import com.ifountain.opsgenie.client.http.OpsGenieHttpClient
import com.ifountain.opsgenie.client.util.ClientConfiguration
import org.apache.http.auth.UsernamePasswordCredentials

LOG_PREFIX = "[${action}]:";
logger.warn("${LOG_PREFIX} Will execute action for alertId ${alert.alertId}");

CONF_PREFIX = "zendesk.";
alertFromOpsgenie = opsgenie.getAlert(alertId: alert.alertId)
HTTP_CLIENT = createHttpClient();
try {
    if (alertFromOpsgenie.size() > 0) {
        String ticketId = alertFromOpsgenie.details.ticket_id;
        if (ticketId) {
            String message;
            if (action == "Acknowledge") {
                message = alert.username + " acknowledged alert: \"" + alert.message + "\"";
            } else if (action == "AddNote") {
                message = alert.username + " noted: \"" + alert.note + "\" on alert: \"" + alert.message + "\"";
            } else if (action == "AddRecipient") {
                message = alert.username + " added recipient " + alert.recipient + " to alert: \"" + alert.message + "\"";
            } else if (action == "AddTeam") {
                message = alert.username + " added team " + alert.team + " to alert: \"" + alert.message + "\"";
            } else if (action == "AssignOwnership") {
                message = alert.username + " assigned ownership of the alert: \"" + alert.message + "\" to " + alert.owner;
            } else if (action == "Close") {
                message = alert.username + " closed alert: \"" + alert.message + "\"";
            } else if (action == "TakeOwnership") {
                message = alert.username + " took ownership of the alert: \"" + alert.message + "\"";
            }
            addCommentToTicketInZendesk(message, ticketId)
        } else {
            logger.warn("${LOG_PREFIX} Cannot send action to Zendesk because ticket_id is not found on alert")
        }
    } else {
        logger.warn("${LOG_PREFIX} Alert with id [${alert.alertId}] does not exist in OpsGenie. It is probably deleted.")
    }
}
finally {
    HTTP_CLIENT.close()
}

void addCommentToTicketInZendesk(String message, String ticketId) {
    String url = _conf("url", true) + "/api/v2/tickets/${ticketId}.json"
    requestParameters = [:]
    requestParameters.key = _conf("apiToken", true)
    def contentParams = [
            "ticket": [
                    "comment": [
                            "body"  : "OpsGenie: " + message,
                            "public": false
                    ]
            ]
    ]

    def put = ((OpsGenieHttpClient) HTTP_CLIENT).preparePutMethod(url, contentParams, [:])
    sendHttpRequestToZendesk(put)
}

void sendHttpRequestToZendesk(def httpMethod) {
    httpMethod.setHeader("Content-Type", "application/json")
    response = ((OpsGenieHttpClient) HTTP_CLIENT).executeHttpMethod(httpMethod)
    if (response.getStatusCode() < 299) {
        logger.info("${LOG_PREFIX} Successfully executed at Zendesk.");
        logger.debug("${LOG_PREFIX} Zendesk response: ${response.statusCode} ${response.getContentAsString()}")
    } else {
        logger.warn("${LOG_PREFIX} Could not execute at Zendesk; response: ${response.statusCode} ${response.getContentAsString()}")
    }
}

def createHttpClient() {
    def timeout = _conf("http.timeout", false);
    if (timeout == null) {
        timeout = 30000;
    } else {
        timeout = timeout.toInteger();
    }

    String username = _conf("email", true) + "/token"
    String password = _conf("apiToken", true)
    ClientConfiguration clientConfiguration = new ClientConfiguration().setSocketTimeout(timeout)
            .setCredentials(new UsernamePasswordCredentials(username, password))
    return new OpsGenieHttpClient(clientConfiguration)
}

def _conf(confKey, boolean isMandatory) {
    def confVal = conf[CONF_PREFIX + confKey]
    if (isMandatory && confVal == null) {
        def errorMessage = "${LOG_PREFIX} Skipping action, Mandatory Conf item ${CONF_PREFIX + confKey} is missing. Check your marid conf file.";
        throw new Exception(errorMessage);
    }
    return confVal
}