import wslite.soap.*

LOG_PREFIX = "[${mappedAction}]: "
BMC_FOOTPRINTS_WEB_SERVICE_EXTENSION = "/MRcgi/MRWebServices.pl"
logger.warn("${LOG_PREFIX} Will execute [${mappedAction}] for alertId ${params.alertId}")

CONF_PREFIX = "bmcFootPrints."

try {
    def url = params.url
    def username = params.username
    def password = params.password

    def incidentWorkspaceId = params.incidentWorkspaceId
    def problemWorkspaceId = params.problemWorkspaceId

    def issueNumber = params.issueNumber
    def title = params.title
    def description = params.description
    def priority = params.priority
    def issueType = params.issueType
    def alertAlias = params.alertAlias

    if (!url) {
        url = _conf("url", true)
    }

    url = reformatUrl(url) + BMC_FOOTPRINTS_WEB_SERVICE_EXTENSION

    if (!username) {
        username = _conf("username", true)
    }

    if (!password) {
        password = _conf("password", true)
    }

    if (!incidentWorkspaceId) {
        incidentWorkspaceId = _conf("incidentWorkspaceId", false)
    }

    if (!problemWorkspaceId) {
        problemWorkspaceId = _conf("problemWorkspaceId", false)
    }

    if (!incidentWorkspaceId && !problemWorkspaceId) {
        logger.error(LOG_PREFIX + "Cannot find both of the incidentWorkspaceId and problemWorkspaceId either in the configuration file or from the OpsGenie payload."
                + " Please fill one of the incident or problem workspace IDs in the config file or in the integration settings in OpsGenie.")
        return
    }

    client = new SOAPClient(url)

    if ("createIncident".equals(mappedAction)) {
        def createdIssueNumber = createIssue(username, password, title, description, priority, alertAlias, incidentWorkspaceId)
        opsgenie.addDetails([alertId: params.alertId, details: ["issueNumber": createdIssueNumber, "issueType": "Incident"]])
    } else if ("createProblem".equals(mappedAction)) {
        def createdIssueNumber = createIssue(username, password, title, description, priority, alertAlias, problemWorkspaceId)
        opsgenie.addDetails([alertId: params.alertId, details: ["issueNumber": createdIssueNumber, "issueType": "Problem"]])
    } else {
        if (!issueType) {
            logger.error(LOG_PREFIX + "Cannot obtain issueType from the OpsGenie payload. Please make sure your integrations settings are correct in OpsGenie.")
            return
        }

        def workspaceId = ("Incident".equals(issueType)) ? incidentWorkspaceId : problemWorkspaceId

        if ("updateDescription".equals(mappedAction)) {
            updateIssueDescription(username, password, issueNumber, description, workspaceId)
        } else if ("updatePriority".equals(mappedAction)) {
            updateIssuePriority(username, password, issueNumber, priority, workspaceId)
        } else if ("resolveIssue".equals(mappedAction)) {
            resolveIssue(username, password, issueNumber, description, workspaceId)
        } else {
            logger.warn(LOG_PREFIX + "Skipping ${mappedAction} action, could not determine the mapped action.")
        }
    }
} catch (Exception e) {
    logger.error(e.getMessage(), e)
}

String createIssue(String fpUsername, String fpPassword, String issueTitle, String issueDescription, String issuePriority, String alertAlias, String workspaceId) {
    logger.debug(LOG_PREFIX + "Will send createIssue request to BMC FootPrints v11 Web Service API: "
            + client.getServiceURL() + ".")

    def response = client.send {
        envelopeAttributes = [
                "xmlns:SOAP-ENV": "http://schemas.xmlsoap.org/soap/envelope/",
                "xmlns:SOAP-ENC": "http://schemas.xmlsoap.org/soap/encoding/",
                "xmlns:namesp2" : "http://xml.apache.org/xml-soap",
                "xmlns:xsd"     : "http://www.w3.org/1999/XMLSchema",
                "xmlns:xsi"     : "http://www.w3.org/1999/XMLSchema-instance"
        ]
        body {
            'namesp1:MRWebServices__createIssue'("xmlns:namesp1": "MRWebServices") {
                user("xsi:type": "xsd:string", fpUsername)
                password("xsi:type": "xsd:string", fpPassword)
                extrainfo("xsi:type": "xsd:string")
                args("xsi:type": "namesp2:SOAPStruct") {
                    projfields("xsi:type": "namesp2:SOAPStruct") {
                        OpsGenie__bAlert__bAlias(alertAlias)
                    }
                    priorityNumber("xsi:type": "xsd:int", mapPriority(issuePriority))
                    status("xsi:type": "xsd:string", "Open")
                    description("xsi:type": "xsd:string", issueDescription)
                    title("xsi:type": "xsd:string", issueTitle)
                    projectID("xsi:type": "xsd:int", workspaceId)
                }
            }
        }
    }

    logger.debug("Response from BMC FootPrints v11 Web Service API: " + response.httpResponse.getContentAsString()
            + " Response Code: " + response.httpResponse.getStatusCode() + ".")

    return response.'MRWebServices__createIssueResponse'.'return'
}

