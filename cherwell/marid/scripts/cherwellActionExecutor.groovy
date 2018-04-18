import com.ifountain.opsgenie.client.http.OpsGenieHttpClient
import com.ifountain.opsgenie.client.util.ClientConfiguration
import org.apache.http.HttpHeaders
import org.apache.http.client.entity.UrlEncodedFormEntity
import org.apache.http.NameValuePair
import org.apache.http.entity.StringEntity
import org.apache.http.message.BasicNameValuePair
import com.ifountain.opsgenie.client.marid.MemoryStore
import groovy.json.*

LOG_PREFIX = "[${mappedAction}]:"
logger.warn("${LOG_PREFIX} Will execute [${mappedAction}] for alertId ${params.alertId}")

CONF_PREFIX = "cherwell."
HTTP_CLIENT = createHttpClient()

username = params.username
password = params.password
apiUrl = params.apiUrl
clientId = params.clientId
accessToken = ""

try {
    parseParameters()
    accessToken = login()

    if (mappedAction == "addJournal") {
        addJournalToIncident()
    }
    else if (mappedAction == "createIncident") {
        createIncident()
    }
    else if (mappedAction == "resolveIncident") {
        setIncidentStatus("Resolved")
    }
    else if (mappedAction == "inProgressIncident") {
        setIncidentStatus("In Progress")
    }
}
catch (Exception e) {
    logger.error(e.getMessage(), e)
}
finally {
    HTTP_CLIENT.close()
}

def parseParameters() {
    username = parseConfigIfNullOrEmpty(username, "username")
    logger.debug("Username: ${username}")
    password = parseConfigIfNullOrEmpty(password, "password")
    logger.debug("Password: ${password.bytes.encodeBase64().toString()}")
    apiUrl = parseConfigIfNullOrEmpty(apiUrl, "apiUrl")
    logger.debug("API URL: ${apiUrl}")
    clientId = parseConfigIfNullOrEmpty(clientId, "clientId")
    logger.debug("Client ID: ${clientId}")
}

def login(){
    Map headers = [:]
    headers[HttpHeaders.CONTENT_TYPE] = "application/x-www-form-urlencoded"
    headers[HttpHeaders.ACCEPT] = "application/json"
    Map parameters = [:]
    parameters.put("grant_type", "password")
    parameters.put("client_id", clientId)
    parameters.put("username", username)
    parameters.put("password", password)

    List<NameValuePair> formParams = new ArrayList<NameValuePair>()
    for (Map.Entry<String, Object> entry : parameters.entrySet()) {
        formParams.add(new BasicNameValuePair(entry.getKey(), String.valueOf(entry.getValue())))
    }
    UrlEncodedFormEntity entity = new UrlEncodedFormEntity(formParams, "UTF-8")

    def response = HTTP_CLIENT.post(apiUrl + "/token", entity, headers)
    if (response.content != null) {
        responseMap = new JsonSlurper().parseText(response.getContentAsString())
        if (response.getStatusCode() < 300 && responseMap.get("access_token") != null) {
            logger.info("${LOG_PREFIX} Successfully logged in.")
            logger.debug("${LOG_PREFIX} Cherwell response: ${response.getContentAsString()}")
            return responseMap.get("access_token")
        }
    }
    throw new Exception("${LOG_PREFIX} Could not log in; response: ${response.statusCode} ${response.getContentAsString()}")

}

