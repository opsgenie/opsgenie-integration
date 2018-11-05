import com.ifountain.opsgenie.client.http.OpsGenieHttpClient
import com.ifountain.opsgenie.client.util.ClientConfiguration
import com.ifountain.opsgenie.client.util.JsonUtils
import org.apache.http.HttpHeaders

LOG_PREFIX = "[${action}]:";
logger.warn("${LOG_PREFIX} Will execute action for alertId ${alert.alertId}");

CONF_PREFIX = "zabbix.";

def alertFromOpsgenie = opsgenie.getAlert(alertId: alert.alertId)
if (alertFromOpsgenie.size() > 0) {

    if (action == "Acknowledge") {
        if (source != null && source.name?.toLowerCase() == "zabbix") {
            logger.warn("OpsGenie alert is already acknowledged by zabbix. Discarding!!!");
        } else {
            def postParams = [:]
            postParams.jsonrpc = "2.0"
            postParams.method = "event.acknowledge"
            def eventId = alertFromOpsgenie.details.eventId?.value
            postParams.params = [
                    "eventids": eventId,
                    "message" : "Acknowledged by ${alert.username} via OpsGenie".toString(),
                    "action" : 6
            ]
            postParams.id = 1

            postToZabbix(postParams);
        }
    }

} else {
    logger.warn("${LOG_PREFIX} Alert with id [${alert.alertId}] does not exist in OpsGenie. It is probably deleted.")
}

def postToZabbix(Map<String, String> postParams) {
    OpsGenieHttpClient HTTP_CLIENT = createHttpClient();
    try {
        String url = _conf("command_url", true)

        String auth = loginToZabbix(HTTP_CLIENT, url)
        if (auth) {
            postParams.auth = auth

            logger.debug("${LOG_PREFIX} Posting to Zabbix. Url ${url} params:${postParams}")

            Map contentTypeHeader = [:]
            contentTypeHeader[HttpHeaders.CONTENT_TYPE] = "application/json"
            def response = HTTP_CLIENT.post(url, JsonUtils.toJson(postParams), contentTypeHeader)
            def responseMap = JsonUtils.parse(response.content)
            if (responseMap.error) {
                logger.error("${LOG_PREFIX} Could not execute at Zabbix; reason:${responseMap.error.data}");
            } else {
                logger.info("${LOG_PREFIX} Successfully executed at Zabbix.");
                logger.debug("${LOG_PREFIX} Zabbix response: ${response.getContentAsString()}")
            }
        }
        else
            logger.error("${LOG_PREFIX} Could not execute at Zabbix because no auth key was retrieved.");
    }
    finally {
        HTTP_CLIENT.close();
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
        logger.warn(errorMessage);
        throw new Exception(errorMessage);
    }
    return confVal
}

String loginToZabbix(OpsGenieHttpClient HTTP_CLIENT, String url) {
    def loginParams = [:]
    loginParams.jsonrpc = "2.0"
    loginParams.method = "user.login"
    def user = _conf("user", true)
    def password = _conf("password", true)
    loginParams.params = ["user": user, "password": password]
    loginParams.id = 1
    logger.debug("${LOG_PREFIX} Logging in to Zabbix. Url ${url} user:${user}")

    Map contentTypeHeader = [:]
    contentTypeHeader[HttpHeaders.CONTENT_TYPE] = "application/json"
    def response = HTTP_CLIENT.post(url, JsonUtils.toJson(loginParams), contentTypeHeader)
    logger.debug("${LOG_PREFIX} login response:${response.statusCode} ${response.getContentAsString()}")
    def responseMap = JsonUtils.parse(response.content)
    if (responseMap.error)
        logger.error("${LOG_PREFIX} Cannot login to zabbix; response:${responseMap.error.data}");
    else
        logger.info("${LOG_PREFIX} Login to zabbix is successful");
    return responseMap.result
}