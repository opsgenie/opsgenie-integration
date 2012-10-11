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

/********************CONFIGURATIONS****************************/

// Recipients should be specified here for automatic tools. 
// Recipients can be users or groups created in OpsGenie
RECIPIENTS="all"
SOURCE="Nagios"


//Nagios credentials are needed for extra information to be fetched through Nagios CGIs
NAGIOS_USER = "nagiosadmin"
NAGIOS_PASSWORD = "admin"
NAGIOS_HOST="localhost"
NAGIOS_PORT=80

//http client preparation
def timeout = 30000;
TARGET_HOST = new HttpHost(NAGIOS_HOST, NAGIOS_PORT, "http");
HttpParams httpClientParams = new BasicHttpParams();
HttpConnectionParams.setConnectionTimeout(httpClientParams, timeout);
HttpConnectionParams.setSoTimeout(httpClientParams, timeout);
HttpConnectionParams.setTcpNoDelay(httpClientParams, true);
HTTP_CLIENT= new DefaultHttpClient(httpClientParams);
AuthScope scope = new AuthScope(TARGET_HOST.getHostName(), TARGET_HOST.getPort());
HTTP_CLIENT.getCredentialsProvider().setCredentials(scope, new UsernamePasswordCredentials(NAGIOS_USER, NAGIOS_PASSWORD));

def entity = params.entity;

if(entity == "host" || entity == "service"){
    def alertProps = [:]
	alertProps.message = params.subject
	alertProps.recipients = RECIPIENTS
	alertProps.description = params.textMessage
	alertProps.source = SOURCE
    def notificationType = System.getenv('NAGIOS_NOTIFICATIONTYPE')
    def hostAddress = System.getenv('NAGIOS_HOSTADDRESS')
    def dateTime =  System.getenv('NAGIOS_LONGDATETIME')
    if(entity == "host"){
        def hostName = System.getenv('HOSTNAME')
        def hostState = System.getenv('HOSTSTATE');

        alertProps.message= "** ${notificationType} Host Alert: ${hostName} is ${hostState} **"
        alertProps.description = """***** Nagios *****

Notification Type: ${notificationType}
Host: ${hostName}
State: ${hostState}
Address: ${hostAddress}
Additional Info: ${System.getenv('NAGIOS_HOSTOUTPUT')}
Date/Time: ${dateTime}
"""
    }
    else{
        def service = System.getenv("NAGIOS_SERVICEDESC")
        def serviceState = System.getenv("NAGIOS_SERVICESTATE");
        def hostAlias = System.getenv("NAGIOS_HOSTALIAS");
        alertProps.message=  "** ${notificationType} Service Alert: ${hostAlias}/${service} is ${serviceState} **"
        alertProps.description = """***** Nagios *****

Notification Type: ${notificationType}
Service: ${service}
Host: ${hostAlias}
Address: ${hostAddress}
State: ${serviceState}
Additional Info: ${System.getenv('NAGIOS_SERVICEOUTPUT')}
Date/Time: ${dateTime}
"""
    }
	logger.warn("Creating alert with message ${alertProps.message}");
	println "Creating alert"
	def response = opsgenie.createAlert(alertProps)
	def alertId =  response.alertId;
	logger.warn("Alert is created with id :"+alertId);
	println "Alert is created with id :"+alertId
	attach(alertId, entity)
}
else{
	logger.warn("Invalid notification mode ${entity}")
	println "Invalid notification mode ${entity}"
}

def attach(alertId, entity){
    // will get alert histgoram and trends charts from nagios and attach them to the alert
    try{
        def alertHistogram = getAlertHistogram(entity);
        def trends = getTrends(entity);
        def htmlText = createHtml(entity, alertHistogram, trends)
        ByteArrayOutputStream bout = createZip(htmlText, alertHistogram, trends)
        logger.warn("Attaching details");
        println "Attaching details"
        response = opsgenie.attach([alertId:alertId, stream:new ByteArrayInputStream(bout.toByteArray()), fileName:"details.zip"])
        if(response.success){
            logger.warn("Successfully attached details");
            println "Successfully attached details"
        }
        else{
            println "Could not attach details"
            logger.warn("Could not attach details");
        }
    }
    catch(e){
        logger.warn("Could not attach details. Reason: ${e.getMessage()}")
        println "Could not attach details. Reason: ${e.getMessage()}"
    }
}

