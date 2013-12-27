import org.apache.commons.lang.StringEscapeUtils
import org.apache.http.HttpHost
import org.apache.http.client.methods.HttpGet
import org.apache.http.impl.client.DefaultHttpClient
import org.apache.http.params.BasicHttpParams
import org.apache.http.params.HttpConnectionParams
import org.apache.http.params.HttpParams
import org.apache.http.util.EntityUtils

import javax.imageio.ImageIO
import java.awt.image.BufferedImage
import java.text.SimpleDateFormat
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

SOURCE = "Nagios"
RECIPIENTS = conf["nagios.recipients"]
NAGIOS_SERVER = conf["nagios.nagiosServer"]

def entity = params.entity;
def action
// attach additional information as a file (only for pro & enterprise plans)
def attachFile = true
// whether to close existing alerts for resolve (Up/OK) events create a separate alert
def autoCloseAlert = true

logger.warn("PARAMS: " + params)
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
        if ((hostState == "DOWN" && notificationType == "PROBLEM") || autoCloseAlert == false) {
            logger.warn("Will create alert for host problem")
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
        } else if (hostState == "UP" && notificationType == "RECOVERY") {
            logger.warn("Will close alert for host problem")
            action = "closeAlert"
        }
    } else {
        def service = params.serviceDesc
        def serviceState = params.serviceState
        def hostAlias = params.hostAlias
        def hostName = params.hostName
        alias = hostName + "_" + service
        logger.warn("service state: ${serviceState}")

        if ((serviceState == "CRITICAL" && notificationType == "PROBLEM") || autoCloseAlert == false) {
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
        } else if (serviceState == "OK" && notificationType == "RECOVERY") {
            action = "closeAlert"
        }
    }
    if (action == "createAlert") {
        logger.warn("Creating alert with message ${alertProps.message}");
        println "Creating alert"
        def response = opsgenie.createAlert(alertProps)
        def alertId = response.alertId;
        logger.warn("Alert is created with id :" + alertId);
        println "Alert is created with id :" + alertId
        if (attachFile) {
            attach(alertId, entity)
        }
    } else {
        if (action == "closeAlert") {
            logger.warn("Closing the alert ${alias}")
            opsgenie.closeAlert([alias: alias])
        }
    }
} else {
    logger.warn("Invalid notification mode ${entity}")
    println "Invalid notification mode ${entity}"
}

def attach(alertId, entity) {
    // will get alert histgoram and trends charts from nagios and attach them to the alert
    try {
        //http client preparation
        def timeout = conf["nagios.http.timeout"].toInteger();
        HttpParams httpClientParams = new BasicHttpParams();
        HttpConnectionParams.setConnectionTimeout(httpClientParams, timeout);
        HttpConnectionParams.setSoTimeout(httpClientParams, timeout);
        HttpConnectionParams.setTcpNoDelay(httpClientParams, true);
        HTTP_CLIENT = new DefaultHttpClient(httpClientParams);


        def alertHistogram = getAlertHistogram(entity);
        def trends = getTrends(entity);
        def htmlText = createHtml(entity, alertHistogram, trends)
        ByteArrayOutputStream bout = createZip(htmlText, alertHistogram, trends)
        logger.warn("Attaching details");
        println "Attaching details"
        response = opsgenie.attach([alertId: alertId, stream: new ByteArrayInputStream(bout.toByteArray()), fileName: "details.zip"])
        if (response.success) {
            logger.warn("Successfully attached details");
            println "Successfully attached details"
        } else {
            println "Could not attach details"
            logger.warn("Could not attach details");
        }
    }
    catch (e) {
        logger.warn("Could not attach details. Reason: ${e.getMessage()}")
        println "Could not attach details. Reason: ${e.getMessage()}"
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
    lastServiceCheck = dateFormatter.format(Long.parseLong(lastServiceCheck) * 1000L)
    lastStateChange = dateFormatter.format(Long.parseLong(lastStateChange) * 1000L)
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
    lastCheckTime = dateFormatter.format(Long.parseLong(lastCheckTime) * 1000L)
    lastStateChange = dateFormatter.format(Long.parseLong(lastStateChange) * 1000L)
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
    return getImage("/nagiosxi/includes/components/nagioscore/ui/histogram.php", entity);
}

def getTrends(entity) {
    return getImage("/nagiosxi/includes/components/nagioscore/ui/trends.php", entity);
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
    HttpGet httpGet = new HttpGet(url);
    HttpHost httpHost = new HttpHost(conf["nagios.host"],Integer.valueOf(conf["nagios.port"]))
    def response = HTTP_CLIENT.execute(httpHost, httpGet);
    if (response.getStatusLine().getStatusCode() == 200) {
        logger.warn("Image received");
        println "Image received"
        return EntityUtils.toByteArray(response.getEntity());
    } else {
        logger.warn("Could not get image from url ${url}")
        println "Could not get image from url ${url}"
        return null;
    }
}

def htmlEscape(value) {
    return StringEscapeUtils.escapeHtml(value)
}

