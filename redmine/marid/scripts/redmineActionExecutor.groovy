import com.ifountain.opsgenie.client.http.OpsGenieHttpClient
import com.ifountain.opsgenie.client.util.ClientConfiguration
import com.ifountain.opsgenie.client.util.JsonUtils
import org.apache.commons.lang.StringUtils
import org.apache.http.entity.StringEntity

LOG_PREFIX = "[${action}]:";
logger.warn("${LOG_PREFIX} Will execute action for alertId ${alert.alertId}");

CONF_PREFIX = "redmine.";
alertFromOpsgenie = opsgenie.getAlert(alertId: alert.alertId)
HTTP_CLIENT = createHttpClient();
try{
    if (alertFromOpsgenie.size() > 0) {
        if (action == 'Create'){
            def contentParams = [:]
            def issueMap = [:]
            issueMap.project_id = Integer.parseInt(_conf("projectId", true) as String)
            issueMap.subject = "New Alert : " +  alertFromOpsgenie.message
            issueMap.description = alertFromOpsgenie.description

            def customFieldId = _conf("customFieldId", false) as String
            if(customFieldId) {
                def customFields = []
                customFields.add([
                        "id"   : Integer.parseInt(customFieldId),
                        "value": alertFromOpsgenie.alertId
                ])
                issueMap.put("custom_fields", customFields)
            }

            contentParams.put("issue", issueMap)
            String content = JsonUtils.toJson(contentParams)

            String url = _conf("url", true) + "/issues.json"

            def requestParameters = [:]
            requestParameters.key = _conf("apiKey", true)

            logger.debug("${LOG_PREFIX} Posting to Redmine. Url ${url} params:${requestParameters} content:${content}")
            def post = ((OpsGenieHttpClient) HTTP_CLIENT).preparePostMethod(url, content, ["Content-Type":"application/json"], requestParameters)
            ((StringEntity)post.getEntity()).setChunked(false)
            def response = ((OpsGenieHttpClient) HTTP_CLIENT).executeHttpMethod(post)

            if (response.getStatusCode() < 299) {
                logger.info("${LOG_PREFIX} Successfully executed at Redmine.");
                logger.debug("${LOG_PREFIX} Redmine response: ${response.statusCode} ${response.getContentAsString()}")

                String issueId = getIssueIdFromRedmine(customFieldId)
                opsgenie.addNote([alertId:alertFromOpsgenie.alertId, note:"Redmine Issue Id : ${issueId}"]);
            } else {
                logger.warn("${LOG_PREFIX} Could not execute at Redmine; response: ${response.statusCode} ${response.getContentAsString()}")
            }
        } else if(action ==  "Acknowledge"){
            def customFieldId = _conf("customFieldId", false) as String
            if (customFieldId) {
                String issueId = getIssueIdFromRedmine(customFieldId)
                if(issueId) {
                    def inProgressStatusId = _conf("inProgressStatusId", false) as String
                    if (inProgressStatusId) {
                        updateIssueStatusInRedmine(issueId, inProgressStatusId)
                    } else {
                        addNoteToRedmineIssue(issueId, "Alert is acknowledged by ${alert.username}")
                    }
                }
            }
        } else if(action == "AddNote"){
            def customFieldId = _conf("customFieldId", false) as String
            if(customFieldId) {
                String issueId = getIssueIdFromRedmine(customFieldId)
                if(issueId) {
                    if(alert.note.startsWith("spent-time")) {
                        createTimeEntryInRedmine(issueId)
                    } else{
                        addNoteToRedmineIssue(issueId, alert.note + " by " + alert.username)
                    }
                }
            }
        } else if (action == "Close") {
            def customFieldId = _conf("customFieldId", false) as String
            if (customFieldId) {
                String issueId = getIssueIdFromRedmine(customFieldId)
                if(issueId) {
                    def closedStatusId = _conf("closedStatusId", false) as String
                    if (closedStatusId) {
                        updateIssueStatusInRedmine(issueId, closedStatusId)
                    } else {
                        addNoteToRedmineIssue(issueId, "Alert is closed by ${alert.username}")
                    }
                }
            }
        }
    }
    else {
        logger.warn("${LOG_PREFIX} Alert with id [${alert.alertId}] does not exist in OpsGenie. It is probably deleted.")
    }
}
finally {
    HTTP_CLIENT.close()
}