def createZip(html, alertHistogram, trends){
    ByteArrayOutputStream bout = new ByteArrayOutputStream();
    ZipOutputStream zout = new ZipOutputStream(bout);
    ZipEntry htmlEntry = new ZipEntry("index.html");
    zout.putNextEntry(htmlEntry);
    zout.write(html.getBytes())
    zout.closeEntry()
    if(alertHistogram){
        ZipEntry imageEntry = new ZipEntry("alertHistogram.png");
        zout.putNextEntry(imageEntry);
        BufferedImage img = ImageIO.read(new ByteArrayInputStream(alertHistogram))
        ImageIO.write(img, "png", zout);
        zout.closeEntry()
    }
    if(trends){
        ZipEntry imageEntry = new ZipEntry("trends.png");
        zout.putNextEntry(imageEntry);
        BufferedImage img = ImageIO.read(new ByteArrayInputStream(trends))
        ImageIO.write(img, "png", zout);
        zout.closeEntry()
    }
    zout.close();
    return bout;
}

def createHtml(entity, alertHistogram, trends){
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
    if(entity == "host"){
        getHostStatusHtml(buf)
    }
    else{
        getServicesStatusHtml(buf)
    }
    if(trends){
        buf.append('<div class="img"><img src="trends.png"></div>')
    }
    if(alertHistogram){
        buf.append('<div class="img"><img src="alertHistogram.png"></div>')
    }
    buf.append("""
                </div>
            </body>
        </html>
    """)
    return buf.toString();
}

