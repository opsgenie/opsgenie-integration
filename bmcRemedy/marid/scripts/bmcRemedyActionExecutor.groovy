import wslite.soap.*

LOG_PREFIX = "[${mappedAction}]:";
logger.warn("${LOG_PREFIX} Will execute [${mappedAction}] for alertId ${params.alertId}");

CONF_PREFIX = "bmcRemedy.";

NAMESPACE_PREFIX = "urn:"
username = params.username
password = params.password
midtierServerUrl = params.midtierServerUrl
serverName = params.serverName
incidentNumber = params.incidentNumber
workInfoDetails = params.workInfoDetails




try {
    parseParameters()

    if (mappedAction == "addWorkInfo") {
        makeAddWorkInfoSOAPRequest('SOAP-OpsGenie [Add Work Info]')
    }
}
catch (Exception e) {
    logger.error(e.getMessage(), e)
}


def parseParameters() {
    username = parseConfigIfNullOrEmpty(username, "username")
    logger.debug("Username: ${username}")
    password = parseConfigIfNullOrEmpty(password, "password")
    logger.debug("Password: ${password}")
    midtierServerUrl = parseConfigIfNullOrEmpty(midtierServerUrl, "midtierServerUrl")
    logger.debug("Mid-Tier Server: ${midtierServerUrl}")
    serverName = parseConfigIfNullOrEmpty(serverName, "serverName")
    logger.debug("Server Name ${serverName}")
}

def makeAddWorkInfoSOAPRequest(String soapAction) {
    SOAPClient client = new SOAPClient(getSOAPEndpoint())

    def response = client.send(SOAPAction: soapAction) {
        soapNamespacePrefix "soapenv"
        envelopeAttributes('xmlns:urn': 'urn:HPD_IncidentServiceInterface')
        header {
            NAMESPACE_PREFIX + 'AuthenticationInfo' {
                NAMESPACE_PREFIX + 'userName'(username)
                'urn:password'(password)
            }
        }
        body {
            NAMESPACE_PREFIX + 'Process_Event' {
                NAMESPACE_PREFIX + 'Action'("PROCESS_EVENT")
                NAMESPACE_PREFIX + 'Incident_Number'(incidentNumber)
                NAMESPACE_PREFIX + 'Work_Info_Details'(workInfoDetails)
            }
        }
    }

    if (300 > response.httpResponse.statusCode) {
        logger.info("SOAP Request sent successfully.")
        logger.debug("${response.httpResponse.statusCode}")
        logger.debug("${response.httpResponse.getContentAsString()}")
    } else {
        logger.error("SOAP Request failed with status code:  ${response.httpResponse.statusCode}")
        logger.debug("${response.httpResponse.getContentAsString()}")
    }

}

def getSOAPEndpoint() {
    def soapEndpoint = midtierServerUrl + "/arsys/services/ARService?server=" + serverName + "&webService=HPD_IncidentServiceInterface"
    logger.debug("SOAP Endpoint: ${soapEndpoint}")
    return soapEndpoint
}

String parseConfigIfNullOrEmpty(String property, String propertyKey) {
    if (property == null || "".equals(property)) {
        return _conf(propertyKey, true)
    } else {
        return property
    }
}

def _conf(confKey, boolean isMandatory) {
    def confVal = conf[CONF_PREFIX + confKey]
    if (isMandatory && confVal == null) {
        def errorMessage = "${LOG_PREFIX} Skipping action, Mandatory Conf item ${CONF_PREFIX + confKey} is missing. Check your marid conf file.";
        throw new Exception(errorMessage);
    }
    return confVal
}