def createIncident(){
    Map headers = [:]
    addAuthorizationHeader(headers, accessToken)
    headers[HttpHeaders.ACCEPT] = "application/json"
    headers[HttpHeaders.CONTENT_TYPE] = "application/json"

    String incidentBusObId = getBusObId("Incident",StoreKey.INCIDENT_ID)
    String customerIdFieldId = getFieldId(incidentBusObId, StoreKey.INCIDENT_TEMPLATE,"Customer ID")
    String descriptionFieldId = getFieldId(incidentBusObId, StoreKey.INCIDENT_TEMPLATE,"Description")
    String priorityFieldId = getFieldId(incidentBusObId, StoreKey.INCIDENT_TEMPLATE,"Priority")
    String shortDescriptionFieldId = getFieldId(incidentBusObId, StoreKey.INCIDENT_TEMPLATE,"Short Description")
    String ownedByDescriptionFieldId = getFieldId(incidentBusObId, StoreKey.INCIDENT_TEMPLATE,"Owned By")

    List<BusinessObjectField> fields = new ArrayList<>()
    fields.add(new BusinessObjectField(dirty: true, displayName: "Description", fieldId: descriptionFieldId, name:"Description", value:getDescription()))
    fields.add(new BusinessObjectField(dirty: true, displayName: "Priority", fieldId: priorityFieldId, name:"Priority", value:getPriorityAsInteger(params.priority)))
    fields.add(new BusinessObjectField(dirty: true, displayName: "Customer ID", fieldId: customerIdFieldId, name:"CustomerRecID", value:getOGCustomerId()))
    fields.add(new BusinessObjectField(dirty: true, displayName: "Short Description", fieldId: shortDescriptionFieldId, name:"ShortDescription", value:params.message))
    fields.add(new BusinessObjectField(dirty: true, displayName: "Owned By", fieldId: ownedByDescriptionFieldId, name:"OwnedBy", value:"OpsGenie"))

    Map payload = new HashMap<String, Object>()
    payload.put("busObId", incidentBusObId)
    payload.put("fields", fields)
    payload.put("persist", true)

    logger.debug("Incident creation payload:" + JsonOutput.toJson(payload))

    def response = HTTP_CLIENT.post(apiUrl + "/api/V1/savebusinessobject/", new StringEntity(JsonOutput.toJson(payload)), headers)
    if (response.content != null) {
        responseMap = new JsonSlurper().parseText(response.getContentAsString())
        if (response.getStatusCode() < 300 && responseMap.get("busObPublicId") != null) {
            logger.info("${LOG_PREFIX} Successfully created incident.")
            logger.debug("${LOG_PREFIX} Cherwell response: ${response.getContentAsString()}")
            opsgenie.addDetails(["alertId": params.alertId, "details": ["og-internal-incidentId": responseMap.get("busObPublicId")]])
            return
        }
    }
    throw new Exception("${LOG_PREFIX} Could not create incident; response: ${response.statusCode} ${response.getContentAsString()}")

}


def setIncidentStatus(String status){
    Map headers = [:]
    addAuthorizationHeader(headers, accessToken)
    headers[HttpHeaders.ACCEPT] = "application/json"
    headers[HttpHeaders.CONTENT_TYPE] = "application/json"

    String incidentBusObId = getBusObId("Incident",StoreKey.INCIDENT_ID)
    String statusFieldId = getFieldId(incidentBusObId, StoreKey.INCIDENT_TEMPLATE,"Status")

    List<BusinessObjectField> fields = new ArrayList<>()
    fields.add(new BusinessObjectField(dirty: true, displayName: "Status", fieldId: statusFieldId, name:"Status", value:status))
    if(!incidentHasOwner(params.incidentPublicId)){
        String ownedByDescriptionFieldId = getFieldId(incidentBusObId, StoreKey.INCIDENT_TEMPLATE,"Owned By")
        fields.add(new BusinessObjectField(dirty: true, displayName: "Owned By", fieldId: ownedByDescriptionFieldId, name:"OwnedBy", value:"OpsGenie"))
    }

    Map payload = new HashMap<String, Object>()
    payload.put("busObId", incidentBusObId)
    payload.put("busObPublicId", params.incidentPublicId)
    payload.put("fields", fields)

    logger.debug("Incident modify payload:" + JsonOutput.toJson(payload))

    def response = HTTP_CLIENT.post(apiUrl + "/api/V1/savebusinessobject/", new StringEntity(JsonOutput.toJson(payload)), headers)
    if (response.content != null) {
        responseMap = new JsonSlurper().parseText(response.getContentAsString())
        if (response.getStatusCode() < 300 && responseMap.get("busObPublicId") != null) {
            logger.info("${LOG_PREFIX} Successfully modified incident.")
            logger.debug("${LOG_PREFIX} Cherwell response: ${response.getContentAsString()}")
            return
        }
    }
    throw new Exception("${LOG_PREFIX} Could not modify incident; response: ${response.statusCode} ${response.getContentAsString()}")
}

