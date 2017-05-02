import org.w3c.dom.Document
import org.w3c.dom.NodeList
import org.xml.sax.InputSource

import javax.xml.parsers.DocumentBuilder
import javax.xml.parsers.DocumentBuilderFactory
import java.text.SimpleDateFormat
import com.ifountain.opsgenie.client.http.OpsGenieHttpClient
import com.ifountain.opsgenie.client.util.ClientConfiguration

CONF_PREFIX = "webex."
LOG_PREFIX = "[${action}]:"
logger.warn("${LOG_PREFIX} Alert: AlertId:[${alert.alertId}] Note:[${alert.note}] Source: [${source}]")

alertFromOpsgenie = opsgenie.getAlert(alertId: alert.alertId)

if (alertFromOpsgenie.size() > 0) {
    def webexServerUrl = _conf("server.url", true)
    def webexUsername = _conf("username", true)
    def webexPassword = _conf("password", true)
    def webexSiteId = _conf("siteId", true)
    def webexPartnerId = _conf("partnerId", true)
    def webexConferenceName = alertFromOpsgenie.message
    def username = alert.source

    def details = alertFromOpsgenie.details
    def webExUrl = details["WebExUrl"]

    if (webExUrl != null && webExUrl.length() > 0) {
        throw new Exception("This alert already contains a WebEx session!")
    }

    def webexConference = createWebexConference(webexServerUrl, webexUsername, webexPassword,
            webexSiteId, webexPartnerId, webexConferenceName, username)

    def detailsMap = [
            "WebExUrl": webexConference.joinMeetingUrl,
            "WebExNumber": webexConference.tollNumber,
            "WebExAccessCode": webexConference.meetingKey,
    ]

    opsgenie.addDetails(["id": alert.alertId, "details": detailsMap])
    opsgenie.addNote(["id": alert.alertId, "note": "${username} has created a WebEx meeting."])
}

def createWebexConference(webexServerUrl, webexUsername, webexPassword,
                             webexSiteId, webexPartnerId, webexConferenceName, username) {
    OpsGenieHttpClient client = createHttpClient()
    def headers = [
            "Content-Type": "application/xml"
    ]

    def createMeetingXML = getXMLStringForCreateMeeting(webexUsername, webexPassword, webexSiteId,
            webexPartnerId, webexConferenceName)

    def createMeetingResponse = client.post(webexServerUrl, createMeetingXML, headers)
    logger.debug("Create meeting result: " + createMeetingResponse)
    Document createMeetingResponseXML = loadXMLFromString(createMeetingResponse.getContentAsString())

    String createMeetingResult = createMeetingResponseXML.getElementsByTagName("serv:result").item(0).getChildNodes().item(0).getNodeValue()

    if (!"SUCCESS".equalsIgnoreCase(createMeetingResult)){
        String reason = createMeetingResponseXML.getElementsByTagName("serv:reason").item(0).getChildNodes().item(0).getNodeValue()
        logger.error("${LOG_PREFIX} Could not create meeting on WebEx! Reason: " + reason)
        throw new Exception("Could not create meeting on WebEx! Reason: " + reason)
    }

    String meetingKey = createMeetingResponseXML.getElementsByTagName("meet:meetingkey").item(0).getChildNodes().item(0).getNodeValue()
    logger.debug("meetingKey: " + meetingKey)
    String guestToken = createMeetingResponseXML.getElementsByTagName("meet:guestToken").item(0).getChildNodes().item(0).getNodeValue()
    logger.debug("guestToken: " + guestToken)
    String hostUrl = createMeetingResponseXML.getElementsByTagName("serv:host").item(0).getChildNodes().item(0).getNodeValue()
    logger.debug("hostUrl: " + hostUrl)
    String attendeeUrl = createMeetingResponseXML.getElementsByTagName("serv:attendee").item(0).getChildNodes().item(0).getNodeValue()
    logger.debug("attendeeUrl: " + attendeeUrl)

    def getMeetingXML = getXMLStringForGetMeeting(webexUsername, webexPassword, webexSiteId, webexPartnerId, meetingKey)

    def getMeetingResponse = client.post(webexServerUrl, getMeetingXML, headers)
    logger.debug("Get meeting result: " + getMeetingResponse)
    Document getMeetingResponseXML = loadXMLFromString(getMeetingResponse.getContentAsString())

    String getMeetingResult = getMeetingResponseXML.getElementsByTagName("serv:result").item(0).getChildNodes().item(0).getNodeValue()

    if (!"SUCCESS".equalsIgnoreCase(getMeetingResult)){
        String reason = getMeetingResponseXML.getElementsByTagName("serv:reason").item(0).getChildNodes().item(0).getNodeValue()
        logger.error("${LOG_PREFIX} Could not get meeting on WebEx! Reason: " + reason)
        throw new Exception("Could not get meeting on WebEx! Reason: " + reason)
    }

    String tollNumber = ""
    NodeList tollNumberElement = getMeetingResponseXML.getElementsByTagName("serv:tollNum")
    if (tollNumberElement != null) {
        tollNumber = tollNumberElement.item(0).getChildNodes().item(0).getNodeValue()
    }
    logger.debug("tollNumber: " + tollNumber)
    String tollFreeNum = ""
    NodeList tollFreeNumberElement = getMeetingResponseXML.getElementsByTagName("serv:tollFreeNum")
    if (tollNumberElement != null) {
        tollFreeNum = tollFreeNumberElement.item(0).getChildNodes().item(0).getNodeValue()
    }
    logger.debug("tollFreeNum: " + tollFreeNum)

    def joinMeetingUrl = getJoinMeetingUrlOnWebex(client, webexServerUrl, webexUsername, webexPassword, webexSiteId, webexPartnerId, username, meetingKey)

    def returnMap = [
            "meetingKey": meetingKey,
            "tollNumber": tollNumber,
            "tollFreeNumber": tollFreeNum,
            "hostUrl": hostUrl,
            "attendeeUrl": attendeeUrl,
            "joinMeetingUrl": joinMeetingUrl
    ]

    return returnMap
}

