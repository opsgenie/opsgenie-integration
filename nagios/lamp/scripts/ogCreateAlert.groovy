import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.auth.AuthScope;
import org.apache.http.util.EntityUtils;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.HttpHost;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import java.util.zip.ZipOutputStream
import java.util.zip.ZipEntry
import javax.imageio.ImageIO
import java.awt.image.BufferedImage
import org.apache.commons.lang.StringEscapeUtils
import java.text.SimpleDateFormat

SOURCE = "Nagios"
RECIPIENTS = conf["nagios.recipients"]
NAGIOS_SERVER=conf["nagios.nagiosServer"]

logger.warn("ogCreateAlert params:${params}")
if(!getNagiosParam('NOTIFICATIONTYPE')){
    logger.warn("Stopping, Nagios NOTIFICATIONTYPE param has no value, please make sure your Nagios and OpsGenie files pass necessary parameters");
    return;
}

def entity = params.entity;
def action
// attach additional information as a file (only for pro & enterprise plans)
def attachFile = true 
// whether to close existing alerts for resolve (Up/OK) events create a separate alert
def autoCloseAlert = true

if (entity == "host" || entity == "service") {
    def alertProps = [:]
    def alias

    alertProps.message = params.subject
    alertProps.recipients = RECIPIENTS
    alertProps.description = params.textMessage
    alertProps.source = SOURCE
    def notificationType = getNagiosParam('NOTIFICATIONTYPE')
    logger.warn("notificationType: ${notificationType}")

    def hostAddress = getNagiosParam('HOSTADDRESS')
    def dateTime = getNagiosParam('LONGDATETIME')
    if (entity == "host") {
        def hostName = getNagiosParam('HOSTNAME')
        def hostState = getNagiosParam('HOSTSTATE');
        alias = hostName
        if ((hostState == "DOWN" && notificationType == "PROBLEM") || autoCloseAlert == false) {
            action = "createAlert"
            alertProps.alias = alias
            alertProps.details = ["host": hostName]
            alertProps.message = "** ${notificationType} Host Alert: ${hostName} is ${hostState} **"
            alertProps.description = """***** Nagios *****

Notification Type: ${notificationType}
Host: ${hostName}
State: ${hostState}
Address: ${hostAddress}
Additional Info: ${getNagiosParam('HOSTOUTPUT')}
Date/Time: ${dateTime}
"""
        } else if (hostState == "UP" && notificationType == "RECOVERY") { 
            action = "closeAlert"
        }
    }
    else {
        def service = getNagiosParam("SERVICEDESC")
        def serviceState = getNagiosParam("SERVICESTATE");
        def hostAlias = getNagiosParam("HOSTALIAS");
        def hostName = getNagiosParam("HOSTNAME");
        alias = hostName + "_" + service
        logger.warn("service state: ${serviceState}")

        if ((serviceState == "CRITICAL" && notificationType == "PROBLEM")  || autoCloseAlert == false) {
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
Additional Info: ${getNagiosParam('SERVICEOUTPUT')}
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
        if (attachFile) { attach(alertId, entity) }
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
        TARGET_HOST = new HttpHost(conf["nagios.host"], conf["nagios.port"].toInteger(), "http");
        HttpParams httpClientParams = new BasicHttpParams();
        HttpConnectionParams.setConnectionTimeout(httpClientParams, timeout);
        HttpConnectionParams.setSoTimeout(httpClientParams, timeout);
        HttpConnectionParams.setTcpNoDelay(httpClientParams, true);
        HTTP_CLIENT = new DefaultHttpClient(httpClientParams);
        AuthScope scope = new AuthScope(TARGET_HOST.getHostName(), TARGET_HOST.getPort());
        HTTP_CLIENT.getCredentialsProvider().setCredentials(scope, new UsernamePasswordCredentials(conf["nagios.user"], conf["nagios.password"]));


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
        }
        else {
            println "Could not attach details"
            logger.warn("Could not attach details");
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
    }
    else {
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
    def serviceGroup = getNagiosParam('SERVICEGROUPNAME');
    def memberOf = serviceGroup ? serviceGroup : 'No service groups'
    def state = getNagiosParam('SERVICESTATE');
    def lastServiceCheck = getNagiosParam("LASTSERVICECHECK");
    def lastStateChange = getNagiosParam("LASTSERVICESTATECHANGE");
    try{
        lastServiceCheck = dateFormatter.format(Long.parseLong(lastServiceCheck) * 1000L)
    }catch(Throwable e){}

    try{
        lastStateChange = dateFormatter.format(Long.parseLong(lastStateChange) * 1000L)
    }catch(Throwable e){}

    buf.append("""
        <div class="well">
            <table>
                <tbody>
                    <tr><td><b>Service:</b></td><td>${htmlEscape(getNagiosParam('SERVICEDESC'))}</td></tr>
                    <tr><td><b>Host:</b></td><td>${htmlEscape(getNagiosParam('HOSTALIAS'))} (${htmlEscape(getNagiosParam('HOSTNAME'))})</td></tr>
                    <tr><td><b>Address:</b></td><td>${htmlEscape(getNagiosParam('HOSTADDRESS'))}</td></tr>
                    <tr><td><b>Member of:</b></td><td>${htmlEscape(memberOf)}</td></tr>
                    <tr><td><b>Current Status:</b></td><td><span class="${state}">${state}</span> for (${getNagiosParam('SERVICEDURATION')})</td></tr>
                    <tr><td><b>Status Information:</b></td><td>${htmlEscape(getNagiosParam('SERVICEOUTPUT'))}</td></tr>
                    <tr><td><b>Performance Data:</b></td><td>${htmlEscape(getNagiosParam('SERVICEPERFDATA'))}</td></tr>
                    <tr><td><b>Current Attempt:</b></td><td>${getNagiosParam('SERVICEATTEMPT')}/${getNagiosParam('MAXSERVICEATTEMPTS')} (${getNagiosParam('SERVICESTATETYPE')} state)</td></tr>
                    <tr><td><b>Last Check Time:</b></td><td>${lastServiceCheck}</td></tr>
                    <tr><td><b>Check Type:</b></td><td>${getNagiosParam('SERVICECHECKTYPE')}</td></tr>
                    <tr><td><b>Check Latency:</b></td><td>${getNagiosParam('SERVICELATENCY')}</td></tr>
                    <tr><td><b>Last State Change:</b></td><td>${lastStateChange}</td></tr>
                </tbody>
            </table>
        </div>
   """)
}

def getHostStatusHtml(buf) {
    SimpleDateFormat dateFormatter = new SimpleDateFormat("MM-dd-yyyy hh:mm:ss")
    def hostGroup = getNagiosParam('HOSTGROUPNAME');
    def memberOf = hostGroup ? hostGroup : 'No host groups'
    def state = getNagiosParam('HOSTSTATE');
    def lastCheckTime = getNagiosParam("LASTHOSTCHECK");
    def lastStateChange = getNagiosParam("LASTHOSTSTATECHANGE");
    try{
        lastCheckTime = dateFormatter.format(Long.parseLong(lastCheckTime) * 1000L)
    }catch(Throwable e){}
    try{
        lastStateChange = dateFormatter.format(Long.parseLong(lastStateChange) * 1000L)
    }catch(Throwable e){}
    buf.append("""
        <div class="well">
            <table>
                <tbody>
                    <tr><td><b>Host:</b></td><td>${htmlEscape(getNagiosParam('HOSTALIAS'))} (${htmlEscape(getNagiosParam('HOSTNAME'))})</td></tr>
                    <tr><td><b>Address:</b></td><td>${htmlEscape(getNagiosParam('HOSTADDRESS'))}</td></tr>
                    <tr><td><b>Member of:</b></td><td>${htmlEscape(memberOf)}</td></tr>
                    <tr><td><b>Current Status:</b></td><td><span class="${state}">${state}</span> for (${getNagiosParam('HOSTDURATION')})</td></tr>
                    <tr><td><b>Status Information:</b></td><td>${htmlEscape(getNagiosParam('HOSTOUTPUT'))}</td></tr>
                    <tr><td><b>Performance Data:</b></td><td>${htmlEscape(getNagiosParam('HOSTPERFDATA'))}</td></tr>
                    <tr><td><b>Current Attempt:</b></td><td>${getNagiosParam('HOSTATTEMPT')}/${getNagiosParam('MAXHOSTATTEMPTS')} (${getNagiosParam('HOSTSTATETYPE')} state)</td></tr>
                    <tr><td><b>Last Check Time:</b></td><td>${lastCheckTime}</td></tr>
                    <tr><td><b>Check Type:</b></td><td>${getNagiosParam('HOSTCHECKTYPE')}</td></tr>
                    <tr><td><b>Check Latency:</b></td><td>${getNagiosParam('HOSTLATENCY')}</td></tr>
                    <tr><td><b>Last State Change:</b></td><td>${lastStateChange}</td></tr>
                </tbody>
            </table>
        </div>
   """)
}

def getAlertHistogram(entity) {
    return getImage("/nagios/cgi-bin/histogram.cgi", entity);
}

def getTrends(entity) {
    return getImage("/nagios/cgi-bin/trends.cgi", entity);
}

def getImage(url, entity) {
    def host = getNagiosParam("HOSTNAME")
    url += "?createimage&host=" + URLEncoder.encode(host)
    if (entity == "service") {
        def service = getNagiosParam("SERVICEDESC")
        url += "&service=" + URLEncoder.encode(service)
    }
    HttpGet httpGet = new HttpGet(url);
    def response = HTTP_CLIENT.execute(TARGET_HOST, httpGet);
    if (response.getStatusLine().getStatusCode() == 200) {
        logger.warn("Image received");
        println "Image received"
        return EntityUtils.toByteArray(response.getEntity());
    }
    else {
        logger.warn("Could not get image from url ${url}")
        println "Could not get image from url ${url}"
        return null;
    }
}

def htmlEscape(value) {
    return StringEscapeUtils.escapeHtml(value)
}

def getNagiosParam(paramName)
{
    def value = System.getenv("NAGIOS_"+paramName);
    if(!value){
        value = params[paramName];
    }
    return value;
}