def addJournalToIncident(){
    Map headers = [:]
    addAuthorizationHeader(headers, accessToken)
    headers[HttpHeaders.ACCEPT] = "application/json"
    headers[HttpHeaders.CONTENT_TYPE] = "application/json"

    String journalBusObId = getBusObId("Journal",StoreKey.JOURNAL_ID)
    String journalNoteBusObId = getJournalNoteBusObId()
    String journalTypeIdFieldId = getFieldId(journalBusObId, StoreKey.JOURNAL_TEMPLATE, "Journal TypeID")
    String journalDetailsFieldId = getFieldId(journalBusObId, StoreKey.JOURNAL_TEMPLATE, "Details")
    String incidentJournalRelationshipId = getIncidentJournalRelationshipId()


    List<BusinessObjectField> fields = new ArrayList<>()
    fields.add(new BusinessObjectField(dirty: true, displayName: "Journal TypeID", fieldId: journalTypeIdFieldId, name:"JournalTypeID", value:journalNoteBusObId))
    fields.add(new BusinessObjectField(dirty: true, displayName: "Details", fieldId: journalDetailsFieldId, name:"Details", value:params.journalNote))

    Map payload = new HashMap<String, Object>()
    payload.put("fields", fields)
    payload.put("parentBusObId", getBusObId("Incident",StoreKey.INCIDENT_ID))
    payload.put("parentBusObPublicId", params.incidentPublicId)
    payload.put("relationshipId", incidentJournalRelationshipId)
    payload.put("busObId", journalBusObId)
    payload.put("persist", true)


    logger.debug("Add journal payload:" + JsonOutput.toJson(payload))

    def response = HTTP_CLIENT.post(apiUrl + "/api/V1/saverelatedbusinessobject/", new StringEntity(JsonOutput.toJson(payload)), headers)
    if (response.content != null) {
        responseMap = new JsonSlurper().parseText(response.getContentAsString())
        if (response.getStatusCode() < 300 && responseMap.get("busObPublicId") != null) {
            logger.info("${LOG_PREFIX} Successfully added journal to incident with public id:" + params.incidentPublicId)
            logger.debug("${LOG_PREFIX} Cherwell response: ${response.getContentAsString()}")
            return
        }
    }
    throw new Exception("${LOG_PREFIX} Could not add journal to incident; response: ${response.statusCode} ${response.getContentAsString()}")

}

String getBusObId(String objectName, String storeKey){
    String busObId = MemoryStore.lookup(storeKey)
    return busObId == null ? retrieveBusObId(objectName, storeKey) : busObId
}

String retrieveBusObId(String objectName, String storeKey){
    Map headers = [:]
    addAuthorizationHeader(headers, accessToken)
    headers[HttpHeaders.ACCEPT] = "application/json"

    def response = HTTP_CLIENT.get(apiUrl + "/api/V1/getbusinessobjectsummary/busobname/"+objectName, new HashMap<>(), headers)
    if (response.content != null) {
        responseMapArr = new JsonSlurper().parseText(response.getContentAsString())
        if (response.getStatusCode() < 300 && responseMapArr.get(0)?.get("busObId") != null) {
            logger.debug("Successfully retrieved ${objectName} 's busObId: ${responseMapArr.get(0).get("busObId").toString()}")
            MemoryStore.store(storeKey, responseMapArr.get(0).get("busObId"))
            return responseMapArr.get(0).get("busObId")
        }
    }
    throw new Exception("${LOG_PREFIX} Could not acquire ${objectName} business object ID; response: ${response.statusCode} ${response.getContentAsString()}")
}

List<Map<String, String>> getTemplate(String busObId, String storeKey){
    def template = MemoryStore.lookup(storeKey)
    return template == null ? retrieveTemplate(busObId, storeKey) : template
}

List<Map<String, String>> retrieveTemplate(String busObId, String storeKey){
    Map headers = [:]
    addAuthorizationHeader(headers, accessToken)
    headers[HttpHeaders.ACCEPT] = "application/json"
    headers[HttpHeaders.CONTENT_TYPE] = "application/json"

    Map postParams = [:]
    postParams.put("busObId", busObId)
    postParams.put("includeAll", true)
    logger.debug("Will retrieve ${busObId}'s template with payload:${JsonOutput.toJson(postParams)}")
    def response = HTTP_CLIENT.post(apiUrl + "/api/V1/getbusinessobjecttemplate/", new StringEntity(JsonOutput.toJson(postParams)), headers)
    if (response.content != null) {
        responseMap = new JsonSlurper().parseText(response.getContentAsString())
        if (response.getStatusCode() < 300 && responseMap.get("fields") != null) {
            logger.debug("Successfully retrieved ${busObId} 's template: ${responseMap.get("fields")}")
            MemoryStore.store(storeKey, responseMap.get("fields"))
            return responseMap.get("fields")
        }
    }
    throw new Exception("${LOG_PREFIX} Could not acquire ${storeKey}; response: ${response.statusCode} ${response.getContentAsString()}")
}

String getFieldId(String busObId, String templateStoreKey, String displayName) {
    List<Map<String, String>> template = getTemplate(busObId, templateStoreKey)
    Map<String, String> field = template.find { map -> displayName.equals(map.get("displayName")) }
    if (field != null) {
        logger.debug("Found FieldId with displayName:" + displayName + " fieldId:" + field.fieldId)
        return field.fieldId
    } else {
        throw new Exception("${LOG_PREFIX} Could not find fieldId with displayName: ${displayName}")
    }
}

