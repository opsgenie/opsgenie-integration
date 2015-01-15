import com.ifountain.opsgenie.client.http.OpsGenieHttpClient
import com.ifountain.opsgenie.client.util.ClientConfiguration
import com.ifountain.opsgenie.client.util.JsonUtils
import org.apache.http.HttpHeaders
import org.apache.http.auth.UsernamePasswordCredentials
import org.apache.http.client.methods.HttpPost
import org.apache.http.entity.StringEntity
import org.apache.http.impl.auth.BasicScheme

LOG_PREFIX = "[${action}]:";
logger.warn("${LOG_PREFIX} Will execute action for alertId ${alert.alertId}");

CONF_PREFIX = "zenoss.";

def alertFromOpsgenie = opsgenie.getAlert(alertId: alert.alertId)
if (alertFromOpsgenie.size() > 0) {
    def postParams = [action: "EventsRouter", data: [[evids: [alertFromOpsgenie.alias]]], type: "rpc", tid: alertFromOpsgenie.alertId]
    boolean discardAction = false;
    if (action == "Acknowledge") {
        if (source != null && source.name?.toLowerCase() == "zenoss") {
            logger.warn("OpsGenie alert is already acknowledged by zenoss. Discarding!!!");
            discardAction = true
        } else {
            postParams.method = "acknowledge"
        }
    } else if (action == "Close") {
        if (source != null && source.name?.toLowerCase() == "zenoss") {
            logger.warn("OpsGenie alert is already closed by zenoss. Discarding!!!");
            discardAction = true
        } else {
            postParams.method = "close"
        }
    }

    if(!discardAction){
        postToZenoss(postParams)
    }

} else {
    logger.warn("${LOG_PREFIX} Alert with id [${alert.alertId}] does not exist in OpsGenie. It is probably deleted.")
}

def postToZenoss(Map<String, Object> postParams) {
    OpsGenieHttpClient HTTP_CLIENT = createHttpClient();
    try {
        String url = _conf("command_url", true)
        logger.debug("${LOG_PREFIX} Posting to Zenoss. Url ${url} params:${postParams}")

        Map contentTypeHeader = [:]
        contentTypeHeader[HttpHeaders.CONTENT_TYPE] = "application/json"
        def postMethod = ((OpsGenieHttpClient) HTTP_CLIENT).preparePostMethod(url,JsonUtils.toJson(postParams),contentTypeHeader,[:])
        StringEntity entity = postMethod.getEntity() as StringEntity
        entity.setChunked(false);
        postMethod.addHeader(HttpHeaders.CONTENT_TYPE,"application/json")
        def creds = new UsernamePasswordCredentials(_conf("username",true), _conf("password", true))
        postMethod.addHeader(BasicScheme.authenticate(creds,"US-ASCII",false))
        def response = HTTP_CLIENT.executeHttpMethod(postMethod)
        if (response.getStatusCode() == 200) {
            logger.info("${LOG_PREFIX} Successfully executed at Zenoss.");
            logger.debug("${LOG_PREFIX} Zenoss response: ${response.getContentAsString()}")
        } else {
            logger.error("${LOG_PREFIX} Could not execute at Zenoss; reason:${response.getContentAsString()}");
        }
    }
    finally {
        HTTP_CLIENT.close();
    }
}

def createHttpClient() {
    def timeout = _conf("http.timeout", false);
    if (timeout == null) {
        timeout = 30000;
    } else {
        timeout = timeout.toInteger();
    }
    ClientConfiguration clientConfiguration = new ClientConfiguration().setSocketTimeout(timeout)
    return new OpsGenieHttpClient(clientConfiguration)
}

String _conf(confKey, boolean isMandatory) {
    def confVal = conf[CONF_PREFIX + confKey]
    if (isMandatory && confVal == null) {
        def errorMessage = "${LOG_PREFIX} Skipping action, Mandatory Conf item ${CONF_PREFIX + confKey} is missing. Check your marid conf file.";
        logger.warn(errorMessage);
        throw new Exception(errorMessage);
    }
    return confVal
}