import com.ifountain.opsgenie.client.http.OpsGenieHttpClient
import com.ifountain.opsgenie.client.util.ClientConfiguration
import org.apache.http.auth.UsernamePasswordCredentials

LOG_PREFIX = "[${mappedAction}]:";
logger.warn("${LOG_PREFIX} Will execute [${mappedAction}] for alertId ${params.alertId}");

CONF_PREFIX = "op5.";
HTTP_CLIENT = createHttpClient()

try {
    def comment = params.comment
    def host = params.host_name
    def service = params.service_desc
    def sticky = params.sticky
    def notify = params.notify
    def persistent = params.persistent
    def postParams = ["host_name": host, "sticky": sticky, "notify": notify, "persistent": persistent, "comment": comment]

    if (service) {
        postParams.service_description = service
        postToNagios(postParams, "service");
    } else {
        postToNagios(postParams, "host");
    }

} catch (Exception e) {
    logger.error(e.getMessage(), e)
} finally {
    HTTP_CLIENT.close()
}

def _conf(confKey, boolean isMandatory){
    def confVal = conf[CONF_PREFIX+confKey]
    logger.debug ("confVal ${CONF_PREFIX+confKey} from file is ${confVal}");
    if(isMandatory && confVal == null) {
        def errorMessage = "${LOG_PREFIX} Skipping action, Mandatory Conf item ${CONF_PREFIX+confKey} is missing. Check your marid conf file.";
        logger.warn(errorMessage);
        throw new Exception(errorMessage);
    }
    return confVal
}

def createHttpClient() {
    def timeout = _conf("http.timeout", false);
    if(timeout == null) {
        timeout = 30000;
    }
    else {
        timeout = timeout.toInteger();
    }

    String username = params.username
    String password = params.password

    if (!username) {
        username = _conf("username", true)
    }
    if (!password) {
        password = _conf("password", true)
    }
    ClientConfiguration clientConfiguration = new ClientConfiguration().setSocketTimeout(timeout)
            .setCredentials(new UsernamePasswordCredentials(username, password))
    return new OpsGenieHttpClient(clientConfiguration)
}

def getUrl() {
    def url = params.url
    if (!url) {
        url = _conf("url", true)
    }
    return url + "/api/command/"
}

def postToNagios(Map<String, String> postParams, String typeOfNotification) {
    String url = getUrl();
    if("service".equals(typeOfNotification)) {
        url = url + "ACKNOWLEDGE_SVC_PROBLEM"
    } else if("host".equals(typeOfNotification)) {
        url = url + "ACKNOWLEDGE_HOST_PROBLEM"
    }
    logger.debug("${LOG_PREFIX} Posting to OP5. Url ${url} params:${postParams}")
    def response = ((OpsGenieHttpClient) HTTP_CLIENT).post(url, postParams)
    if (response.getStatusCode() == 200) {
        logger.info("${LOG_PREFIX} Successfully executed at OP5.");
        logger.debug("${LOG_PREFIX} OP5 response: ${response.getContentAsString()}")
    } else {
        logger.error("${LOG_PREFIX} Could not execute at OP5. StatusCode: ${response.getStatusCode()} Response:${response.getContentAsString()}")
    }
}
