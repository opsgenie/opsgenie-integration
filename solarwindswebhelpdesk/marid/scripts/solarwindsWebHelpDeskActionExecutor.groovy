import com.ifountain.opsgenie.client.http.OpsGenieHttpClient
import com.ifountain.opsgenie.client.util.ClientConfiguration
import org.apache.http.HttpHeaders
import com.ifountain.opsgenie.client.util.JsonUtils
import org.apache.http.entity.StringEntity

LOG_PREFIX = "[${action}]:"
logger.warn("${LOG_PREFIX} Will execute action for alertId ${alert.alertId}")

CONF_PREFIX = "solarwindswebhelpdesk.";
ACKNOWLEDGED_STATUS_ID = 6
CLOSE_STATUS_ID = 3
serverURL = ""
apiToken = ""

serverURL = parseConfigIfNullOrEmpty(serverURL, "serverURL")
apiToken = parseConfigIfNullOrEmpty(apiToken, "apiToken")
HTTP_CLIENT = createHttpClient()

try {
    if (action == "Close") {
        sendCloseRequest()
    } else if (action == "Acknowledge") {
        sendAcknowledgeRequest()
    } else if (action == "AddNote") {
        sendAddNoteRequest()
    }
}
finally {
    HTTP_CLIENT.close()
}

def sendCloseRequest(){
    Map contentTypeHeader = [:]
    contentTypeHeader[HttpHeaders.CONTENT_TYPE] = "application/json"
    String url = serverURL + "/helpdesk/WebObjects/Helpdesk.woa/ra/Tickets/" + alert.alias + "?apiKey=" + apiToken

    def statustype = [:]
    def contentParams = [:]

    statustype.put("id", CLOSE_STATUS_ID)
    contentParams.put("statustype", statustype)

    sendPutActionsToSolarwinds(contentParams,url);
}

def sendAcknowledgeRequest(){
    Map contentTypeHeader = [:]
    contentTypeHeader[HttpHeaders.CONTENT_TYPE] = "application/json"
    String url = serverURL + "/helpdesk/WebObjects/Helpdesk.woa/ra/Tickets/" + alert.alias + "?apiKey=" + apiToken
    logger.info("${url} URL");

    def statustype = [:]
    def contentParams = [:]

    statustype.put("id", ACKNOWLEDGED_STATUS_ID)
    contentParams.put("statustype", statustype)

    sendPutActionsToSolarwinds(contentParams,url)
}

def sendAddNoteRequest(){
    Map contentTypeHeader = [:]
    contentTypeHeader[HttpHeaders.CONTENT_TYPE] = "application/json"
    String url = serverURL + "/helpdesk/WebObjects/Helpdesk.woa/ra/TechNotes?apiKey=" + apiToken;
    def job = [:]
    def contentParams = [:]
    contentParams.put("noteText", alert.note );

    job.put("type", "JobTicket")
    job.put("id", alert.alias)
    contentParams.put("jobticket", job)
    contentParams.put("workTime", "0")
    contentParams.put("isHidden", false)
    contentParams.put("isSolution", false)
    contentParams.put("emailClient", true)
    contentParams.put("emailTech", true)
    contentParams.put("emailTechGroupLevel", false)
    contentParams.put("emailGroupManager", false)
    contentParams.put("emailCc", false)
    contentParams.put("emailBcc", false)
    contentParams.put("ccAddressesForTech", "")
    contentParams.put("bccAddresses", "")

    sendPostActionsToSolarwinds(contentParams,contentTypeHeader,url);
}


def createHttpClient() {
    def timeout = _conf("http.timeout", false);
    if(timeout == null){
        timeout = 30000;
    }
    else{
        timeout = timeout.toInteger();
    }
    ClientConfiguration clientConfiguration = new ClientConfiguration().setSocketTimeout(timeout)

    return new OpsGenieHttpClient(clientConfiguration)
}

def sendPutActionsToSolarwinds(Map<String, Object> postParams, String url) {
    String content = JsonUtils.toJson(postParams)
    logger.info("Content : ${content} ")

    def put = ((OpsGenieHttpClient) HTTP_CLIENT).preparePutMethod(url, content, [:])
    ((StringEntity) put.getEntity()).setChunked(false)
    put.setHeader("Content-Type", "application/json")
    def response = ((OpsGenieHttpClient) HTTP_CLIENT).executeHttpMethod(put)

    def body = response.content
    if (body != null) {
        if (response.getStatusCode() < 400) {
            logger.info("${LOG_PREFIX} Successfully executed at Solarwinds.");
            logger.debug("${LOG_PREFIX} Solarwinds response: ${response.getContentAsString()}")
        } else {
            logger.error("${LOG_PREFIX} Could not execute at Solarwinds; response: ${response.statusCode} ${response.getContentAsString()}");
        }
    }
}

def sendPostActionsToSolarwinds(Map<String, Object> postParams, Map headers, String url) {

    String content = JsonUtils.toJson(postParams)

    def post = ((OpsGenieHttpClient) HTTP_CLIENT).preparePostMethod(url, content, headers)
    ((StringEntity) post.getEntity()).setChunked(false)

    def response = ((OpsGenieHttpClient) HTTP_CLIENT).executeHttpMethod(post)
    def body = response.content
    if (body != null) {
        if (response.getStatusCode() < 400) {
            logger.info("${LOG_PREFIX} Successfully executed at Solarwinds.");
            logger.debug("${LOG_PREFIX} Solarwinds response: ${response.getContentAsString()}")
        } else {
            logger.error("${LOG_PREFIX} Could not execute at Solarwinds; response: ${response.statusCode} ${response.getContentAsString()}");
        }
    }
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
        throw new Exception(errorMessage)
    }
    return confVal
}


