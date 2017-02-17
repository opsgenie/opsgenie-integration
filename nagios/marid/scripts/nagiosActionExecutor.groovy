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

LOG_PREFIX = "[${action}]:";
logger.warn("${LOG_PREFIX} Will execute action for alertId ${alert.alertId}");

ImageIO.setUseCache(false)
CONF_PREFIX = "nagios.";
alertFromOpsgenie = opsgenie.getAlert(alertId: alert.alertId)
if (alertFromOpsgenie.size() > 0) {
    def host = alertFromOpsgenie.details.host_name
    def service = alertFromOpsgenie.details.service_desc
    def postParams = ["btnSubmit": "Commit", "cmd_mod": "2", "send_notification": "off", "host": host, "com_author" : "opsgenie"]
    if (service) postParams.service = service;
    boolean discardAction = false;

    //determine which Nagios server will be used by using the alert details prop nagios_server
    def nagiosServer = alertFromOpsgenie.details.nagios_server
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

    HTTP_CLIENT = createHttpClient();
    try {
        if (action == "Create") {
            if (service) {
                attach(alert.alertId, "service")
            } else {
                attach(alert.alertId, "host")
            }
            discardAction = true;
        } else if (action == "Acknowledge") {
            if (source != null && source.name?.toLowerCase()?.startsWith("nagios")) {
                logger.warn("OpsGenie alert is already acknowledged by nagios. Discarding!!!");
                discardAction = true;
            } else {
                postParams.com_data = "Acknowledged by ${alert.username} via OpsGenie"
                postParams.sticky_ack = "on"
                postParams.cmd_typ = service ? "34" : "33";
            }
        } else if (action == "UnAcknowledge") {
            if (source != null && source.name?.toLowerCase()?.startsWith("nagios")) {
                logger.warn("OpsGenie alert is already unacknowledged by Nagios. Discarding!!!");
                discardAction = true;
            } else {
                postParams.cmd_typ = service ? "52" : "51";
            }
        } else if (action == "TakeOwnership") {
            postParams.com_data = "alert ownership taken by ${alert.username}"
            postParams.cmd_typ = service ? "3" : "1";
        } else if (action == "AssignOwnership") {
            postParams.com_data = "alert ownership assigned to ${alert.owner}"
            postParams.cmd_typ = service ? "3" : "1";
        } else if (action == "AddNote") {
            postParams.com_data = "${alert.note} by ${alert.username}"
            postParams.cmd_typ = service ? "3" : "1";
        }

        if (!discardAction) {
            postToNagios(postParams);
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

def getUrl(String confProperty, String backwardCompatabilityUrl, boolean  isMandatory) {
    def url = _conf(confProperty, isMandatory)
    if (url != null) {
        return url;
    } else {
        //backward compatability
        def scheme =  _conf("http.scheme", false)
        if (scheme == null) scheme = "http";
        def port = _conf("port", true).toInteger();
        def host = _conf("host", true);
        return new HttpHost(host, port, scheme).toURI() + backwardCompatabilityUrl;
    }
}

def postToNagios(Map<String, String> postParams) {
    String url = getUrl("command_url", "/nagios/cgi-bin/cmd.cgi", true);
    logger.debug("${LOG_PREFIX} Posting to Nagios. Url ${url} params:${postParams}")
    def response = ((OpsGenieHttpClient) HTTP_CLIENT).post(url, postParams)
    if (response.getStatusCode() == 200) {
        logger.info("${LOG_PREFIX} Successfully executed at Nagios.");
        logger.debug("${LOG_PREFIX} Nagios response: ${response.getContentAsString()}")
    } else {
        logger.warn("${LOG_PREFIX} Could not execute at Nagios. Nagios Resonse:${response.getContentAsString()}")
    }
}

def attach(alertId, entity) {
    // will get alert histogram and trends charts from nagios and attach them to the alert
    try {
        def alertHistogram = getAlertHistogram(entity);
        def trends = getTrends(entity);
        def htmlText = createHtml(entity, alertHistogram, trends)
        ByteArrayOutputStream bout = createZip(htmlText, alertHistogram, trends)
        logger.warn("Attaching details");
        println "Attaching details"

        String fileDate = new SimpleDateFormat("yyyy_MM_dd_HH_mm_ss").format(new Date());
        String fileName = "details_${fileDate}.zip";
        response = opsgenie.attach([alertId: alertId, stream: new ByteArrayInputStream(bout.toByteArray()), fileName: fileName])
        if (response.success) {
            logger.warn("Successfully attached details ${fileName}");
            println "Successfully attached details"
        } else {
            println "Could not attach details"
            logger.warn("Could not attach details ${fileName}");
        }
    }
    catch (e) {
        logger.warn("Could not attach details. Reason: ${e}", e)
        println "Could not attach details. Reason: ${e}"
    }
}

def createZip(html, alertHistogram, trends) {
    ByteArrayOutputStream bout = new ByteArrayOutputStream();
    ZipOutputStream zout = new ZipOutputStream(bout);
    ZipEntry htmlEntry = new ZipEntry("index.html");
    zout.putNextEntry(htmlEntry);
    zout.write(html.getBytes())
    zout.closeEntry()
    if (alertHistogram) {
        ZipEntry imageEntry = new ZipEntry("alertHistogram.png");
        zout.putNextEntry(imageEntry);
        BufferedImage img = ImageIO.read(new ByteArrayInputStream(alertHistogram))
        ImageIO.write(img, "png", zout);
        zout.closeEntry()
    }
    if (trends) {
        ZipEntry imageEntry = new ZipEntry("trends.png");
        zout.putNextEntry(imageEntry);
        BufferedImage img = ImageIO.read(new ByteArrayInputStream(trends))
        ImageIO.write(img, "png", zout);
        zout.closeEntry()
    }
    zout.close();
    return bout;
}

def createHtml(entity, alertHistogram, trends) {
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
    if (entity == "host") {
        getHostStatusHtml(buf)
    } else {
        getServicesStatusHtml(buf)
    }
    if (trends) {
        buf.append('<div class="img"><img src="trends.png"></div>')
    }
    if (alertHistogram) {
        buf.append('<div class="img"><img src="alertHistogram.png"></div>')
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

def getAlertHistogram(entity) {
    String url = getUrl("alert_histogram_image_url", "/nagios/cgi-bin/histogram.cgi", false)
    return getImage(url, entity);
}

def getTrends(entity) {
    String url = getUrl("trends_image_url", "/nagios/cgi-bin/trends.cgi", false)
    return getImage(url, entity);
}

def getImage(url, entity) {
    def host = alertFromOpsgenie.details.host_name
    url += "?createimage&host=" + URLEncoder.encode(host)
    if (entity == "service") {
        def service = alertFromOpsgenie.details.service_desc
        url += "&service=" + URLEncoder.encode(service)
    }
    logger.warn("Sending request to url:" + url)
    def response = HTTP_CLIENT.get(url, [:])
    def code = response.getStatusCode()
    if (code == 200) {
        logger.warn("Image received");
        println "Image received"
        return response.getContent();
    } else {
        def content = response.getContentAsString()
        logger.warn("Could not get image from url ${url}. ResponseCode:${code} Reason:" + content)
        println "Could not get image from url ${url}. ResponseCode:${code} Reason:" + content
        return null;
    }
}

def htmlEscape(value) {
    return StringEscapeUtils.escapeHtml(value)
}
