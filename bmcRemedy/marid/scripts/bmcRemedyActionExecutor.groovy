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

assignedGroup =  params.teamName
resolution = params.resolution
notes = "og_alias:["+params.alias+"]"
customerName = "App"
customerLastName = "Admin"
company = "Calbro Services"
incidentType = "Infrastructure Event"
reportedSource = "Other"
createStatus = "New"
closedStatus = "Closed"
assignee = ""
if(assignedGroup==null){
    assignedGroup="Service Desk"
    assignee="Allen Allbrook"
}
emptyString = ""



try {
    parseParameters()

    if (mappedAction == "addWorkInfo") {
        makeAddWorkInfoSOAPRequest('SOAP-OpsGenie [Add Work Info]')
    }
    else if (mappedAction == "createIncident") {
        makeCreateIncidentSOAPRequest('SOAP-OpsGenie [Create Incident]')
    }
    else if (mappedAction == "closeIncident") {
        makeCloseIncidentSOAPRequest('SOAP-OpsGenie [Close Incident]')
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

def makeCreateIncidentSOAPRequest(String soapAction) {
    SOAPClient client = new SOAPClient(getCreateIncidentSOAPEndpoint())
    String priority=params.priority
    String impact = translatePriorityToImpactValue(priority)
    String urgency = translatePriorityToUrgencyValue(priority)

    def response = client.send(SOAPAction: soapAction) {
        soapNamespacePrefix "soapenv"
        envelopeAttributes('xmlns:urn': 'urn:HPD_IncidentInterface_Create_WS')
        header {
            NAMESPACE_PREFIX + 'AuthenticationInfo' {
                NAMESPACE_PREFIX + 'userName'(username)
                'urn:password'(password)
            }
        }
        body {
            NAMESPACE_PREFIX + 'HelpDesk_Submit_Service' {
                NAMESPACE_PREFIX + 'Company'(company)
                NAMESPACE_PREFIX + 'First_Name'(customerName)
                NAMESPACE_PREFIX + 'Last_Name'(customerLastName)
                NAMESPACE_PREFIX + 'Impact'(impact)
                NAMESPACE_PREFIX + 'Reported_Source'(reportedSource)
                NAMESPACE_PREFIX + 'Service_Type'(incidentType)
                NAMESPACE_PREFIX + 'Status'(createStatus)
                NAMESPACE_PREFIX + 'Summary'(params.message)
                NAMESPACE_PREFIX + 'Urgency'(urgency)
                NAMESPACE_PREFIX + 'Notes'(notes)
                NAMESPACE_PREFIX + 'Assigned_Group'(assignedGroup)
                NAMESPACE_PREFIX + 'Assignee'(assignee)
                NAMESPACE_PREFIX + 'Action'(emptyString)
            }
        }
    }
    if (300 > response.httpResponse.statusCode) {
        logger.debug("${response.httpResponse.getContentAsString()}")
        opsgenie.addDetails(["alertId": params.alertId, "details": ["og-internal-incidentID": response.body.text()]])
    } else {
        logger.error("SOAP Request failed with status code:  ${response.httpResponse.statusCode}")
        logger.debug("${response.httpResponse.getContentAsString()}")
    }

}

def makeCloseIncidentSOAPRequest(String soapAction) {
    SOAPClient client = new SOAPClient(getCloseIncidentSOAPEndpoint())
    String priority=params.priority
    String impact = translatePriorityToImpactValue(priority)
    String urgency = translatePriorityToUrgencyValue(priority)

    def response = client.send(SOAPAction: soapAction) {
        soapNamespacePrefix "soapenv"
        envelopeAttributes('xmlns:urn': 'urn:HPD_IncidentInterface_WS')
        header {
            NAMESPACE_PREFIX + 'AuthenticationInfo' {
                NAMESPACE_PREFIX + 'userName'(username)
                'urn:password'(password)
            }
        }
        body {
            NAMESPACE_PREFIX + 'HelpDesk_Modify_Service' {
                NAMESPACE_PREFIX + 'Company'(company)
                NAMESPACE_PREFIX + 'Impact'(impact)
                NAMESPACE_PREFIX + 'Incident_Number'(incidentNumber)
                NAMESPACE_PREFIX + 'Service_Type'(incidentType)
                NAMESPACE_PREFIX + 'Status'(closedStatus)
                NAMESPACE_PREFIX + 'Summary'(params.message)
                NAMESPACE_PREFIX + 'Urgency'(urgency)
                NAMESPACE_PREFIX + 'Notes'(notes)
                NAMESPACE_PREFIX + 'Resolution'(resolution)

                NAMESPACE_PREFIX + 'Action'(emptyString)
                NAMESPACE_PREFIX + 'Categorization_Tier_1'(emptyString)
                NAMESPACE_PREFIX + 'Categorization_Tier_2'(emptyString)
                NAMESPACE_PREFIX + 'Categorization_Tier_3'(emptyString)
                NAMESPACE_PREFIX + 'Manufacturer'(emptyString)
                NAMESPACE_PREFIX + 'Closure_Manufacturer'(emptyString)
                NAMESPACE_PREFIX + 'Closure_Product_Category_Tier1'(emptyString)
                NAMESPACE_PREFIX + 'Closure_Product_Category_Tier2'(emptyString)
                NAMESPACE_PREFIX + 'Closure_Product_Category_Tier3'(emptyString)
                NAMESPACE_PREFIX + 'Closure_Product_Model_Version'(emptyString)
                NAMESPACE_PREFIX + 'Closure_Product_Name'(emptyString)
                NAMESPACE_PREFIX + 'Product_Categorization_Tier_1'(emptyString)
                NAMESPACE_PREFIX + 'Product_Categorization_Tier_2'(emptyString)
                NAMESPACE_PREFIX + 'Product_Categorization_Tier_3'(emptyString)
                NAMESPACE_PREFIX + 'Product_Model_Version'(emptyString)
                NAMESPACE_PREFIX + 'Product_Name'(emptyString)
                NAMESPACE_PREFIX + 'Reported_Source'(emptyString)
                NAMESPACE_PREFIX + 'Resolution_Category'(emptyString)
                NAMESPACE_PREFIX + 'Resolution_Category_Tier_2'(emptyString)
                NAMESPACE_PREFIX + 'Resolution_Category_Tier_3'(emptyString)
                NAMESPACE_PREFIX + 'Resolution_Method'(emptyString)
                NAMESPACE_PREFIX + 'Work_Info_Summary'(emptyString)
                NAMESPACE_PREFIX + 'Work_Info_Notes'(emptyString)
                NAMESPACE_PREFIX + 'Work_Info_Type'(emptyString)
                NAMESPACE_PREFIX + 'Work_Info_Date'(emptyString)
                NAMESPACE_PREFIX + 'Work_Info_Source'(emptyString)
                NAMESPACE_PREFIX + 'Work_Info_Locked'(emptyString)
                NAMESPACE_PREFIX + 'Work_Info_View_Access'(emptyString)
                NAMESPACE_PREFIX + 'ServiceCI'(emptyString)
                NAMESPACE_PREFIX + 'ServiceCI_ReconID'(emptyString)
                NAMESPACE_PREFIX + 'HPD_CI'(emptyString)
                NAMESPACE_PREFIX + 'HPD_CI_ReconID'(emptyString)
                NAMESPACE_PREFIX + 'HPD_CI_FormName'(emptyString)
                NAMESPACE_PREFIX + 'z1D_CI_FormName'(emptyString)
            }
        }
    }
    if (300 > response.httpResponse.statusCode) {
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

def getCreateIncidentSOAPEndpoint() {
    def soapEndpoint = midtierServerUrl + "/arsys/services/ARService?server=" + serverName + "&webService=HPD_IncidentInterface_Create_WS"
    logger.debug("SOAP Endpoint: ${soapEndpoint}")
    return soapEndpoint
}

def getCloseIncidentSOAPEndpoint() {
    def soapEndpoint = midtierServerUrl + "/arsys/services/ARService?server=" + serverName + "&webService=HPD_IncidentInterface_WS"
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

String translatePriorityToImpactValue(String priority) {
    if ("P1".equals(priority)) {
        return "1-Extensive/Widespread"
    } else if ("P2".equals(priority)) {
        return "2-Significant"
    } else if ("P3".equals(priority)) {
        return "3-Moderate/Limited"
    } else
        return "4-Minor"
}

String translatePriorityToUrgencyValue(String priority) {
    if ("P1".equals(priority)) {
        return "1-Critical"
    } else if ("P2".equals(priority)) {
        return "2-High"
    } else if ("P3".equals(priority)) {
        return "3-Medium"
    } else
        return "4-Low"
}