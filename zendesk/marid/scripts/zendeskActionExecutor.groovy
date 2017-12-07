import com.ifountain.opsgenie.client.http.OpsGenieHttpClient
import com.ifountain.opsgenie.client.util.ClientConfiguration
import com.ifountain.opsgenie.client.util.JsonUtils
import org.apache.http.auth.UsernamePasswordCredentials
import org.apache.http.HttpHeaders

LOG_PREFIX = "[${mappedAction}]:";
logger.info("${LOG_PREFIX} Will execute [${mappedAction}] for alertId ${params.alertId}");

CONF_PREFIX = "zendesk.";
HTTP_CLIENT = createHttpClient();

try {
    String zendeskUrl = params.zendeskUrl
    if (zendeskUrl == null || "".equals(zendeskUrl)) {
        zendeskUrl = "https://" + _conf("subdomain", true) + ".zendesk.com"
    }
    String ticketID = params.ticketId
    Map contentTypeHeader = [:]
    contentTypeHeader[HttpHeaders.CONTENT_TYPE] = "application/json"
    contentTypeHeader[HttpHeaders.ACCEPT_LANGUAGE] = "application/json"

    def contentParams = [:]
    def ticket = [:]
    def comment = [:]

    String resultUri = zendeskUrl + "/api/v2/tickets"
    if (mappedAction == "addInternalComment") {
        resultUri += "/" + ticketID + ".json"
        comment.put("body", params.body)
        comment.put("public", false)
        ticket.put("comment", comment)
        contentParams.put("ticket", ticket)

    } else if (mappedAction == "addPublicComment") {
        resultUri += "/" + ticketID + ".json"
        comment.put("body", params.body)
        comment.put("public", true)
        ticket.put("comment", comment)
    } else if (mappedAction == "createTicket") {
        resultUri += ".json";
        comment.put("body", params.body)
        comment.put("public", false)
        ticket.put("comment", comment)
        ticket.put("external_id", params.externalId)
        ticket.put("subject", params.subject)
        ticket.put("tags", params.tags)
        contentParams.put("ticket", ticket)
    } else if (mappedAction == "setStatusToClosed") {
        resultUri += "/" + ticketID + ".json"
        comment.put("body", params.body)
        comment.put("public", false)
        ticket.put("comment", comment)
        ticket.put("status", "closed")
        contentParams.put("ticket", ticket)
    } else if (mappedAction == "setStatusToOpen") {
        resultUri += "/" + ticketID + ".json"
        comment.put("body", params.body)
        comment.put("public", false)
        ticket.put("comment", comment)
        ticket.put("status", "open")
        contentParams.put("ticket", ticket)
    } else if (mappedAction == "setStatusToSolved") {
        resultUri += "/" + ticketID + ".json"
        comment.put("body", params.body)
        comment.put("public", false)
        ticket.put("comment", comment)
        ticket.put("status", "solved")
        contentParams.put("ticket", ticket)
    } else if (mappedAction == "setStatusToPending") {
        resultUri += "/" + ticketID + ".json"
        comment.put("body", params.body)
        comment.put("public", false)
        ticket.put("comment", comment)
        ticket.put("status", "pending")
        contentParams.put("ticket", ticket)
    }

    logger.debug("${LOG_PREFIX} The payload to be sent to Zendesk: ${contentParams}")
    logger.debug("${LOG_PREFIX} OpsGenie will send the payload to: ${resultUri}")

    if (mappedAction == "createTicket") {
        def responseBody = postActionsToZendesk(contentParams, contentTypeHeader, resultUri)
        addTicketIDtoAlert(params, responseBody)
    } else {
        def put = ((OpsGenieHttpClient) HTTP_CLIENT).preparePutMethod(resultUri, contentParams, [:])
        putActionsToZendesk(put)
    }
}
catch (Exception e) {
    logger.error(e.getMessage(), e)
}
finally {
    HTTP_CLIENT.close()
}

void putActionsToZendesk(def httpMethod) {

    httpMethod.setHeader("Content-Type", "application/json")
    response = ((OpsGenieHttpClient) HTTP_CLIENT).executeHttpMethod(httpMethod)
    if (response.getStatusCode() < 299) {
        logger.info("${LOG_PREFIX} Successfully executed at Zendesk.")
        logger.debug("${LOG_PREFIX} Zendesk response: ${response.statusCode} ${response.getContentAsString()}")
    } else {
        logger.warn("${LOG_PREFIX} Could not execute at Zendesk; response: ${response.statusCode} ${response.getContentAsString()}")
    }

}

def postActionsToZendesk(Map<String, Object> postParams, Map headers, String zendeskUrl) {
    def response = HTTP_CLIENT.post(zendeskUrl, JsonUtils.toJson(postParams), headers)

    def responseMap = [:]
    def body = response.content
    if (body != null) {
        responseMap = JsonUtils.parse(body)
        if (response.getStatusCode() < 400) {
            logger.info("${LOG_PREFIX} Successfully executed at Zendesk.");
            logger.debug("${LOG_PREFIX} Zendesk response: ${response.statusCode} ${response.getContentAsString()}")
        } else {
            logger.warn("${LOG_PREFIX} Could not execute at Zendesk; response: ${response.statusCode} ${response.getContentAsString()}");
        }
    }
    return responseMap
}

void addTicketIDtoAlert(Map params, Map responseBody) {
    def ticket = responseBody.ticket
    String ticketId = ticket?.id
    String alertId = params.alertId

    if (responseBody != null) {

        opsgenie.addDetails(["id": alertId, "details": ["ticket_id": ticketId]])
    } else {
        logger.warn("${LOG_PREFIX} Zendesk response body is null.")
    }
}

def createHttpClient() {
    def timeout = _conf("http.timeout", false);
    if (timeout == null) {
        timeout = 30000;
    } else {
        timeout = timeout.toInteger();
    }

    String zendeskEmail = params.zendeskEmail
    if (zendeskEmail == null || "".equals(zendeskEmail)) {
        zendeskEmail = _conf("zendeskEmail", true)
    }
    String apiToken = params.apiToken
    if (apiToken == null || "".equals(apiToken)) {
        apiToken = _conf("apiToken", true)
    }
    zendeskEmail = zendeskEmail + "/token"
    logger.debug("${LOG_PREFIX} Zendesk Email: ${zendeskEmail}")
    logger.debug("${LOG_PREFIX} API Token: ${apiToken}")
    ClientConfiguration clientConfiguration = new ClientConfiguration().setSocketTimeout(timeout)
            .setCredentials(new UsernamePasswordCredentials(zendeskEmail, apiToken))
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