import com.ifountain.opsgenie.client.http.OpsGenieHttpClient
import com.ifountain.opsgenie.client.util.ClientConfiguration
import com.ifountain.opsgenie.client.util.JsonUtils
import org.apache.commons.lang.StringEscapeUtils
import org.apache.commons.lang.StringUtils
import org.apache.http.HttpHeaders
import org.apache.http.auth.UsernamePasswordCredentials

import javax.imageio.ImageIO
import java.awt.image.BufferedImage
import java.text.SimpleDateFormat
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

LOG_PREFIX = "[${action}]:";
logger.warn("${LOG_PREFIX} Will execute action for alertId ${alert.alertId}");

ImageIO.setUseCache(false)
CONF_PREFIX = "icinga.";
alertFromOpsgenie = opsgenie.getAlert(alertId: alert.alertId)
if (alertFromOpsgenie.size() > 0) {
    String host = alertFromOpsgenie.details.host_name
    String service = alertFromOpsgenie.details.service_desc
    boolean isServiceAlert = StringUtils.isNotBlank(service)
    def contentMap = [:]
    def urlPath = ""
    if (isServiceAlert){
        contentMap.type = "Service"
        contentMap.filter = "host.name==\"${host}\" && service.name==\"${service}\"".toString()
    } else{
        contentMap.type = "Host"
        contentMap.filter = "host.name==\"${host}\"".toString()
    }

    boolean discardAction = false;

    //determine which icinga server will be used by using the alert details prop icinga_server
    def icingaServer = alertFromOpsgenie.details.icinga_server
    if (!icingaServer || icingaServer == "default") {
        CONF_PREFIX = "icinga.";
    } else {
        CONF_PREFIX = "icinga." + icingaServer + ".";
    }
    logger.info("CONF_PREFIX is ${CONF_PREFIX}");

    //if icinga_server from alert details does exist in this Marid conf file , it should be ignored
    def apiUrl = _conf("api_url", false);
    if (!apiUrl) {
        logger.warn("Ignoring action ${action} from icingaServer ${icingaServer}, because ${CONF_PREFIX} does not exist in conf file, alert: ${alert.message}");
        return;
    }

    HTTP_CLIENT = createHttpClient();
    try {
        if (action == "Create") {
            attach(isServiceAlert)
            discardAction = true;
        } else if (action == "Acknowledge") {
            if (source != null && source.name?.toLowerCase()?.startsWith("icinga")) {
                logger.warn("OpsGenie alert is already acknowledged by icinga. Discarding!!!");
                discardAction = true;
            } else {
                urlPath = "/v1/actions/acknowledge-problem"
                contentMap.put("comment", String.valueOf("Acknowledged by ${alert.username} via OpsGenie"))
                contentMap.put("author", alert.username)
                contentMap.put("notify", true)
                contentMap.put("sticky", true)
            }
        } else if (action == "TakeOwnership") {
            urlPath = "/v1/actions/add-comment"
            contentMap.put("comment", String.valueOf("alert ownership taken by ${alert.username}"))
            contentMap.put("author", "OpsGenie")
        } else if (action == "AssignOwnership") {
            urlPath = "/v1/actions/add-comment"
            contentMap.put("comment", String.valueOf("alert ownership assigned to ${alert.owner}"))
            contentMap.put("author", "OpsGenie")
        } else if (action == "AddNote") {
            urlPath = "/v1/actions/add-comment"
            contentMap.put("comment", String.valueOf("${alert.note} by ${alert.username}"))
            contentMap.put("author", "OpsGenie")
        }

        if (!discardAction) {
            postToIcingaApi(urlPath, contentMap);
        }
    }
    finally {
        HTTP_CLIENT.close()
    }
} else {
    logger.warn("${LOG_PREFIX} Alert with id [${alert.alertId}] does not exist in OpsGenie. It is probably deleted.")
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
            .setCredentials(new UsernamePasswordCredentials(_conf("user", true), _conf("password", true)))
    return new OpsGenieHttpClient(clientConfiguration)
}

def postToIcingaApi(String urlPath, Map contentMap) {
    String url = _conf("api_url", true) + urlPath;
    String jsonContent = JsonUtils.toJson(contentMap);
    logger.debug("${LOG_PREFIX} Posting to Icinga. Url ${url}, content:${jsonContent} , conmap:${contentMap}")
    def httpPost = ((OpsGenieHttpClient) HTTP_CLIENT).preparePostMethod(url, jsonContent, [(HttpHeaders.ACCEPT): "application/json", (HttpHeaders.CONTENT_TYPE): "application/json"], [:])
    def response = ((OpsGenieHttpClient) HTTP_CLIENT).executeHttpMethod(httpPost)
    if (response.getStatusCode() == 200) {
        logger.info("${LOG_PREFIX} Successfully executed at Icinga.");
        logger.debug("${LOG_PREFIX} Icinga response: ${response.getContentAsString()}")
    } else {
        logger.warn("${LOG_PREFIX} Could not execute at Icinga. Icinga Response:${response.getContentAsString()}")
    }
}