def getXMLStringForCreateMeeting(webExUsername, webExPassword, siteId, partnerId, conferenceName) {
    TimeZone timeZone = TimeZone.getTimeZone("UTC")
    Calendar cal = Calendar.getInstance(timeZone)
    SimpleDateFormat dateFormat = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss")
    dateFormat.setTimeZone(timeZone)
    String currentTime = dateFormat.format(cal.getTime())

    return """
<?xml version="1.0" encoding="ISO-8859-1"?>
<serv:message xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
    <header>
        <securityContext>
            <webExID>${webExUsername}</webExID>
            <password>${webExPassword}</password>
            <siteID>${siteId}</siteID>
            <partnerID>${partnerId}</partnerID>
        </securityContext>
    </header>
    <body>
        <bodyContent
            xsi:type="java:com.webex.service.binding.meeting.CreateMeeting">
            <metaData>
                <confName>${conferenceName}</confName>
                <meetingType>105</meetingType>
            </metaData>
            <participants>
                <maxUserNumber>4</maxUserNumber>
            </participants>
            <enableOptions>
                <chat>true</chat>
                <poll>true</poll>
                <audioVideo>true</audioVideo>
            </enableOptions>
            <schedule>
                <startDate>${currentTime}</startDate>
                <timeZoneID>20</timeZoneID>
            </schedule>
            <telephony>
                <telephonySupport>CALLIN</telephonySupport>
            </telephony>
        </bodyContent>
    </body>
</serv:message>
"""
}

def getXMLStringForGetMeeting(webExUsername, webExPassword, webExSiteId, webExPartnerId,
                                 meetingKey) {
    return """
<?xml version="1.0" encoding="ISO-8859-1"?>
<serv:message xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
    <header>
        <securityContext>
            <webExID>${webExUsername}</webExID>
            <password>${webExPassword}</password>
            <siteID>${webExSiteId}</siteID>
            <partnerID>${webExPartnerId}</partnerID>
        </securityContext>
    </header>
    <body>
        <bodyContent xsi:type="java:com.webex.service.binding.meeting.GetMeeting">
            <meetingKey>${meetingKey}</meetingKey>
        </bodyContent>
    </body>
</serv:message>
"""
}

