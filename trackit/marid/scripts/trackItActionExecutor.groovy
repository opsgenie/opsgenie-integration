import com.ifountain.opsgenie.client.http.OpsGenieHttpClient
import com.ifountain.opsgenie.client.util.JsonUtils
import com.ifountain.opsgenie.client.util.ClientConfiguration
import org.apache.http.auth.UsernamePasswordCredentials

CONF_PREFIX = "trackit."
LOG_PREFIX = "[${action}]:";
logger.info("${LOG_PREFIX} Will execute action for alertId ${alert.alertId}");

alertFromOpsgenie = opsgenie.getAlert(alertId: alert.alertId)
HTTP_CLIENT = createHttpClient()
try{
    String trackKey = loginToTrackIt()
    if (alertFromOpsgenie.size() > 0) {
            String workflowId = alertFromOpsgenie.details.workflow_id;

        if (workflowId) {
                String message;
                if (action == "Acknowledge") {
                    message = alert.username + " acknowledged alert: \"" + alert.message + "\"";
                } else if (action == "AddNote") {
                    message = alert.username + " noted: \"" + alert.note + "\" on alert: \"" + alert.message + "\"";
                } else if (action == "AddRecipient") {
                    message = alert.username + " added recipient " + alert.recipient + " to alert: \"" + alert.message + "\"";
                } else if (action == "AddTeam") {
                    message = alert.username + " added team " + alert.team + " to alert: \"" + alert.message + "\"";
                } else if (action == "AssignOwnership") {
                    message = alert.username + " assigned ownership of the alert: \"" + alert.message + "\" to " + alert.owner;
                } else if (action == "TakeOwnership") {
                    message = alert.username + " took ownership of the alert: \"" + alert.message + "\"";
                } else{
                    message = alert.username + " executed [" + action + "] action on alert: \"" + alert.message + "\"";
                }
                if(action != "Close"){
                    addNoteToWorkflow(message, workflowId, trackKey)
                }
                else {
                    closeWorkflow(workflowId, trackKey)
                }
            } else {
                logger.warn("${LOG_PREFIX} Cannot send action to Track-It because workflow_id is not found on alert")
            }
    } else {
        logger.warn("${LOG_PREFIX} Alert with id [${alert.alertId}] does not exist in OpsGenie. It is probably deleted.")
    }
}
finally {
    HTTP_CLIENT.close()
}
void closeWorkflow(String workflowId, String trackKey) {
    try {
        String url = _conf("url", true) + "/TrackitWeb/api/workorder/Close/${workflowId}";
        def requestParameters = [:]
        def requestHeaders = ["Content-Type" : "text/json",
                              "Accept"       : "text/json",
                              "TrackitAPIKey": trackKey];

        def contentArray = [:]
        def builder = new groovy.json.JsonBuilder(contentArray);
        String content = builder.toString();

        def post = ((OpsGenieHttpClient) HTTP_CLIENT).preparePostMethod(url, content, requestHeaders, requestParameters);
        sendHttpRequestToTrackIt(post);

    }finally {
        HTTP_CLIENT.close();
    }

}
void addNoteToWorkflow(String message,String workflowId, String trackKey){
    try {
        String url = _conf("url", true) + "/TrackitWeb/api/workorder/AddNote/${workflowId}";
        def requestParameters = [:]
        def requestHeaders = ["Content-Type" : "text/json",
                              "Accept"       : "text/json",
                              "TrackitAPIKey": trackKey];

        def contentArray = [
                "IsPrivate": "False",
                "FullText": message
        ]
        def builder = new groovy.json.JsonBuilder(contentArray);
        String content = builder.toString();

        logger.debug("Before Post-> Url: ${url}, Content: ${content}, Request Headers: ${requestHeaders}" );
        def post = ((OpsGenieHttpClient) HTTP_CLIENT).preparePostMethod(url, content, requestHeaders, requestParameters);
        sendHttpRequestToTrackIt(post);

    }finally {
        HTTP_CLIENT.close();
    }

}

void sendHttpRequestToTrackIt(def httpMethod) {

    def response = ((OpsGenieHttpClient) HTTP_CLIENT).executeHttpMethod(httpMethod)
    if (response.getStatusCode() < 299) {
        logger.info("${LOG_PREFIX} Successfully executed at TrackIt.");
        logger.debug("${LOG_PREFIX} TrackIt response: ${response.content}")
    } else {
        logger.warn("${LOG_PREFIX} Could not execute at TrackIt; response: ${response.statusCode} ${response.getContentAsString()}")
    }
}
String loginToTrackIt() {
    String url =_conf("url", true)  + "/TrackitWeb/api/login?username="+ _conf("login", true) +"&pwd=" + _conf("password", true);
    def requestParameters = [:]
    logger.debug ("Url ${url}")
    def response = HTTP_CLIENT.get(url, requestParameters)
    def responseMap = JsonUtils.parse(response.getContentAsString())
    logger.debug ("Response: ${responseMap}")
    if(responseMap != null){
        Map data = responseMap.get("data")
        String apiKey = (String) data.get("apiKey")
        return apiKey;
    }
    return null
}
def createHttpClient() {
    def timeout = _conf("http.timeout", false);
    if (timeout == null) {
        timeout = 30000;
    } else {
        timeout = timeout.toInteger();
    }

    String username = _conf("login", true)
    String password = _conf("password", true)
    String url = _conf("url", true)

    ClientConfiguration clientConfiguration = new ClientConfiguration().setSocketTimeout(timeout);
    clientConfiguration.setCredentials(new UsernamePasswordCredentials(username, password));

    return new OpsGenieHttpClient(clientConfiguration);
}


def _conf(confKey, boolean isMandatory) {
    def confVal = conf[CONF_PREFIX + confKey]
    if (isMandatory && confVal == null) {
        def errorMessage = "${LOG_PREFIX} Skipping action, Mandatory Conf item ${CONF_PREFIX + confKey} is missing. Check your marid conf file.";
        throw new Exception(errorMessage);
    }
    return confVal
}

return;