def attach(boolean isServiceAlert) {
    // will get performance data from Icinga and attach it to the alert
    try {
        def perfData = getPerfData(isServiceAlert)
        def htmlText = createHtml(isServiceAlert, perfData)
        ByteArrayOutputStream bout = createZip(htmlText, perfData)
        logger.info("Attaching details");

        String fileDate = new SimpleDateFormat("yyyy_MM_dd_HH_mm_ss").format(new Date());
        String fileName = "details_${fileDate}.zip";
        response = opsgenie.attach([alertId: alert.alertId, stream: new ByteArrayInputStream(bout.toByteArray()), fileName: fileName])
        if (response.success) {
            logger.info("Successfully attached details ${fileName}");
        } else {
            logger.error("Could not attach details ${fileName}");
        }
    }
    catch (e) {
        logger.error("Could not attach details. Reason: ${e}", e)
    }
}

def createZip(html, perfData) {
    ByteArrayOutputStream bout = new ByteArrayOutputStream();
    ZipOutputStream zout = new ZipOutputStream(bout);
    ZipEntry htmlEntry = new ZipEntry("index.html");
    zout.putNextEntry(htmlEntry);
    zout.write(html.getBytes())
    zout.closeEntry()
    if (perfData) {
        ZipEntry imageEntry = new ZipEntry("perfData.png");
        zout.putNextEntry(imageEntry);
        BufferedImage img = ImageIO.read(new ByteArrayInputStream(perfData))
        ImageIO.write(img, "png", zout);
        zout.closeEntry()
    }
    zout.close();
    return bout;
}

def createHtml(boolean isServiceAlert, perfData) {
    StringBuffer buf = new StringBuffer()
    buf.append("""
        <html>
            <head>
                <style>
                    .well{border: 1px solid #C0C0C0; border-radius: 4px; padding: 5px;background-color:#f2f2f2}
                    .CRITICAL{background-color: #F88888;border: 1px solid #777;font-weight: bold;}
                    .OK{ background-color: #88d066; border: 1px solid #777777; font-weight: bold;}
                    .WARNING{ background-color: #ffff00; border: 1px solid #777777; font-weight: bold;}
                    .UNKNOWN{ background-color: #ffbb55; border: 1px solid #777777; font-weight: bold;}
                    .img{margin:20px 0;}
                </style>
            </head>
            <body>
                <div>
    """)
    if (isServiceAlert) {
        getServicesStatusHtml(buf)
    } else {
        getHostStatusHtml(buf)
    }
    if (perfData) {
        buf.append('<div class="img"><img src="perfData.png"></div>')
    }
    buf.append("""
                </div>
            </body>
        </html>
    """)
    return buf.toString();
}

def getServicesStatusHtml(buf) {
    SimpleDateFormat dateFormatter = new SimpleDateFormat("MM-dd-yyyy hh:mm:ss")
    def serviceGroup = alertFromOpsgenie.details.service_group_name
    def memberOf = serviceGroup ? serviceGroup : 'No service groups'
    def state = alertFromOpsgenie.details.service_state
    def lastServiceCheck = alertFromOpsgenie.details.last_service_check
    def lastStateChange = alertFromOpsgenie.details.last_service_state_change
    try {
        lastServiceCheck = dateFormatter.format(Long.parseLong(lastServiceCheck) * 1000L)
    } catch (Throwable e) {
    }

    try {
        lastStateChange = dateFormatter.format(Long.parseLong(lastStateChange) * 1000L)
    } catch (Throwable e) {
    }

    buf.append("""
        <div class="well">
            <table>
                <tbody>
                    <tr><td><b>Service:</b></td><td>${htmlEscape(alertFromOpsgenie.details.service_desc)}</td></tr>
                    <tr><td><b>Host:</b></td><td>${htmlEscape(alertFromOpsgenie.details.host_alias)} (${
        htmlEscape(alertFromOpsgenie.details.host_name)
    })</td></tr>
                    <tr><td><b>Address:</b></td><td>${htmlEscape(alertFromOpsgenie.details.host_address)}</td></tr>
                    <tr><td><b>Member of:</b></td><td>${htmlEscape(memberOf)}</td></tr>
                    <tr><td><b>Current Status:</b></td><td><span class="${state}">${state}</span> for (${
        alertFromOpsgenie.details.service_duration
    })</td></tr>
                    <tr><td><b>Status Information:</b></td><td>${htmlEscape(alertFromOpsgenie.details.service_output)}</td></tr>
                    <tr><td><b>Performance Data:</b></td><td>${htmlEscape(alertFromOpsgenie.details.service_perf_data)}</td></tr>
                    <tr><td><b>Current Attempt:</b></td><td>${alertFromOpsgenie.details.service_attempt}/${
        alertFromOpsgenie.details.max_service_attempts
    } (${alertFromOpsgenie.details.service_state_type} state)</td></tr>
                    <tr><td><b>Last Check Time:</b></td><td>${lastServiceCheck}</td></tr>
                    <tr><td><b>Check Latency:</b></td><td>${alertFromOpsgenie.details.service_latency}</td></tr>
                    <tr><td><b>Last State Change:</b></td><td>${lastStateChange}</td></tr>
                </tbody>
            </table>
        </div>
   """)
}

