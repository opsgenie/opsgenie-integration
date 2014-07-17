import com.ifountain.opsgenie.client.http.OpsGenieHttpClient
import com.ifountain.opsgenie.client.util.ClientConfiguration
import org.apache.commons.lang.StringEscapeUtils
import org.apache.http.HttpHost

import javax.imageio.ImageIO
import java.awt.image.BufferedImage
import java.text.SimpleDateFormat
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

SOURCE = "Nagios"
RECIPIENTS = conf["nagios.recipients"]
NAGIOS_SERVER = conf["nagios.nagiosServer"]

logger.warn("ogCreateAlert params:${params}")
if (!params.notificationType) {
    logger.warn("Stopping, Nagios notificationType param has no value, please make sure your Nagios and OpsGenie files pass necessary parameters");
    return;
}
HTTP_CLIENT = createHttpClient();
try {
    def entity = params.entity;
    def action = null;
    def attachFiles = conf["nagios.attachFiles"] == "true"
// whether to close existing alerts for resolve (Up/OK) events create a separate alert
    def autoCloseAlert = true
    def autoAckAlert = true

    if (entity == "host" || entity == "service") {
        def alertProps = [:]
        def alias

        alertProps.message = params.subject
        alertProps.recipients = RECIPIENTS
        alertProps.description = params.textMessage
        alertProps.source = SOURCE
        def notificationType = params.notificationType
        logger.warn("notificationType: ${notificationType}")

        def hostAddress = params.hostAddress
        def dateTime = params.longDateTime
        if (entity == "host") {
            def hostName = params.hostName
            def hostState = params.hostState
            alias = hostName
            if(autoAckAlert && hostState == "DOWN" && notificationType == "ACKNOWLEDGEMENT"){
                action = "ackAlert"
            }
            else if(autoCloseAlert && hostState == "UP" && notificationType == "RECOVERY"){
                action = "closeAlert"
            }
            else if((hostState == "DOWN" || hostState == "UP") && (notificationType == "PROBLEM" || notificationType == "RECOVERY" || notificationType == "ACKNOWLEDGEMENT")){
                action = "createAlert"
                alertProps.alias = alias
                alertProps.details = ["host": hostName]
                alertProps.message = "** ${notificationType} Host Alert: ${hostName} is ${hostState} **"
                alertProps.description = """***** Nagios *****

Notification Type: ${notificationType}
Host: ${hostName}
State: ${hostState}
Address: ${hostAddress}
Additional Info: ${params.hostOutput}
Date/Time: ${dateTime}
"""
            }
        } else {
            def service = params.serviceDesc
            def serviceState = params.serviceState
            def hostAlias = params.hostAlias
            def hostName = params.hostName
            alias = hostName + "_" + service
            logger.warn("service state: ${serviceState}")
            if(autoAckAlert && serviceState == "CRITICAL" && notificationType == "ACKNOWLEDGEMENT"){
                action = "ackAlert"
            }
            else if(autoCloseAlert && serviceState == "OK" && notificationType == "RECOVERY"){
                action = "closeAlert"
            }
            else if((serviceState == "CRITICAL" || serviceState == "OK") && (notificationType == "PROBLEM" || notificationType == "RECOVERY" || notificationType == "ACKNOWLEDGEMENT")){
                action = "createAlert"
                alertProps.alias = alias
                alertProps.details = ["host": hostName, "service": service, "nagiosServer": NAGIOS_SERVER]
                alertProps.message = "** ${notificationType} Service Alert: ${hostAlias}/${service} is ${serviceState} **"
                alertProps.description = """***** Nagios *****

Notification Type: ${notificationType}
Service: ${service}
Host: ${hostAlias}
Address: ${hostAddress}
State: ${serviceState}
Additional Info: ${params.serviceOutput}
Date/Time: ${dateTime}
"""
            }
        }
        if (action == "createAlert") {
            logger.warn("Creating alert with message ${alertProps.message}");
            println "Creating alert"
            def response = opsgenie.createAlert(alertProps)
            def alertId = response.alertId;
            logger.warn("Alert is created with id :" + alertId);
            println "Alert is created with id :" + alertId
            if (attachFiles) {
                attach(alertId, entity)
            }
        } else if (action == "closeAlert") {
            logger.warn("Closing the alert ${alias}")
            opsgenie.closeAlert([alias: alias, source:"nagios"])
        }
        else if (action == "ackAlert") {
            logger.warn("Acknowledging the alert ${alias}")
            opsgenie.acknowledge([alias: alias, source:"nagios"])
        }
        else{
            logger.warn("Could not find action type for ${params}")
        }
    } else {
        logger.warn("Invalid notification mode ${entity}")
        println "Invalid notification mode ${entity}"
    }
} finally {
    HTTP_CLIENT.close();
}