def getServicesStatusHtml(buf){
   SimpleDateFormat dateFormatter = new SimpleDateFormat("MM-dd-yyyy hh:mm:ss")
   def serviceGroup = System.getenv('NAGIOS_SERVICEGROUPNAME');
   def memberOf = serviceGroup ? serviceGroup : 'No service groups'
   def state = System.getenv('NAGIOS_SERVICESTATE');
   def lastServiceCheck = System.getenv("NAGIOS_LASTSERVICECHECK");
   def lastStateChange = System.getenv("NAGIOS_LASTSERVICESTATECHANGE");
   lastServiceCheck = dateFormatter.format(Long.parseLong(lastServiceCheck) * 1000L)
   lastStateChange = dateFormatter.format(Long.parseLong(lastStateChange) * 1000L)
   buf.append("""
        <div class="well">
            <table>
                <tbody>
                    <tr><td><b>Service:</b></td><td>${htmlEscape(System.getenv('NAGIOS_SERVICEDESC'))}</td></tr>
                    <tr><td><b>Host:</b></td><td>${htmlEscape(System.getenv('NAGIOS_HOSTALIAS'))} (${htmlEscape(System.getenv('NAGIOS_HOSTNAME'))})</td></tr>
                    <tr><td><b>Address:</b></td><td>${htmlEscape(System.getenv('NAGIOS_HOSTADDRESS'))}</td></tr>
                    <tr><td><b>Member of:</b></td><td>${htmlEscape(memberOf)}</td></tr>
                    <tr><td><b>Current Status:</b></td><td><span class="${state}">${state}</span> for (${System.getenv('NAGIOS_SERVICEDURATION')})</td></tr>
                    <tr><td><b>Status Information:</b></td><td>${htmlEscape(System.getenv('NAGIOS_SERVICEOUTPUT'))}</td></tr>
                    <tr><td><b>Performance Data:</b></td><td>${htmlEscape(System.getenv('NAGIOS_SERVICEPERFDATA'))}</td></tr>
                    <tr><td><b>Current Attempt:</b></td><td>${System.getenv('NAGIOS_SERVICEATTEMPT')}/${System.getenv('NAGIOS_MAXSERVICEATTEMPTS')} (${System.getenv('NAGIOS_SERVICESTATETYPE')} state)</td></tr>
                    <tr><td><b>Last Check Time:</b></td><td>${lastServiceCheck}</td></tr>
                    <tr><td><b>Check Type:</b></td><td>${System.getenv('NAGIOS_SERVICECHECKTYPE')}</td></tr>
                    <tr><td><b>Check Latency:</b></td><td>${System.getenv('NAGIOS_SERVICELATENCY')}</td></tr>
                    <tr><td><b>Last State Change:</b></td><td>${lastStateChange}</td></tr>
                </tbody>
            </table>
        </div>
   """)
}
def getHostStatusHtml(buf){
    SimpleDateFormat dateFormatter = new SimpleDateFormat("MM-dd-yyyy hh:mm:ss")
    def hostGroup = System.getenv('NAGIOS_HOSTGROUPNAME');
    def memberOf = hostGroup ? hostGroup : 'No host groups'
    def state = System.getenv('NAGIOS_HOSTSTATE');
    def lastCheckTime = System.getenv("NAGIOS_LASTHOSTCHECK");
    def lastStateChange = System.getenv("NAGIOS_LASTHOSTSTATECHANGE");
    lastCheckTime = dateFormatter.format(Long.parseLong(lastCheckTime) * 1000L)
    lastStateChange = dateFormatter.format(Long.parseLong(lastStateChange) * 1000L)
    buf.append("""
        <div class="well">
            <table>
                <tbody>
                    <tr><td><b>Host:</b></td><td>${htmlEscape(System.getenv('NAGIOS_HOSTALIAS'))} (${htmlEscape(System.getenv('NAGIOS_HOSTNAME'))})</td></tr>
                    <tr><td><b>Address:</b></td><td>${htmlEscape(System.getenv('NAGIOS_HOSTADDRESS'))}</td></tr>
                    <tr><td><b>Member of:</b></td><td>${htmlEscape(memberOf)}</td></tr>
                    <tr><td><b>Current Status:</b></td><td><span class="${state}">${state}</span> for (${System.getenv('NAGIOS_HOSTDURATION')})</td></tr>
                    <tr><td><b>Status Information:</b></td><td>${htmlEscape(System.getenv('NAGIOS_HOSTOUTPUT'))}</td></tr>
                    <tr><td><b>Performance Data:</b></td><td>${htmlEscape(System.getenv('NAGIOS_HOSTPERFDATA'))}</td></tr>
                    <tr><td><b>Current Attempt:</b></td><td>${System.getenv('NAGIOS_HOSTATTEMPT')}/${System.getenv('NAGIOS_MAXHOSTATTEMPTS')} (${System.getenv('NAGIOS_HOSTSTATETYPE')} state)</td></tr>
                    <tr><td><b>Last Check Time:</b></td><td>${lastCheckTime}</td></tr>
                    <tr><td><b>Check Type:</b></td><td>${System.getenv('NAGIOS_HOSTCHECKTYPE')}</td></tr>
                    <tr><td><b>Check Latency:</b></td><td>${System.getenv('NAGIOS_HOSTLATENCY')}</td></tr>
                    <tr><td><b>Last State Change:</b></td><td>${lastStateChange}</td></tr>
                </tbody>
            </table>
        </div>
   """)
}

def getAlertHistogram(entity){
    return getImage("/nagios/cgi-bin/histogram.cgi", entity);
}

def getTrends(entity){
    return getImage("/nagios/cgi-bin/trends.cgi", entity);
}

def getImage(url, entity){
    def host = System.getenv("NAGIOS_HOSTNAME")
    url += "?createimage&host=" + URLEncoder.encode(host)
    if(entity == "service"){
        def service = System.getenv("NAGIOS_SERVICEDESC")
        url+="&service=" + URLEncoder.encode(service)
    }
    HttpGet httpGet = new HttpGet(url);
    def response = HTTP_CLIENT.execute(TARGET_HOST, httpGet);
    if(response.getStatusLine().getStatusCode() == 200){
        logger.warn("Image received");
        println "Image received"
        return EntityUtils.toByteArray(response.getEntity());
    }
    else{
        logger.warn("Could not get image from url ${url}")
        println "Could not get image from url ${url}"
        return null;
    }
}

def htmlEscape(value){
    return StringEscapeUtils.escapeHtml(value)
}
