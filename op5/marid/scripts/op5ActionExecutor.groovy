import com.ifountain.opsgenie.client.http.OpsGenieHttpClient
import com.ifountain.opsgenie.client.util.ClientConfiguration
import org.apache.commons.lang.StringEscapeUtils
import org.apache.http.HttpHost
import org.apache.http.auth.UsernamePasswordCredentials

import javax.imageio.ImageIO
import java.awt.image.BufferedImage
import java.text.SimpleDateFormat
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

LOG_PREFIX = "[${mappedAction}]:";
logger.warn("${LOG_PREFIX} Will execute [${mappedAction}] for alertId ${params.alertId}");

ImageIO.setUseCache(false)

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
    def nagiosServer = params.nagios_server
    //determine which Nagios server will be used by using the alert details prop nagios_server
    if (!nagiosServer || nagiosServer == "default") {
        CONF_PREFIX = "nagios.";
    } else {
        CONF_PREFIX = "nagios." + nagiosServer + ".";
    }
    logger.info("CONF_PREFIX is ${CONF_PREFIX}");
    //if nagios_server from alert details does exist in this Marid conf file , it should be ignored
    def command_url = _conf("command_url", false);
    if (!command_url) {
        logger.warn("Ignoring action ${action} from nagiosServer ${nagiosServer}, because ${CONF_PREFIX} does not exist in conf file, alert: ${alert.message}");
        return;
    }

    if (service) {
        postParams.service = service
    }

    if (source != null && source.name?.toLowerCase()?.startsWith("op5")) {
        logger.warn("OpsGenie alert is already acknowledged by OP5. Discarding!!!");
    } else {
        if(service != null) {
            postToNagios(postParams, "service");
        } else {
            postToNagios(postParams, "host");
        }
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

    if (username == null || "".equals(username)) {
        username = _conf("username", true)
    }
    if (password == null || "".equals(password)) {
        password = _conf("password", true)
    }
    ClientConfiguration clientConfiguration = new ClientConfiguration().setSocketTimeout(timeout)
            .setCredentials(new UsernamePasswordCredentials(username, password))
    return new OpsGenieHttpClient(clientConfiguration)
}

def getUrl(String confProperty, boolean  isMandatory) {
    def url = params.url
    String commandUrl = _conf(confProperty, isMandatory)
    if (url == null || "".equals(url)) {
        url = _conf("url", true)
        return url + commandUrl
    } else {
        return url + commandUrl;
    }
}

def postToNagios(Map<String, String> postParams, String typeOfNotification) {
    String url = getUrl("command_url", true);
    if("service".equals(typeOfNotification)) {
        url = url + "ACKNOWLEDGE_SVC_PROBLEM"
    } else if("host".equals(typeOfNotification)) {
        url = url + "ACKNOWLEDGE_HOST_PROBLEM"
    }
    logger.debug("${LOG_PREFIX} Posting to Nagios. Url ${url} params:${postParams}")
    def response = ((OpsGenieHttpClient) HTTP_CLIENT).post(url, postParams)
    if (response.getStatusCode() == 200) {
        logger.info("${LOG_PREFIX} Successfully executed at Nagios.");
        logger.debug("${LOG_PREFIX} Nagios response: ${response.getContentAsString()}")
    } else {
        logger.warn("${LOG_PREFIX} Could not execute at Nagios. Nagios Resonse:${response.getContentAsString()}")
    }
}