def attach(alertId, entity) {
    // will get alert histgoram and trends charts from nagios and attach them to the alert
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
    if (alertHistogram != null) {
        ZipEntry imageEntry = new ZipEntry("alertHistogram.png");
        zout.putNextEntry(imageEntry);
        BufferedImage img = ImageIO.read(new ByteArrayInputStream(alertHistogram))
        ImageIO.write(img, "png", zout);
        zout.closeEntry()
    }
    if (trends != null) {
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
    if (trends != null) {
        buf.append('<div class="img"><img src="trends.png"></div>')
    } else {
        logger.warn("Trends is null while creating html")
    }

    if (alertHistogram != null) {
        buf.append('<div class="img"><img src="alertHistogram.png"></div>')
    } else {
        logger.warn("Histogram is null while creating html")
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
    def serviceGroup = params.serviceGroupName
    def memberOf = serviceGroup ? serviceGroup : 'No service groups'
    def state = params.serviceState
    def lastServiceCheck = params.lastServiceCheck
    def lastStateChange = params.lastServiceStateChange
    try {
        lastServiceCheck = dateFormatter.format(Long.parseLong(lastServiceCheck) * 1000L)
    } catch (Throwable e) {
    }
    try{
        lastStateChange = dateFormatter.format(Long.parseLong(lastStateChange) * 1000L)
    } catch (Throwable e) {
    }
    buf.append("""
        <div class="well">
            <table>
                <tbody>
                    <tr><td><b>Service:</b></td><td>${htmlEscape(params.serviceDesc)}</td></tr>
                    <tr><td><b>Host:</b></td><td>${htmlEscape(params.hostAlias)} (${htmlEscape(params.hostName)})</td></tr>
                    <tr><td><b>Address:</b></td><td>${htmlEscape(params.hostAddress)}</td></tr>
                    <tr><td><b>Member of:</b></td><td>${htmlEscape(memberOf)}</td></tr>
                    <tr><td><b>Current Status:</b></td><td><span class="${state}">${state}</span> for (${
        params.serviceDuration
    })</td></tr>
                    <tr><td><b>Status Information:</b></td><td>${htmlEscape(params.serviceOutput)}</td></tr>
                    <tr><td><b>Current Attempt:</b></td><td>${params.serviceAttempt}/${params.maxServiceAttempts} (${
        params.serviceStateType
    } state)</td></tr>
                    <tr><td><b>Last Check Time:</b></td><td>${lastServiceCheck}</td></tr>
                    <tr><td><b>Check Latency:</b></td><td>${params.serviceLatency}</td></tr>
                    <tr><td><b>Last State Change:</b></td><td>${lastStateChange}</td></tr>
                </tbody>
            </table>
        </div>
   """)
}

def getHostStatusHtml(buf) {
    SimpleDateFormat dateFormatter = new SimpleDateFormat("MM-dd-yyyy hh:mm:ss")
    def hostGroup = params.hostGroupName
    def memberOf = hostGroup ? hostGroup : 'No host groups'
    def state = params.hostState
    def lastCheckTime = params.lastHostCheck
    def lastStateChange = params.lastHostStateChange
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
                    <tr><td><b>Host:</b></td><td>${htmlEscape(params.hostAlias)} (${htmlEscape(params.hostName)})</td></tr>
                    <tr><td><b>Address:</b></td><td>${htmlEscape(params.hostAddress)}</td></tr>
                    <tr><td><b>Member of:</b></td><td>${htmlEscape(memberOf)}</td></tr>
                    <tr><td><b>Current Status:</b></td><td><span class="${state}">${state}</span> for (${
        params.hostDuration
    })</td></tr>
                    <tr><td><b>Status Information:</b></td><td>${htmlEscape(params.hostOutput)}</td></tr>
                    <tr><td><b>Current Attempt:</b></td><td>${params.hostAttempt}/${params.maxHostAttempts} (${
        params.hostStateType
    } state)</td></tr>
                    <tr><td><b>Last Check Time:</b></td><td>${lastCheckTime}</td></tr>
                    <tr><td><b>Check Latency:</b></td><td>${params.hostLatency}</td></tr>
                    <tr><td><b>Last State Change:</b></td><td>${lastStateChange}</td></tr>
                </tbody>
            </table>
        </div>
   """)
}

def getAlertHistogram(entity) {
    String url = getUrl("alert_histogram_image_url", "/nagiosxi/includes/components/nagioscore/ui/histogram.php")
    return getImage(url, entity);
}

def getTrends(entity) {
    String url = getUrl("trends_image_url", "/nagiosxi/includes/components/nagioscore/ui/trends.php")
    return getImage(url, entity);
}

def getImage(url, entity) {
    def host = params.hostName
    url += "?createimage&host=" + URLEncoder.encode(host)
    if (entity == "service") {
        def service = params.serviceDesc
        url += "&service=" + URLEncoder.encode(service)
    }
    url += "&username=" + conf["nagios.user"]
    url += "&ticket=" + conf["nagios.ticket"]
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


def createHttpClient() {
    def timeout = conf["nagios.http.timeout"]
    if(timeout == null){
        timeout = 30000;
    }
    else{
        timeout = timeout.toInteger();
    }
    ClientConfiguration clientConfiguration = new ClientConfiguration().setSocketTimeout(timeout)
    return new OpsGenieHttpClient(clientConfiguration)
}

def getUrl(String confProperty, String backwardCompatabilityUrl) {
    def url = conf["nagios."+confProperty]
    if (url != null) {
        return url;
    } else {
        //backward compatability
        def scheme = conf["nagios.scheme"]
        if (scheme == null) scheme = "http";
        def port = conf["nagios.port"].toInteger();
        def host = conf["nagios.host"];
        return new HttpHost(host, port, scheme).toURI() + backwardCompatabilityUrl;
    }
}



