import com.ifountain.opsgenie.client.http.OpsGenieHttpClient
import com.ifountain.opsgenie.client.util.ClientConfiguration
import org.apache.commons.lang.StringEscapeUtils
import org.apache.http.auth.UsernamePasswordCredentials

LOG_PREFIX = "[${mappedAction}]:";
logger.info("${LOG_PREFIX} Will execute [${mappedAction}] for alertId ${params.alertId}");

CONF_PREFIX = "dynatraceappmon.";
HTTP_CLIENT = createHttpClient();
try {
    String url = params.url
    if (url == null || "".equals(url)) {
        url = _conf("url", true)
    }
    String profileName = params.profileName
    if (profileName == null || "".equals(profileName)) {
        profileName = _conf("profileName", true)
    }

    String incidentRule = params.incidentRule
    String incidentId = params.alias

    String contentParams = createXML()
    logger.debug("XML content: ${contentParams}")

    String resultUrl = url + "/rest/management/profiles/" + profileName + "/incidentrules/" + incidentRule + "/incidents/" + incidentId
    logger.debug("URL: ${resultUrl}")
    def put = ((OpsGenieHttpClient) HTTP_CLIENT).preparePutMethod(resultUrl, contentParams, [:])
    putActionsToDynatraceAppMon(put)

}
catch (Exception e) {
    logger.error(e.getMessage(), e)
}
finally {
    HTTP_CLIENT.close()
}


def putActionsToDynatraceAppMon(def httpMethod) {

    httpMethod.setHeader("Content-Type", "application/xml")
    response = ((OpsGenieHttpClient) HTTP_CLIENT).executeHttpMethod(httpMethod)
    if (response != null) {
        if (response.getStatusCode() < 400) {
            logger.info("${LOG_PREFIX} Successfully executed at Dynatrace AppMon.");
            logger.debug("${LOG_PREFIX} Dynatrace AppMon response: ${response.getStatusCode()}")
        } else {
            logger.error("${LOG_PREFIX} Could not execute at Dynatrace AppMon; response: ${response.statusCode}");
        }
    }

    return response
}

def createXML() {
    def state;
    if (mappedAction == "confirmIncident") {
        state = "Confirmed"
    } else if (mappedAction == "inProgressIncident") {
        state = "InProgress"
    }

    String xmlFormat =
"""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<incident id="${xmlEscape(params.alias)}">        
    <state>${xmlEscape(state)}</state>
</incident>"""

    return xmlFormat

}

def xmlEscape(value) {
    return StringEscapeUtils.escapeXml(value)
}

def createHttpClient() {
    def timeout = _conf("http.timeout", false);
    if (timeout == null) {
        timeout = 30000;
    } else {
        timeout = timeout.toInteger();
    }


    String username = params.userName
    String password = params.password

    if (username == null || "".equals(username)) {
        username = _conf("userName", true)
    }
    if (password == null || "".equals(password)) {
        password = _conf("password", true)
    }
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