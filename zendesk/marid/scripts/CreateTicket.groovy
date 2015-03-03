import com.ifountain.opsgenie.client.http.OpsGenieHttpClient
import com.ifountain.opsgenie.client.util.ClientConfiguration
import com.ifountain.opsgenie.client.util.JsonUtils
import org.apache.http.auth.UsernamePasswordCredentials

LOG_PREFIX = "[${action}]:";
logger.warn("${LOG_PREFIX} Will create ticket for alertId ${alert.alertId}");

CONF_PREFIX = "zendesk.";

def alertFromOpsgenie = opsgenie.getAlert(alertId: alert.alertId)

if (alertFromOpsgenie.size() > 0) {
    if (source.name?.toLowerCase() != "zendesk") {
        createTicketInZendesk(alertFromOpsgenie)
    } else {
        logger.warn("${LOG_PREFIX} Action source is Zendesk; discarding action in order to prevent looping.")
    }
} else {
    logger.warn("${LOG_PREFIX} Alert with id [${alert.alertId}] does not exist in OpsGenie. It is probably deleted.")
}

void createTicketInZendesk(def alertFromOpsgenie) {
    OpsGenieHttpClient HTTP_CLIENT = createHttpClient();
    try {
        String url = _conf("url", true) + "/api/v2/tickets.json"
        requestParameters = [:]
        requestParameters.key = _conf("apiToken", true)
        def contentParams = [
            "ticket" : [
                "subject" : alert.message,
                "comment" : [
                        "body" : String.valueOf("Ticket created by ${alert.username}\n" +
                                        "Description: ${alertFromOpsgenie.description}"),
                        "public" : false
                ],
                "tags" : alert.tags
            ]
        ]

        def post = ((OpsGenieHttpClient) HTTP_CLIENT).preparePostMethod(url, JsonUtils.toJson(contentParams), [:], requestParameters)
        sendHttpRequestToZendesk(post, HTTP_CLIENT)
    } finally {
        HTTP_CLIENT.close();
    }
}

void sendHttpRequestToZendesk(def httpMethod, OpsGenieHttpClient HTTP_CLIENT) {
    httpMethod.setHeader("Content-Type", "application/json")
    response = HTTP_CLIENT.executeHttpMethod(httpMethod)
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