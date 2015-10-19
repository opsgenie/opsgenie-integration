import com.ifountain.opsgenie.client.http.OpsGenieHttpClient
import com.ifountain.opsgenie.client.util.ClientConfiguration
import com.ifountain.opsgenie.client.util.JsonUtils
import org.apache.http.HttpHeaders
import org.apache.http.auth.UsernamePasswordCredentials
import org.apache.http.entity.StringEntity
import org.apache.http.impl.auth.BasicScheme

LOG_PREFIX = "[${action}]:";
logger.warn("${LOG_PREFIX} Will execute action for alertId ${alert.alertId}");

CONF_PREFIX = "op5.";
alertFromOpsgenie = opsgenie.getAlert(alertId: alert.alertId)

OP5_URL = "ACKNOWLEDGE_HOST_PROBLEM"

//Content will be posted to Op5
def sticky = 0
def notify = false
def persistent = false
protocol = "https"

try{
    if (alertFromOpsgenie.size() > 0) {
        def host = alertFromOpsgenie.details.host_name
        def service = alertFromOpsgenie.details.service_desc

        def postParams = [sticky: sticky, notify: notify, persistent: persistent, host_name: host]
        if (service) postParams.service_description = service;

        boolean discardAction = false;

        //determine which Nagios server will be used by using the alert details prop nagios_server
        def nagiosServer = alertFromOpsgenie.details.nagios_server
        if (!nagiosServer || nagiosServer == "default") {
            CONF_PREFIX = "op5.";
        } else {
            CONF_PREFIX = "op5." + nagiosServer + ".";
        }
        logger.info("CONF_PREFIX is ${CONF_PREFIX}");

        HTTP_CLIENT = createHttpClient();

        if (action == "Acknowledge") {
            if(source != null && source.name?.toLowerCase()?.startsWith("nagios")){
                logger.warn("OpsGenie alert is already acknowledged by op5. Discarding!!!");
                discardAction = true;
            }
            else{
                if(service){
                    OP5_URL = "ACKNOWLEDGE_SVC_PROBLEM"
                }
                postParams.comment = "Acknowledged by " + alert.username + " via OpsGenie"
            }
        }

        if(!discardAction){
            postToOP5(postParams);
        }
    } else {
        logger.warn("${LOG_PREFIX} Alert with id [${alert.alertId}] does not exist in OpsGenie. It is probably deleted.")
    }
}
finally {
    HTTP_CLIENT.close()
}

def _conf(confKey, boolean isMandatory){
    def confVal = conf[CONF_PREFIX+confKey]
    logger.debug ("confVal ${CONF_PREFIX+confKey} from file is ${confVal}");
    if(isMandatory && confVal == null){
        def errorMessage = "${LOG_PREFIX} Skipping action, Mandatory Conf item ${CONF_PREFIX+confKey} is missing. Check your marid conf file.";
        logger.warn(errorMessage);
        throw new Exception(errorMessage);
    }
    return confVal
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

def postToOP5(Map<String, Object> postParams) {
    def host = _conf("host", true);
    def port = _conf("port", false)
    String url = "${protocol}://${host}"
    if(port){
        url += ":${port.toInteger()}"
    }
    url += "/api/command/${OP5_URL}"
    logger.debug("${LOG_PREFIX} Posting to Op5. Url ${url} params:${postParams}")

    def postMethod = ((OpsGenieHttpClient) HTTP_CLIENT).preparePostMethod(url,JsonUtils.toJson(postParams),[:],[:])
    StringEntity entity = postMethod.getEntity() as StringEntity
    entity.setChunked(false);
    postMethod.addHeader(HttpHeaders.CONTENT_TYPE,"application/json")
    def creds = new UsernamePasswordCredentials(_conf("user",true), _conf("password", true))
    postMethod.addHeader(BasicScheme.authenticate(creds,"US-ASCII",false))
    def response = HTTP_CLIENT.executeHttpMethod(postMethod)
    if (response.getStatusCode() == 200) {
        logger.info("${LOG_PREFIX} Successfully executed at Op5.");
        logger.debug("${LOG_PREFIX} Op5 response: ${response.getContentAsString()}")
    } else {
        logger.warn("${LOG_PREFIX} Could not execute at Op5. Op5 Resonse:${response.getContentAsString()}")
    }
}



