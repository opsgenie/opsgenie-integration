import wslite.soap.*
import wslite.http.auth.*

import java.text.SimpleDateFormat

LOG_PREFIX = "[${mappedAction}]: "
BMC_FOOTPRINTS_WEB_SERVICE_EXTENSION = "/footprints/servicedesk/externalapisoap/ExternalApiServicePort?wsdl"
logger.warn("${LOG_PREFIX} Will execute [${mappedAction}] for alertId ${params.alertId}")

CONF_PREFIX = "bmcFootPrints."

try {
    def url = params.url
    def username = params.username
    def password = params.password
    def workspaceName = params.workspaceName
    def ticketId = params.ticketId
    def shortDescription = params.shortDescription
    def description = params.description
    def priority = params.priority
    def ticketType = params.ticketType
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

    if (!workspaceName) {
        workspaceName = _conf("workspaceName", true)
    }

    client = new SOAPClient(url)
    client.setAuthorization(new HTTPBasicAuthorization(username, password))

    def workspaceId = getWorkspaceId(workspaceName)

    if (!workspaceId) {
        logger.error(LOG_PREFIX + "Cannot obtain workspace ID from BMC FootPrints v12.")
        return
    }

    if ("createIncident".equals(mappedAction) || "createProblem".equals(mappedAction)) {
        ticketType = "createIncident".equals(mappedAction) ? "Incident" : "Problem"
        def itemDefinitionId = getItemDefinitionId(workspaceId, ticketType)

        if (!itemDefinitionId) {
            logger.error(LOG_PREFIX + "Cannot obtain item definition ID for '${ticketType}' from BMC FootPrints v12.")
            return
        }

        def itemId = createTicket(itemDefinitionId, shortDescription, description, priority, alertAlias, ticketType)

        if (!itemId) {
            logger.error(LOG_PREFIX + "Cannot obtain item ID for item definition ID '${itemDefinitionId}' from BMC FootPrints v12.")
            return
        }

        opsgenie.addDetails([alertId: params.alertId, details: ["ticketId": itemId, "ticketType": ticketType]])
    } else {
        def itemDefinitionId = getItemDefinitionId(workspaceId, ticketType)

        if (!itemDefinitionId) {
            logger.error(LOG_PREFIX + "Cannot obtain item definition ID for '${ticketType}' from BMC FootPrints v12.")
            return
        }

        if ("updateDescription".equals(mappedAction)) {
            updateTicketDescription(itemDefinitionId, ticketId, description)
        } else if ("updatePriority".equals(mappedAction)) {
            updateTicketPriority(itemDefinitionId, ticketId, description, priority)
        } else if ("resolveTicket".equals(mappedAction)) {
            resolveTicket(itemDefinitionId, ticketId, description)
        } else {
            logger.warn(LOG_PREFIX + "Skipping ${mappedAction} action, could not determine the mapped action.")
        }
    }
} catch (Exception e) {
    logger.error(e.getMessage(), e)
}

String getWorkspaceId(String workspaceName) {
    logger.debug(LOG_PREFIX + "Will send listContainerDefinitions request to BMC FootPrints v12 Web Service API: "
            + client.getServiceURL() + ".")

    def response = client.send {
        envelopeAttributes = [
                "xmlns:soapenv": "http://schemas.xmlsoap.org/soap/envelope/",
                "xmlns:ext"    : "http://externalapi.business.footprints.numarasoftware.com/"
        ]
        body {
            "ext:listContainerDefinitions" {
                "listContainerDefinitionsRequest" {

                }
            }
        }
    }

    logger.debug("Response from BMC FootPrints v12 Web Service API: " + response.httpResponse.getContentAsString()
            + " Response Code: " + response.httpResponse.getStatusCode() + ".")

    return response.listContainerDefinitionsResponse.'return'._definitions.find { it ->
        it._definitionName == workspaceName
    }?._definitionId
}

String getItemDefinitionId(String workspaceId, String itemType) {
    logger.debug(LOG_PREFIX + "Will send listItemDefinitions request to BMC FootPrints v12 Web Service API: "
            + client.getServiceURL() + ".")

    def response = client.send {
        envelopeAttributes = [
                "xmlns:soapenv": "http://schemas.xmlsoap.org/soap/envelope/",
                "xmlns:ext"    : "http://externalapi.business.footprints.numarasoftware.com/"
        ]
        body {
            "ext:listItemDefinitions" {
                "listItemDefinitionsRequest" {
                    _containerDefinitionId(workspaceId)
                }
            }
        }
    }

    logger.debug("Response from BMC FootPrints v12 Web Service API: " + response.httpResponse.getContentAsString()
            + " Response Code: " + response.httpResponse.getStatusCode() + ".")

    return response.listItemDefinitionsResponse.'return'._definitions.find { it ->
        it._definitionName == itemType
    }?._definitionId
}