String getOGCustomerId(){
    String ogCustomerId = MemoryStore.lookup(StoreKey.CUSTOMER_OG_ID)
    return ogCustomerId == null ? retrieveOGCustomerId() : ogCustomerId
}

String retrieveOGCustomerId(){
    String customerBusObId = getBusObId("CustomerInternal",StoreKey.CUSTOMER_BUS_OBJ_ID)
    Map headers = [:]
    addAuthorizationHeader(headers, accessToken)
    headers[HttpHeaders.ACCEPT] = "application/json"

    Map queryParams = ["includerelationships" : true]
    def response = HTTP_CLIENT.get(apiUrl + "/api/V1/getbusinessobjectschema/busobid/"+customerBusObId, queryParams, headers)
    if (response.content != null) {
        responseMap = new JsonSlurper().parseText(response.getContentAsString())
        if (response.getStatusCode() < 300 && responseMap.get("fieldDefinitions") != null) {
            Map<String, String> fullNameFieldMap = responseMap.get("fieldDefinitions").find { map -> "Full name".equals(map.get("displayName")) }
            if(fullNameFieldMap != null) {
                logger.debug("Successfully retrieved customer's FullNameFieldID: ${fullNameFieldMap.get("fieldId")}")
                fullNameFieldId = fullNameFieldMap.get("fieldId")
            }
            else{
                throw new Exception("${LOG_PREFIX} Could not retrieve customer's FullNameFieldID")
                }
            }

            Map searchPayload = [:]
            searchPayload.put("busObId", customerBusObId)
            searchPayload.put("filters", [new Condition(fieldId: fullNameFieldId, operator: "eq", value:"OpsGenie")])
            searchPayload.put("includeAllFields", true)

            headers[HttpHeaders.CONTENT_TYPE] = "application/json"
            def searchResponse = HTTP_CLIENT.post(apiUrl + "/api/V1/getsearchresults", new StringEntity(JsonOutput.toJson(searchPayload)), headers)
            if (searchResponse.content != null) {
                searchResponseMap = new JsonSlurper().parseText(searchResponse.getContentAsString())
                searchResponseBusinessObjects = searchResponseMap.get("businessObjects")
                String ogCustBusObRecId = searchResponseBusinessObjects?.get(0)?.get("busObRecId")
                if (searchResponse.getStatusCode() < 300 && ogCustBusObRecId != null) {
                    MemoryStore.store(StoreKey.CUSTOMER_OG_ID, ogCustBusObRecId)
                    logger.debug("Found internal customer named 'OpsGenie' busObRecId: ${ogCustBusObRecId}")
                    return ogCustBusObRecId
                }

            throw new Exception("${LOG_PREFIX} Could not find internal customer named 'OpsGenie'")
        }
    }
    throw new Exception("${LOG_PREFIX} Could not acquire business object schema of ${customerBusObId}; response: ${response.statusCode} ${response.getContentAsString()}")
}

String getJournalNoteBusObId(){
    String journalNoteBusObId = MemoryStore.lookup(StoreKey.JOURNAL_NOTE_ID)
    return journalNoteBusObId == null ? retrieveJournalNoteBusObId() : journalNoteBusObId
}

String retrieveJournalNoteBusObId(){
    Map headers = [:]
    addAuthorizationHeader(headers, accessToken)
    headers[HttpHeaders.ACCEPT] = "application/json"

    def response = HTTP_CLIENT.get(apiUrl + "/api/V1/getbusinessobjectsummary/busobname/Journal", new HashMap<>(), headers)
    if (response.content != null) {
        responseMapArr = new JsonSlurper().parseText(response.getContentAsString())
        if (response.getStatusCode() < 300 && responseMapArr.get(0)?.get("groupSummaries") != null) {
            Map<String, String> journalNote = responseMapArr.get(0)?.get("groupSummaries")?.find { map -> "Journal - Note".equals(map.get("displayName")) }
            if(journalNote != null) {
                logger.debug("Successfully retrieved Journal - Note's busObId: ${journalNote.get("busObId")}")
                MemoryStore.store(StoreKey.JOURNAL_NOTE_ID, journalNote.get("busObId"))
                return journalNote.get("busObId")
            }
        }
    }
    throw new Exception("${LOG_PREFIX} Could not acquire Journal - Note's business object ID; response: ${response.statusCode} ${response.getContentAsString()}")
}

String getIncidentJournalRelationshipId(){
    String relationshipId = MemoryStore.lookup(StoreKey.JOURNAL_RELATIONSHIP_ID)
    return relationshipId == null ? retrieveIncidentJournalRelationshipId() : relationshipId
}