def updateIssueDescription(String fpUsername, String fpPassword, String issueMRId, String newDescription, String workspaceId) {
    logger.debug(LOG_PREFIX + "Will send editIssue request for updating issue description to BMC FootPrints v11 Web Service API: "
            + client.getServiceURL() + ".")

    def response = client.send {
        envelopeAttributes = [
                "xmlns:SOAP-ENV": "http://schemas.xmlsoap.org/soap/envelope/",
                "xmlns:SOAP-ENC": "http://schemas.xmlsoap.org/soap/encoding/",
                "xmlns:namesp2" : "http://xml.apache.org/xml-soap",
                "xmlns:xsd"     : "http://www.w3.org/1999/XMLSchema",
                "xmlns:xsi"     : "http://www.w3.org/1999/XMLSchema-instance"
        ]
        body {
            'namesp1:MRWebServices__editIssue'("xmlns:namesp1": "MRWebServices") {
                user("xsi:type": "xsd:string", fpUsername)
                password("xsi:type": "xsd:string", fpPassword)
                extrainfo("xsi:type": "xsd:string")
                args("xsi:type": "namesp2:SOAPStruct") {
                    description("xsi:type": "xsd:string", newDescription)
                    projectID("xsi:type": "xsd:int", workspaceId)
                    mrID("xsi:type": "xsd:int", issueMRId)
                }
            }
        }
    }

    logger.debug("Response from BMC FootPrints v11 Web Service API: " + response.httpResponse.getContentAsString()
            + " Response Code: " + response.httpResponse.getStatusCode() + ".")
}

def updateIssuePriority(String fpUsername, String fpPassword, String issueMRId, String issuePriority, String workspaceId) {
    logger.debug(LOG_PREFIX + "Will send editIssue request for updating issue priority to BMC FootPrints v11 Web Service API: "
            + client.getServiceURL() + ".")

    def response = client.send {
        envelopeAttributes = [
                "xmlns:SOAP-ENV": "http://schemas.xmlsoap.org/soap/envelope/",
                "xmlns:SOAP-ENC": "http://schemas.xmlsoap.org/soap/encoding/",
                "xmlns:namesp2" : "http://xml.apache.org/xml-soap",
                "xmlns:xsd"     : "http://www.w3.org/1999/XMLSchema",
                "xmlns:xsi"     : "http://www.w3.org/1999/XMLSchema-instance"
        ]
        body {
            'namesp1:MRWebServices__editIssue'("xmlns:namesp1": "MRWebServices") {
                user("xsi:type": "xsd:string", fpUsername)
                password("xsi:type": "xsd:string", fpPassword)
                extrainfo("xsi:type": "xsd:string")
                args("xsi:type": "namesp2:SOAPStruct") {
                    priorityNumber("xsi:type": "xsd:int", mapPriority(issuePriority))
                    projectID("xsi:type": "xsd:int", workspaceId)
                    mrID("xsi:type": "xsd:int", issueMRId)
                }
            }
        }
    }

    logger.debug("Response from BMC FootPrints v11 Web Service API: " + response.httpResponse.getContentAsString()
            + " Response Code: " + response.httpResponse.getStatusCode() + ".")
}

def resolveIssue(String fpUsername, String fpPassword, String issueMRId, String issueResolution, String workspaceId) {
    logger.debug(LOG_PREFIX + "Will send editIssue request for resolving to BMC FootPrints v11 Web Service API: "
            + client.getServiceURL() + ".")

    def response = client.send {
        envelopeAttributes = [
                "xmlns:SOAP-ENV": "http://schemas.xmlsoap.org/soap/envelope/",
                "xmlns:SOAP-ENC": "http://schemas.xmlsoap.org/soap/encoding/",
                "xmlns:namesp2" : "http://xml.apache.org/xml-soap",
                "xmlns:xsd"     : "http://www.w3.org/1999/XMLSchema",
                "xmlns:xsi"     : "http://www.w3.org/1999/XMLSchema-instance"
        ]
        body {
            'namesp1:MRWebServices__editIssue'("xmlns:namesp1": "MRWebServices") {
                user("xsi:type": "xsd:string", fpUsername)
                password("xsi:type": "xsd:string", fpPassword)
                extrainfo("xsi:type": "xsd:string")
                args("xsi:type": "namesp2:SOAPStruct") {
                    status("xsi:type": "xsd:string", "Closed")
                    description("xsi:type": "xsd:string", issueResolution)
                    projfields("xsi:type": "namesp2:SOAPStruct") {
                        Closure__bCode("xsi:type": "xsd:string", "Completed__bSuccessfully")
                        Resolution("xsi:type": "xsd:string", issueResolution)
                    }
                    projectID("xsi:type": "xsd:int", workspaceId)
                    mrID("xsi:type": "xsd:int", issueMRId)
                }
            }
        }
    }

    logger.debug("Response from BMC FootPrints v11 Web Service API: " + response.httpResponse.getContentAsString()
            + " Response Code: " + response.httpResponse.getStatusCode() + ".")
}

def _conf(String confKey, boolean isMandatory) {
    def confVal = conf[CONF_PREFIX + confKey]

    if (isMandatory && !confVal) {
        def errorMessage = "${LOG_PREFIX} Skipping action, Mandatory Conf item ${CONF_PREFIX + confKey} is missing. Check your marid conf file."
        throw new Exception(errorMessage)
    }

    return confVal
}

static def reformatUrl(String url) {
    if (url.endsWith("/")) {
        return url.substring(0, url.length() - 1)
    } else {
        return url
    }
}

static def mapPriority(String priority) {
    switch (priority) {
        case "P1":
            return "1"
        case "P2":
            return "2"
        case "P3":
            return "3"
        case "P4":
            return "4"
        case "P5":
            return "5"
        default:
            return "3"
    }
}
