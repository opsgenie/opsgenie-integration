import com.ifountain.opsgenie.client.http.OpsGenieHttpClient
import com.ifountain.opsgenie.client.util.JsonUtils
import com.ifountain.opsgenie.client.util.ClientConfiguration
import org.apache.http.auth.UsernamePasswordCredentials

CONF_PREFIX = "trackit."
LOG_PREFIX = "[${action}]:"
logger.warn("${LOG_PREFIX} Alert: AlertId:[${alert.alertId}] Note:[${alert.note}] Source: [${source}]");

HTTP_CLIENT = createHttpClient()
try{
    String trackKey = loginToTrackIt()
    if (action == "Create") {
        message = alert.message;
        createWorkflow(message, trackKey);
    }

}finally {
    HTTP_CLIENT.close();

}
void createWorkflow( String message, String trackKey) {
    try {
        String url = _conf("url", true) + "/TrackitWeb/api/workorder/Create";
        def requestParameters = [:]
        def requestHeaders = ["Content-Type" : "text/json",
                              "Accept"       : "text/json",
                              "TrackitAPIKey": trackKey];

        def contentArray = [
                "StatusName": "Open",
                "Summary"   : message,
                "RequestorName": _conf("login", true)
        ]
        def builder = new groovy.json.JsonBuilder(contentArray);
        String content = builder.toString();

        logger.debug("Before Post-> Url: ${url}, Content: ${content}, Request Headers: ${requestHeaders}" );
        def post = ((OpsGenieHttpClient) HTTP_CLIENT).preparePostMethod(url, content, requestHeaders, requestParameters);
        def responseMap = sendHttpRequestToTrackIt(post);

        if (responseMap != null) {
            Map data = responseMap.get("data")
            Map data2 = data.get("data")
            def flowId = data2.get("Id")
            opsgenie.addDetails(["id": alert.alertId, "details": ["workflow_id": flowId]])
        }
    }finally {
        HTTP_CLIENT.close();
    }

}

Map sendHttpRequestToTrackIt(def httpMethod) {
    logger.debug("Content: ${httpMethod}" );
    def response = ((OpsGenieHttpClient) HTTP_CLIENT).executeHttpMethod(httpMethod)
    if (response.getStatusCode() < 299) {
        logger.info("${LOG_PREFIX} Successfully executed at TrackIt.");
        logger.debug("${LOG_PREFIX} TrackIt response: ${response.statusCode} - ${response.content}")
       return JsonUtils.parse(response.content)
    } else {
        logger.error("${LOG_PREFIX} Could not execute at TrackIt; response: ${response.statusCode} ${response.getContentAsString()}")
        return null
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
