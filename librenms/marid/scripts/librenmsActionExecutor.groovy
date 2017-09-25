import com.ifountain.opsgenie.client.http.OpsGenieHttpClient
import com.ifountain.opsgenie.client.util.ClientConfiguration
import com.ifountain.opsgenie.client.util.JsonUtils

LOG_PREFIX = "[${mappedAction}]:";
logger.info("${LOG_PREFIX} Will execute [${mappedAction}] for alertId ${params.alertId}");

CONF_PREFIX = "librenms.";
HTTP_CLIENT = createHttpClient();
try {
    String url = params.url

    if (!url) {
        url = _conf("url", true)
    }

    if (url.endsWith("/") && url.length() >= 2) {
        url = url.substring(0, url.length() - 2)
    }

    String apiToken = params.apiToken

    if (!apiToken) {
        apiToken = _conf("apiToken", true)
    }

    String rule = params.rule

    def listRulesEndPoint = url + "/api/v0/rules"

    logger.debug("Sending GET request to " + listRulesEndPoint)

    def listRulesResponse = HTTP_CLIENT.get(listRulesEndPoint, [:], ["X-Auth-Token": apiToken])

    logger.debug("Response from " + listRulesEndPoint + ": " + listRulesResponse.getContentAsString() +
            " Status Code: " + listRulesResponse.getStatusCode())

    if (listRulesResponse.getStatusCode() < 400) {
        def listRulesResponseMap = JsonUtils.parse(listRulesResponse.getContentAsString())
        def rules = listRulesResponseMap.get("rules")

        def ruleId = rules.findResult { it ->
            it.get("rule").trim().replace("\\\"", "\"") == rule.trim() ? it.get("id") : null
        }

        logger.debug("Rule Id from LibreNMS: " + ruleId)

        if (ruleId != null) {
            def listAlertsEndPoint = url + "/api/v0/alerts"

            def listAlertsResponse

            if (mappedAction == "ackAlert") {
                def queryParams = ["state":"1"]

                logger.debug("Sending GET request to " + listAlertsEndPoint + " with parameters: " + queryParams.toMapString())
                listAlertsResponse = HTTP_CLIENT.get(listAlertsEndPoint, queryParams, ["X-Auth-Token": apiToken])
            } else if (mappedAction == "unmuteAlert") {
                def queryParams = ["state":"2"]

                logger.debug("Sending GET request to " + listAlertsEndPoint + " with parameters: " + queryParams.toMapString())
                listAlertsResponse = HTTP_CLIENT.get(listAlertsEndPoint, queryParams, ["X-Auth-Token": apiToken])
            }

            logger.debug("Response from " + listAlertsEndPoint + ": " + listAlertsResponse.getContentAsString() +
                    " Status Code: " + listAlertsResponse.getStatusCode())

            if (listAlertsResponse.getStatusCode() < 400) {
                def listAlertsResponseMap = JsonUtils.parse(listAlertsResponse.getContentAsString())
                def alerts = listAlertsResponseMap.get("alerts")

                def alertId = alerts.findResult { it ->
                    it.get("rule_id") == ruleId ? it.get("id") : null
                }

                logger.debug("Alert Id from LibreNMS: " + alertId)

                if (alertId != null) {
                    if (mappedAction == "ackAlert") {
                        url = url + "/api/v0/alerts/" + alertId
                    } else if (mappedAction == "unmuteAlert") {
                        url = url + "/api/v0/alerts/unmute/" + alertId
                    }

                    def putMethod = HTTP_CLIENT.preparePutMethod(url, [:], [:])
                    putMethod.setHeader("X-Auth-Token", apiToken)

                    logger.debug("Sending PUT request to " + url)

                    def response = HTTP_CLIENT.executeHttpMethod(putMethod)

                    logger.debug("Response from " + url + ": " + response.getContentAsString() +
                            " Status Code: " + response.getStatusCode())

                    if (response.getStatusCode() < 400) {
                        logger.info("${LOG_PREFIX} Successfully executed at LibreNMS.")
                        logger.debug("${LOG_PREFIX} LibreNMS response: ${response.getContentAsString()}")
                    } else {
                        logger.error("${LOG_PREFIX} Could not execute at LibreNMS; response: ${response.statusCode} ${response.getContentAsString()}")
                    }
                } else {
                    logger.error("${LOG_PREFIX} alertId from the LibreNMS API was null.")
                }
            } else {
                logger.error("${LOG_PREFIX} Could not get alert list from LibreNMS; response: ${response.statusCode} ${response.getContentAsString()}")
            }
        } else {
            logger.error("${LOG_PREFIX} ruleId from the LibreNMS API was null.")
        }
    } else {
        logger.error("${LOG_PREFIX} Could not get rules list from LibreNMS; response: ${response.statusCode} ${response.getContentAsString()}")
    }
}
catch (Exception e) {
    logger.error(e.getMessage(), e)
}
finally {
    HTTP_CLIENT.close()
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