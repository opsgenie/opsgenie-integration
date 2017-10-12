import com.ifountain.opsgenie.client.http.OpsGenieHttpClient
import com.ifountain.opsgenie.client.util.ClientConfiguration
import com.ifountain.opsgenie.client.util.JsonUtils

LOG_PREFIX = "[${mappedAction}]:";
logger.info("${LOG_PREFIX} Will execute [${mappedAction}] for alertId ${params.alertId}");

CONF_PREFIX = "prtg.";
HTTP_CLIENT = createHttpClient();
try {
    String url = params.prtgUrl
    String ackMessage = params.acknowledgeMessage
    String id = params.sensorId
    if (!url) {
        url = _conf("prtgUrl", true)
    }

    String username = params.username
    String passhash = params.passhash

    if (!username) {
        username = _conf("username", true)
    }
    if (!passhash) {
        passhash = _conf("passhash", true)
    }

    String prtgPath = "/api/acknowledgealarm.htm"
    if (url.endsWith("/")) {
        prtgPath = "api/acknowledgealarm.htm"
    }
    String resultUrl = url + prtgPath
    requestParameters = [:]
    if (mappedAction == "acknowledgeSensor") {
        requestParameters.id = id
        requestParameters.ackmsg = ackMessage
        requestParameters.username = username
        requestParameters.passhash = passhash
    }
    doPostRequestToPrtg(resultUrl, requestParameters)
}
catch (Exception e) {
    logger.error(e.getMessage(), e)
}
finally {
    HTTP_CLIENT.close()
}
void doPostRequestToPrtg(String url, Map requestParameters){
    logger.debug("Request getting ready for post ${url} with Request Params: ${requestParameters.toMapString()} ")
    def response = HTTP_CLIENT.post(url, JsonUtils.toJson([:]), [:], requestParameters)
    logger.debug("Status code of the response: ${response.getStatusCode()}")
    logger.debug("Response content: ${response.getContent()}")
    if (response.getStatusCode() < 400) {
        logger.info("${LOG_PREFIX} Successfully executed at Prtg.")
    } else {
        logger.error("${LOG_PREFIX} Could not execute at Prtg; response: ${response.statusCode} ${response.getContentAsString()}")
    }
}

def createHttpClient() {
    def timeout = _conf("http.timeout", false);
    if (timeout == null) {
        timeout = 30000;
    } else {
        timeout = timeout.toInteger();
    }

    ClientConfiguration clientConfiguration = new ClientConfiguration().setSocketTimeout(timeout)
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