def getHostStatusHtml(buf) {
    SimpleDateFormat dateFormatter = new SimpleDateFormat("MM-dd-yyyy hh:mm:ss")
    def hostGroup = alertFromOpsgenie.details.host_group_name;
    def memberOf = hostGroup ? hostGroup : 'No host groups'
    def state = alertFromOpsgenie.details.host_state;
    def lastCheckTime = alertFromOpsgenie.details.last_host_check;
    def lastStateChange = alertFromOpsgenie.details.last_host_state_change;
    try {
        lastCheckTime = dateFormatter.format(Long.parseLong(lastCheckTime) * 1000L)
    } catch (Throwable e) {
    }
    try {
        lastStateChange = dateFormatter.format(Long.parseLong(lastStateChange) * 1000L)
    } catch (Throwable e) {
    }
    buf.append("""
        <div class="well">
            <table>
                <tbody>
                    <tr><td><b>Host:</b></td><td>${htmlEscape(alertFromOpsgenie.details.host_alias)} (${
        htmlEscape(alertFromOpsgenie.details.host_name)
    })</td></tr>
                    <tr><td><b>Address:</b></td><td>${htmlEscape(alertFromOpsgenie.details.host_address)}</td></tr>
                    <tr><td><b>Member of:</b></td><td>${htmlEscape(memberOf)}</td></tr>
                    <tr><td><b>Current Status:</b></td><td><span class="${state}">${state}</span> for (${
        alertFromOpsgenie.details.host_duration
    })</td></tr>
                    <tr><td><b>Status Information:</b></td><td>${htmlEscape(alertFromOpsgenie.details.host_output)}</td></tr>
                    <tr><td><b>Performance Data:</b></td><td>${htmlEscape(alertFromOpsgenie.details.host_perf_data)}</td></tr>
                    <tr><td><b>Current Attempt:</b></td><td>${alertFromOpsgenie.details.host_attempt}/${
        alertFromOpsgenie.details.max_host_attempts
    } (${alertFromOpsgenie.details.host_state_type} state)</td></tr>
                    <tr><td><b>Last Check Time:</b></td><td>${lastCheckTime}</td></tr>
                    <tr><td><b>Check Latency:</b></td><td>${alertFromOpsgenie.details.host_latency}</td></tr>
                    <tr><td><b>Last State Change:</b></td><td>${lastStateChange}</td></tr>
                </tbody>
            </table>
        </div>
   """)
}

def getPerfData(boolean isServiceAlert) {
    String graphiteUrl = _conf("graphite_url", false);
    if (StringUtils.isBlank(graphiteUrl)) {
        logger.warn("Could not get performance data because graphite_url is not configured.")
        return null
    }
    String targetParam;
    String host = alertFromOpsgenie.details.host_name;
    if (isServiceAlert) {
        String service = alertFromOpsgenie.details.service_desc
        targetParam = "icinga2." + host + ".services." + service + ".*.perfdata.*.*"
    } else {
        targetParam = "icinga2." + host + ".host.*.perfdata.*.*"
    }
    logger.debug("Sending to ${graphiteUrl} target: ${targetParam}")
    def response = HTTP_CLIENT.get(graphiteUrl + "/render", ["target": targetParam])
    int code = response.getStatusCode()
    if (code == 200) {
        logger.info("Image received");
        return response.getContent();
    } else {
        def content = response.getContentAsString()
        logger.error("Could not get image from url ${url}. ResponseCode:${code} Reason:" + content)
        return null
    }
}

def htmlEscape(value) {
    return StringEscapeUtils.escapeHtml(value)
}