def getXMLStringForJoinMeeting(webExUsername, webExPassword, webExSiteId, webExPartnerId,
                                  username, sessionKey) {
    return """
<?xml version="1.0" encoding="ISO-8859-1"?>
<serv:message xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
    <header>
        <securityContext>
            <webExID>${webExUsername}</webExID>
            <password>${webExPassword}</password>
            <siteID>${webExSiteId}</siteID>
            <partnerID>${webExPartnerId}</partnerID>
        </securityContext>
    </header>
    <body>
        <bodyContent
            xsi:type="java:com.webex.service.binding.meeting.GetjoinurlMeeting">
            <sessionKey>${sessionKey}</sessionKey>
            <attendeeName>${username}</attendeeName>
        </bodyContent>
    </body>
</serv:message>
"""
}

Document loadXMLFromString(String xml) throws Exception {
    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance()
    DocumentBuilder builder = factory.newDocumentBuilder()
    InputSource is = new InputSource(new StringReader(xml))
    return builder.parse(is)
}

def getJoinMeetingUrlOnWebex(OpsGenieHttpClient client, webexServerUrl, webexUsername, webexPassword, webexSiteId, webexPartnerId, username, meetingKey) {
    def headers = [
            "Content-Type": "application/xml"
    ]

    def joinMeetingXML = getXMLStringForJoinMeeting(webexUsername, webexPassword, webexSiteId, webexPartnerId, username, meetingKey)
    def joinMeetingResponse = client.post(webexServerUrl, joinMeetingXML, headers)
    logger.debug("Join meeting result: " + joinMeetingResponse)
    Document joinMeetingResponseXML = loadXMLFromString(joinMeetingResponse.getContentAsString())

    String joinMeetingResult = joinMeetingResponseXML.getElementsByTagName("serv:result").item(0).getChildNodes().item(0).getNodeValue()
    if (!"SUCCESS".equalsIgnoreCase(joinMeetingResult)){
        String reason = joinMeetingResponseXML.getElementsByTagName("serv:reason").item(0).getChildNodes().item(0).getNodeValue()
        logger.error("${LOG_PREFIX} Could not get join meeting url from WebEx! Reason: " + reason)
        throw new Exception("Could not get join meeting url from WebEx! Reason: " + reason)
    }

    String joinMeetingUrl = joinMeetingResponseXML.getElementsByTagName("meet:joinMeetingURL").item(0).getChildNodes().item(0).getNodeValue()
    String inviteMeetingUrl = joinMeetingResponseXML.getElementsByTagName("meet:inviteMeetingURL").item(0).getChildNodes().item(0).getNodeValue()
    logger.debug("joinMeetingUrl: " + joinMeetingUrl)
    logger.debug("inviteMeetingURL: " + inviteMeetingUrl)
    return inviteMeetingUrl
}

def _conf(confKey, boolean isMandatory) {
    def confVal = conf[CONF_PREFIX + confKey]
    logger.debug("confVal ${CONF_PREFIX + confKey} from file is ${confVal}")
    if (isMandatory && confVal == null) {
        def errorMessage = "${LOG_PREFIX} Skipping action, Mandatory Conf item ${CONF_PREFIX + confKey} is missing. Check your marid conf file."
        logger.warn(errorMessage)
        throw new Exception(errorMessage)
    }
    return confVal
}

OpsGenieHttpClient createHttpClient() {
    def timeout = _conf("http.timeout", false)
    if (timeout == null) {
        timeout = 30000
    } else {
        timeout = timeout.toInteger()
    }
    ClientConfiguration clientConfiguration = new ClientConfiguration().setSocketTimeout(timeout)
    return new OpsGenieHttpClient(clientConfiguration)
}