String createTicket(String itemDefinitionId, String shortDescription, String description, String priority, String alertAlias, String ticketType) {
    logger.debug(LOG_PREFIX + "Will send createTicket request to BMC FootPrints v12 Web Service API: "
            + client.getServiceURL() + ".")

    def statusField = (ticketType.equals("Incident")) ? "Request" : "Pending review"

    def response = client.send {
        envelopeAttributes = [
                "xmlns:soapenv": "http://schemas.xmlsoap.org/soap/envelope/",
                "xmlns:ext": "http://externalapi.business.footprints.numarasoftware.com/"
        ]
        body {
            "ext:createTicket" {
                "createTicketRequest" {
                    _ticketDefinitionId(itemDefinitionId)
                    _ticketFields {
                        itemFields {
                            fieldName("OpsGenie Alert Alias")
                            fieldValue {
                                value(alertAlias)
                            }
                        }
                        itemFields {
                            fieldName("Description")
                            fieldValue {
                                value(description)
                            }
                        }
                        itemFields {
                            fieldName("Short Description")
                            fieldValue {
                                value(shortDescription)
                            }
                        }
                        itemFields {
                            fieldName("Status")
                            fieldValue {
                                value(statusField)
                            }
                        }
                        itemFields {
                            fieldName("Priority")
                            fieldValue {
                                value(mapPriority(priority))
                            }
                        }
                    }
                }
            }
        }
    }

    logger.debug("Response from BMC FootPrints v12 Web Service API: " + response.httpResponse.getContentAsString()
            + " Response Code: " + response.httpResponse.getStatusCode() + ".")

    return response.createTicketResponse.'return'
}

def updateTicketDescription(String ticketDefinitionId, String ticketId, String newDescription) {
    logger.debug(LOG_PREFIX + "Will send editTicket request for updating ticket description to BMC FootPrints v12 Web Service API: "
            + client.getServiceURL() + ".")

    def response = client.send {
        envelopeAttributes = [
                "xmlns:soapenv": "http://schemas.xmlsoap.org/soap/envelope/",
                "xmlns:ext"    : "http://externalapi.business.footprints.numarasoftware.com/"
        ]
        body {
            "ext:editTicket" {
                "editTicketRequest" {
                    _ticketDefinitionId(ticketDefinitionId)
                    _ticketId(ticketId)
                    _ticketFields {
                        itemFields {
                            fieldName("Description")
                            fieldValue {
                                value(newDescription)
                            }
                        }
                    }
                }
            }
        }
    }

    logger.debug("Response from BMC FootPrints v12 Web Service API: " + response.httpResponse.getContentAsString()
            + " Response Code: " + response.httpResponse.getStatusCode() + ".")
}

def updateTicketPriority(String ticketDefinitionId, String ticketId, String newDescription, String priority) {
    logger.debug(LOG_PREFIX + "Will send editTicket request for updating ticket priority to BMC FootPrints v12 Web Service API: "
            + client.getServiceURL() + ".")

    def response = client.send {
        envelopeAttributes = [
                "xmlns:soapenv": "http://schemas.xmlsoap.org/soap/envelope/",
                "xmlns:ext"    : "http://externalapi.business.footprints.numarasoftware.com/"
        ]
        body {
            "ext:editTicket" {
                "editTicketRequest" {
                    _ticketDefinitionId(ticketDefinitionId)
                    _ticketId(ticketId)
                    _ticketFields {
                        itemFields {
                            fieldName("Description")
                            fieldValue {
                                value(newDescription)
                            }
                        }
                        itemFields {
                            fieldName("Priority")
                            fieldValue {
                                value(mapPriority(priority))
                            }
                        }
                    }
                }
            }
        }
    }

    logger.debug("Response from BMC FootPrints v12 Web Service API: " + response.httpResponse.getContentAsString()
            + " Response Code: " + response.httpResponse.getStatusCode() + ".")
}

def resolveTicket(String ticketDefinitionId, String ticketId, String resolution) {
    logger.debug(LOG_PREFIX + "Will send editTicket request for resolving to BMC FootPrints v12 Web Service API: "
            + client.getServiceURL() + ".")

    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss")
    def resolutionDateTime = sdf.format(new Date())

    def response = client.send {
        envelopeAttributes = [
                "xmlns:soapenv": "http://schemas.xmlsoap.org/soap/envelope/",
                "xmlns:ext"    : "http://externalapi.business.footprints.numarasoftware.com/"
        ]
        body {
            "ext:editTicket" {
                "editTicketRequest" {
                    _ticketDefinitionId(ticketDefinitionId)
                    _ticketId(ticketId)
                    _ticketFields {
                        itemFields {
                            fieldName("Description")
                            fieldValue {
                                value(resolution)
                            }
                        }
                        itemFields {
                            fieldName("Resolution")
                            fieldValue {
                                value(resolution)
                            }
                        }
                        itemFields {
                            fieldName("Resolution Date & Time")
                            fieldValue {
                                value(resolutionDateTime)
                            }
                        }
                    }
                }
            }
        }
    }

    logger.debug("Response from BMC FootPrints v12 Web Service API: " + response.httpResponse.getContentAsString()
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
            return "1-Critical"
        case "P2":
            return "2-High"
        case "P3":
            return "3-Medium"
        case "P4":
            return "4-Low"
        case "P5":
            return "5-Planning"
        default:
            return "3-Medium"
    }
}