String retrieveIncidentJournalRelationshipId(){
    Map headers = [:]
    addAuthorizationHeader(headers, accessToken)
    headers[HttpHeaders.ACCEPT] = "application/json"

    Map queryParams = ["includerelationships" : true]
    def response = HTTP_CLIENT.get(apiUrl + "/api/V1/getbusinessobjectschema/busobid/"+getBusObId("Incident",StoreKey.INCIDENT_ID), queryParams, headers)
    if (response.content != null) {
        responseMap = new JsonSlurper().parseText(response.getContentAsString())
        if (response.getStatusCode() < 300 && responseMap.get("relationships") != null) {
            Map<String, String> incidentOwnsJournals
            for(Map<String, String> relationship : responseMap.get("relationships")){
                if("Incident Owns Journals".equals(relationship.get("displayName"))){
                    incidentOwnsJournals = relationship
                    break
                }
            }

            if(incidentOwnsJournals != null) {
                logger.debug("Successfully retrieved Incident owns Journals's relationship ID: ${incidentOwnsJournals.get("relationshipId")}")
                MemoryStore.store(StoreKey.JOURNAL_RELATIONSHIP_ID, incidentOwnsJournals.get("relationshipId"))
                return incidentOwnsJournals.get("relationshipId")
            }
        }
    }
    throw new Exception("${LOG_PREFIX} Could not acquire Incident owns Journals's relationship ID; response: ${response.statusCode} ${response.getContentAsString()}")
}

boolean incidentHasOwner(String incidentPublicId){
    Map headers = [:]
    addAuthorizationHeader(headers, accessToken)
    headers[HttpHeaders.ACCEPT] = "application/json"

    def response = HTTP_CLIENT.get(apiUrl + "/api/V1/getbusinessobject/busobid/"+getBusObId("Incident",StoreKey.INCIDENT_ID)+"/publicid/"+incidentPublicId, new HashMap<>() , headers)

    if (response.content != null) {
        responseMap = new JsonSlurper().parseText(response.getContentAsString())
        if (response.getStatusCode() < 300 && responseMap.get("fields") != null) {
            Map<String, String> ownedByField = null
            for(Map<String, String> field : responseMap.get("fields")){
                if("Owned By".equals(field.get("displayName"))){
                    ownedByField = field
                    break
                }
            }

            return !ownedByField?.get("value")?.equals("")
        }
    }
    throw new Exception("${LOG_PREFIX} Could not acquire Incident's ownership information; response: ${response.statusCode} ${response.getContentAsString()}")
}

String parseConfigIfNullOrEmpty(String property, String propertyKey) {
    if (property == null || "" == property) {
        return _conf(propertyKey, true)
    } else {
        return property
    }
}

static def addAuthorizationHeader(Map headers, String accessToken){
    headers.put("Authorization", "Bearer " + accessToken)
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

def _conf(confKey, boolean isMandatory) {
    def confVal = conf[CONF_PREFIX + confKey]
    if (isMandatory && confVal == null) {
        def errorMessage = "${LOG_PREFIX} Skipping action, Mandatory Conf item ${CONF_PREFIX + confKey} is missing. Check your marid conf file."
        throw new Exception(errorMessage)
    }
    return confVal
}

static String getPriorityAsInteger(String priority) {
    if ("P1" == priority) {
        return "1"
    } else if ("P2" == priority) {
        return "2"
    } else if ("P3" == priority) {
        return "3"
    } else if ("P4" == priority) {
        return "4"
    }  else if ("P5" == priority) {
        return "5"
    }
    return "3"
}

String getDescription(){
    String description = params.description?.trim() + "\n" + "og_alias:["+params.alias+"]" ? params.description : "-"
    return "${description}\nog_alias:[${params.alias}]"
}

class StoreKey{
    public static String INCIDENT_TEMPLATE = "incidentTemplate"
    public static String INCIDENT_ID = "incidentId"
    public static String CUSTOMER_BUS_OBJ_ID = "customerId"
    public static String CUSTOMER_OG_ID = "customerId"
    public static String JOURNAL_ID = "journalId"
    public static String JOURNAL_NOTE_ID = "journalNoteId"
    public static String JOURNAL_TEMPLATE = "journalTemplate"
    public static String JOURNAL_RELATIONSHIP_ID = "journalRelationshipId"
}

class BusinessObjectField{
    boolean dirty
    String displayName
    String fieldId
    String name
    String value
}

class Condition{
    String fieldId
    String operator
    String value
}