String getIssueIdFromRedmine(String customFieldId) {
    String url = _conf("url", true) + "/issues.json"

    def requestParameters = [:]
    requestParameters.key = _conf("apiKey", true)
    requestParameters.put("cf_${customFieldId}".toString(), alertFromOpsgenie.alertId)

    logger.debug("${LOG_PREFIX} executing GET request. Url ${url} params:${requestParameters}")
    def response = ((OpsGenieHttpClient) HTTP_CLIENT).get(url, requestParameters)
    if (response.getStatusCode() < 299) {
        logger.info("${LOG_PREFIX} Successfully executed at Redmine.");
        logger.debug("${LOG_PREFIX} Redmine response: ${response.statusCode} ${response.getContentAsString()}")

        def responseMap = JsonUtils.parse(response.contentAsString)
        if (responseMap.total_count > 0) {
            return responseMap.issues[0].id
        } else {
            logger.warn("${LOG_PREFIX} No issue could be found")
        }
    } else {
        logger.warn("${LOG_PREFIX} Could not execute at Redmine; response: ${response.statusCode} ${response.getContentAsString()}")
    }
    return null;
}

void updateIssueStatusInRedmine(String issueId, def statusId){
    String url = _conf("url", true) + "/issues/${issueId}.json"
    requestParameters = [:]
    requestParameters.key = _conf("apiKey", true)
    def contentParams = [:]
    def issueMap = [:]
    issueMap.status_id = statusId
    contentParams.put("issue", issueMap)

    doPutRequestToRedmine(url, contentParams, requestParameters)
}

void addNoteToRedmineIssue(String issueId, String note){
    String url = _conf("url", true) + "/issues/${issueId}.json"
    requestParameters = [:]
    requestParameters.key = _conf("apiKey", true)
    def contentParams = [:]
    def issueMap = [:]
    issueMap.notes = note
    contentParams.put("issue", issueMap)

    doPutRequestToRedmine(url, contentParams, requestParameters)
}

void createTimeEntryInRedmine(String issueId){
    def hoursSpent = StringUtils.substringAfter(alert.note, ":")
    String url = _conf("url", true) + "/time_entries.json"
    requestParameters = [:]
    requestParameters.key = _conf("apiKey", true)
    def contentParams = [:]
    def timeEntryMap = [:]
    timeEntryMap.issue_id = issueId
    timeEntryMap.hours = hoursSpent
    timeEntryMap.comments = "Time entry created by ${alert.username}".toString()
    contentParams.put("time_entry", timeEntryMap)

    doPostRequestToRedmine(url, contentParams, requestParameters)
}

void doPutRequestToRedmine(String url, Map contentParams, Map requestParameters){
    def put = ((OpsGenieHttpClient) HTTP_CLIENT).preparePutMethod(url, contentParams, requestParameters)
    sendHttpRequestToRedmine(put)
}

void doPostRequestToRedmine(String url, Map contentParams, Map requestParameters){
    def post = ((OpsGenieHttpClient) HTTP_CLIENT).preparePostMethod(url, JsonUtils.toJson(contentParams), [:], requestParameters)
    sendHttpRequestToRedmine(post)
}

void sendHttpRequestToRedmine(def httpMethod){
    ((StringEntity) httpMethod.getEntity()).setChunked(false)
    httpMethod.setHeader("Content-Type", "application/json")
    response = ((OpsGenieHttpClient) HTTP_CLIENT).executeHttpMethod(httpMethod)
    if (response.getStatusCode() < 299) {
        logger.info("${LOG_PREFIX} Successfully executed at Redmine.");
        logger.debug("${LOG_PREFIX} Redmine response: ${response.statusCode} ${response.getContentAsString()}")
    } else {
        logger.warn("${LOG_PREFIX} Could not execute at Redmine; response: ${response.statusCode} ${response.getContentAsString()}")
    }
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
def _conf(confKey, boolean isMandatory)
{
    def confVal = conf[CONF_PREFIX+confKey]
    if(isMandatory && confVal == null){
        def errorMessage = "${LOG_PREFIX} Skipping action, Mandatory Conf item ${CONF_PREFIX+confKey} is missing. Check your marid conf file.";
        throw new Exception(errorMessage);
    }
    return confVal
}