import com.ifountain.opsgenie.client.http.OpsGenieHttpClient
import com.ifountain.opsgenie.client.util.ClientConfiguration
import org.apache.commons.lang.StringEscapeUtils
import org.apache.http.auth.UsernamePasswordCredentials

import java.text.SimpleDateFormat

/********************CONFIGURATIONS****************************/

// Recipients should be specified here for automatic tools. 
// Recipients can be users or groups created in OpsGenie
RECIPIENTS=conf["opennms.recipients"]
SOURCE="OpenNMS"


//OpenNMS credentials are needed for extra information to be fetched through REST API
OPENNMS_USER = conf["opennms.user"]
OPENNMS_PASSWORD = conf["opennms.password"]


def nodeId = params.nodeId;

if(nodeId){
	def alertProps = [:]
	alertProps.message = params.subject
	alertProps.recipients = RECIPIENTS
	alertProps.description = params.textMessage
	alertProps.source = SOURCE
	
	
	logger.warn("Creating alert with message ${alertProps.message}");
	println "Creating alert"
	def response = opsgenie.createAlert(alertProps)
	def alertId =  response.alertId;
	logger.warn("Alert is created with id :"+alertId);
	println "Alert is created with id :"+alertId
	attach(nodeId, alertId)
}
else{
	logger.warn("No node id is specified, skipping...")
	println "No node id is specified, skipping..."
}

def attach(nodeId, alertId){
	def restResponse = restCall(nodeId);
	if(restResponse){
		String htmlText = createHtml(restResponse);
		if(htmlText){
			logger.warn("Attaching details");
		    println "Attaching details"
		    response = opsgenie.attach([alertId:alertId, stream:new ByteArrayInputStream(htmlText.getBytes()), fileName:"outages.html"])
		    if(response.success){
		        logger.warn("Successfully attached details");
		        println "Successfully attached details"
		    }
		    else{
		        println "Could not attach details"
		        logger.warn("Could not attach details");
		    }		
		}	
		else{
			logger.warn("No outages found, skipping attachment.")
			println "No outages found, skipping attachment."
		}    
	}
}

def createHtml(restResponse){
	def xml = new XmlSlurper().parseText(restResponse);
	def outages = xml.outage;
	if(outages.size() > 0){
	    def nodelabel = outages[0].serviceLostEvent.nodeLabel.text();
	    //change these if you have different date formats.
	    def dateParser = new SimpleDateFormat("yyyy-MM-dd'T'hh:mm:ss");
	    def dateFormatter = new SimpleDateFormat("M/dd/yy hh:mm:ss a");
		StringBuffer buf = new StringBuffer();
		buf.append("""
			<html>
				<head>
					<style>
						body{background:#eee;}
						table{border-collapse: collapse;background:white;width: 100%;}
						td{border: 1px solid #999999;padding: 4px 5px;vertical-align:top;}
						.outage, .event{font-weight:bold;line-height:1.25em}
						.outage{background:#66992A;color:white;}
						.event{background:#FFEBCD;color:#555;}
					</style>
				</head>
				<body>
					<div>
						<h2>Outages of Node ${htmlEscape(nodelabel)}</h2>
						<table>
							<tbody>
					
		""")
		
		outages.each{
			def outageId = it.@id.text();
			def serviceLostEvent = it.serviceLostEvent
			def serviceRegainedEvent = it.serviceRegainedEvent
			def ipInterface = it.ipAddress.text();
			def service = it.monitoredService.serviceType.name.text();
			def lostTime = it.ifLostService.text();
			if(lostTime.indexOf("+") > -1){
				lostTime = lostTime.substring(0, lostTime.indexOf("+"))
			}
			lostTime = dateFormatter.format(dateParser.parse(lostTime));
			def regainTime = it.ifRegainedService.text();
			if(regainTime){
				if(regainTime.indexOf("+") > -1){
					regainTime = regainTime.substring(0, regainTime.indexOf("+"))
				}
				regainTime = dateFormatter.format(dateParser.parse(regainTime));
			}
			else{
				regainTime = "DOWN"
			}
			buf.append("""
				<tr>
					<td colspan="4" class="outage">Outage: ${outageId}</td>
				</tr>
				<tr>
					<td>Interface:</td>
					<td>${htmlEscape(ipInterface)}</td>
					<td>Lost Service Time:</td>
					<td>${lostTime}</td>
				</tr>
				<tr>
					<td>Service:</td>
					<td>${htmlEscape(service)}</td>
					<td>Regain Service Time:</td>
					<td>${regainTime}</td>
				</tr>
				<tr>
					<td colspan="4" class="event">Service Lost Event</td>
				</tr>
				<tr>
					<td>Severity:</td>
					<td>${serviceLostEvent.@severity.text()}</td>
					<td>UEI:</td>
					<td>${htmlEscape(serviceLostEvent.uei.text())}</td>
				</tr>
				<tr>
					<td>Description:</td>
					<td colspan="3">${htmlEscape(serviceLostEvent.description.text())}</td>
				</tr>
				<tr>
					<td>Log Message:</td>
					<td colspan="3">${htmlEscape(serviceLostEvent.logMessage.text())}</td>
				</tr>
			""")
			if(serviceRegainedEvent.size() > 0){
				buf.append("""
					<tr>
						<td colspan="4" class="event">Service Regained Event</td>
					</tr>
					<tr>
						<td>Severity:</td>
						<td>${serviceRegainedEvent.@severity.text()}</td>
						<td>UEI:</td>
						<td>${htmlEscape(serviceRegainedEvent.uei.text())}</td>
					</tr>
					<tr>
						<td>Description:</td>
						<td colspan="3">${htmlEscape(serviceRegainedEvent.description.text())}</td>
					</tr>
					<tr>
						<td>Log Message:</td>
						<td colspan="3">${htmlEscape(serviceRegainedEvent.logMessage.text())}</td>
					</tr>
				""")
			}
		}
		
		buf.append("""
							</tbody>
						</table>
					</div>
				</body>
			</html>
		""")
		return buf.toString();
	}
	return null;
}

def restCall(nodeId){
    String url = getUrl(nodeId)
	logger.warn("Getting node outages from url ${url}");
    println "Getting node outages from url ${url}"
    OpsGenieHttpClient httpClient = createHttpClient();
	try {
        def response = httpClient.get(getUrl(nodeId), [:])

		if(response.getStatusCode() == 200){
			logger.warn("Node outages received");
        	println "Node outages received"	
			return response.getContentAsString();
		}
		else{
			logger.warn("Could not get node outages. Response:${response.getContentAsString()}")
			println "Could not get node outages. Response:${response.getContentAsString()}"
			return null;
		}
	}
	finally{
        httpClient.close();
	}
}

def createHttpClient() {
    def timeout = conf["opennms.http.timeout"]
    if(timeout == null){
        timeout = 30000;
    }
    else{
        timeout = timeout.toInteger();
    }
    ClientConfiguration clientConfiguration = new ClientConfiguration().setSocketTimeout(timeout).
            setCredentials(new UsernamePasswordCredentials(OPENNMS_USER, OPENNMS_PASSWORD));
    return new OpsGenieHttpClient(clientConfiguration)
}

def getUrl(String nodeId) {
    def url = conf["opennms.outage_url"]
    return  url+"/${nodeId}/"
}

def htmlEscape(value){
    return StringEscapeUtils.escapeHtml(value)
}
