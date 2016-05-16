import com.ifountain.opsgenie.client.http.OpsGenieHttpClient
import com.ifountain.opsgenie.client.util.ClientConfiguration
import org.apache.http.auth.UsernamePasswordCredentials

import java.text.SimpleDateFormat

CONF_PREFIX = "solarwinds."
LOG_PREFIX = "[${action}]:"
logger.warn("${LOG_PREFIX} Alert: AlertId:[${alert.alertId}] Note:[${alert.note}] Source: [${source}]");

alertFromOpsgenie = opsgenie.getAlert(alertId: alert.alertId)
HTTP_CLIENT = createHttpClient()
try {
    if (alertFromOpsgenie.size() > 0) {
        // If the action originated in Solarwinds, don't process it here
        if(source.type?.toLowerCase() != "solarwinds") {
            String definitionID = alertFromOpsgenie.details.AlertDefinitionID;
            logger.debug("alerDefinitionID: ${definitionID}")
            String objectType = alertFromOpsgenie.details.ObjectType;
            logger.debug("objectType: ${objectType}")
            String objectID = alertFromOpsgenie.details.ObjectID;
            logger.debug("objectID: ${objectID}")

            def dateFormat = new SimpleDateFormat("MM-dd-yyyy HH:mm:ss")
            String strUpdated = dateFormat.format(new Date())

            String message = "";
            String comment = "";

            if (action == "Acknowledge") {
                message = alert.username + " acknowledged alert: \"" + alert.note + "\" on alert: \"" + alert.message + "\"";
                acknowledgeSolarwindsAlert(definitionID, objectType, objectID, "${strUpdated} Acknowledged in OpsGenie by ${alert.username}");
            } else if (action == "AddNote") {
                message = alert.username + " added note to alert: \"" + alert.note + "\" on alert: \"" + alert.message + "\"";
                comment = "${strUpdated} Updated by " + alert.username + " from OpsGenie: " + alert.note;
                addNoteSolarwindsAlert(definitionID, objectType, objectID, comment);
            } else{
                message = alert.username + " executed [" + action + "] action on alert: \"" + alert.message + "\"";
            }

            logger.info("${LOG_PREFIX} ${message}");
        } else{
            logger.warn("${LOG_PREFIX} Action source is Solarwinds; discarding action in order to prevent looping.");
        }
    } else {
        logger.warn("${LOG_PREFIX} Alert with id [${alert.alertId}] does not exist in OpsGenie. It is probably deleted.");
    }
}
finally {
    HTTP_CLIENT.close();
}

void acknowledgeSolarwindsAlert(String definitionID, String objectType, String objectID, String comment) {
    String url = _conf("url", true) + "/SolarWinds/InformationService/v3/Json/Invoke/Orion.AlertStatus/Acknowledge"
    def requestParameters = [:]
    def requestHeaders = ["Content-Type":"application/json"];

    def contentArray = [
            [
                    [
                            "DefinitionID":definitionID,
                            "ObjectType":objectType,
                            "ObjectID":objectID
                    ]
            ]
    ]
    def builder = new groovy.json.JsonBuilder(contentArray);
    String content = builder.toString();

    logger.warn ("Acknowledgement details: ${content}");

    def post = ((OpsGenieHttpClient) HTTP_CLIENT).preparePostMethod(url, content, requestHeaders, requestParameters);
    sendHttpRequestToSolarwinds(post);

    addNoteSolarwindsAlert(definitionID, objectType, objectID, comment);

}

void addNoteSolarwindsAlert(String definitionID, String objectType, String objectID, String comment) {
    String url = _conf("url", true) + "/SolarWinds/InformationService/v3/Json/Invoke/Orion.AlertStatus/AddNote";
    def requestParameters = [:];
    def requestHeaders = ["Content-Type":"application/json"];

    contentList = [definitionID, objectID, objectType, comment]
    def builder = new groovy.json.JsonBuilder(contentList);
    String content = builder.toString();

    logger.warn ("AddNote details: ${content}");

    def post = ((OpsGenieHttpClient) HTTP_CLIENT).preparePostMethod(url, content, requestHeaders, requestParameters);
    sendHttpRequestToSolarwinds(post);
}

void sendHttpRequestToSolarwinds(def httpMethod) {
    httpMethod.setHeader("Content-Type", "application/json")
    def response = ((OpsGenieHttpClient) HTTP_CLIENT).executeHttpMethod(httpMethod)
    if (response.getStatusCode() < 299) {
        logger.info("${LOG_PREFIX} Successfully executed at Solarwinds.");
        logger.debug("${LOG_PREFIX} Solarwinds response: ${response.statusCode} ${response.getContentAsString()}")
    } else {
        logger.warn("${LOG_PREFIX} Could not execute at Solarwinds; response: ${response.statusCode} ${response.getContentAsString()}")
    }
}

def createHttpClient() {
    def timeout = _conf("http.timeout", false);
    if (timeout == null) {
        timeout = 30000;
    } else {
        timeout = timeout.toInteger();
    }

    String username = _conf("login", true)
    String password = _conf("password", true)

    ClientConfiguration clientConfiguration = new ClientConfiguration().setSocketTimeout(timeout);
    clientConfiguration.setCredentials(new UsernamePasswordCredentials(username, password));

    return new OpsGenieHttpClient(clientConfiguration);
}

def _conf(confKey, boolean isMandatory) {
    def confVal = conf[CONF_PREFIX + confKey]
    if (isMandatory && confVal == null) {
        def errorMessage = "${LOG_PREFIX} Skipping action, Mandatory Conf item ${CONF_PREFIX + confKey} is missing. Check your marid conf file.";
        throw new Exception(errorMessage);
    }
    return confVal
}

return;
