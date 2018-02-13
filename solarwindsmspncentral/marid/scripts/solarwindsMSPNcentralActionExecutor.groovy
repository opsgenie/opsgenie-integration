import wslite.soap.*

LOG_PREFIX = "[${mappedAction}]:";
logger.warn("${LOG_PREFIX} Will execute [${mappedAction}] for alertId ${params.alertId}");

CONF_PREFIX = "solarwindsMSPNcentral.";

username = params.username
password = params.password
url = params.url
activeNotificationTriggerID = params.activeNotificationTriggerID
logger.debug("activeNotificationTriggerID: ${activeNotificationTriggerID}")


try {
    parseParameters()

    if (mappedAction == "acknowledgeNotification") {
        makeAcknowledgeAlertSOAPRequest()
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
    url = parseConfigIfNullOrEmpty(url, "url")
    logger.debug("URL: ${url}")
}

def makeAcknowledgeAlertSOAPRequest() {
    SOAPClient client = new SOAPClient(getSOAPEndpoint())

    def response = client.send(SOAPAction: '') {
        soapNamespacePrefix "soap"
        envelopeAttributes('xmlns:ei2':'http://ei2.nobj.nable.com/')
        body {
            'ei2:acknowledgeNotification' {
                'ei2:activeNotificationTriggerIDArray'(activeNotificationTriggerID)
                'ei2:username'(username)
                'ei2:password'(password)
                'ei2:addToDeviceNotes'(true)
                'ei2:suppressOnEscalation'(false)

            }
        }
    }

    logger.debug("Status code of the response: ${response.httpResponse.statusCode}")
    logger.debug("Response content: ${response.httpResponse.getContentAsString()}")
    if (300 > response.httpResponse.statusCode) {
        logger.info("SOAP Request sent successfully.")
    } else {
        logger.error("SOAP Request failed with status code:  ${response.httpResponse.statusCode}")
    }


}

def getSOAPEndpoint() {
    def soapEndpoint = url + "/dms2/services2/ServerEI2"
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

