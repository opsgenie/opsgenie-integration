import org.apache.commons.lang.StringEscapeUtils

connParams = [:];
connParams.broker = conf["smarts.broker"];
connParams.username = conf["smarts.username"];
connParams.password = conf["smarts.password"];
connParams.brokerUsername = conf["smarts.brokerUsername"];
connParams.brokerPassword = conf["smarts.brokerPassword"];

def LOG_PREFIX ="[${action}]:";
logger.warn("${LOG_PREFIX} Will execute action for alertId ${alert.alertId}");

def alertFromOpsGenie = opsgenie.getAlert(["alertId": alert.alertId]);
def notificationName = alertFromOpsGenie.alias;
INSTANCE_NAME = alertFromOpsGenie.entity
CLASS_NAME = alertFromOpsGenie.details["ClassName"]
ELEMENT_CLASS_NAME = alertFromOpsGenie.details["ElementClassName"]
ELEMENT_NAME = alertFromOpsGenie.details["ElementName"]
DOMAIN_NAME = alertFromOpsGenie.details["DomainName"]
SOURCE_DOMAIN_NAME = alertFromOpsGenie.details["SourceDomainName"]

if(!notificationName)
{
    logger.error("${LOG_PREFIX} notificationName does not exists in alert details for alert id : ${alert.alertId}");
    return
}

if(!DOMAIN_NAME)
{
    logger.error("${LOG_PREFIX} domainName does not exists in alert details for alert id : ${alert.alertId}");
    return
}

connParams.domain=DOMAIN_NAME;
logger.warn("${LOG_PREFIX} Will execute action for alert ${notificationName} on Smarts");
SmartsDatasource.execute(connParams){ds->
    if(action == "Create"){
        if(CLASS_NAME != null && INSTANCE_NAME != null){
            attach(alert.alertId, CLASS_NAME, INSTANCE_NAME)
            if(ELEMENT_CLASS_NAME != null && ELEMENT_NAME != null && (CLASS_NAME != ELEMENT_CLASS_NAME || INSTANCE_NAME != ELEMENT_NAME)){
                attach(alert.alertId, ELEMENT_CLASS_NAME, ELEMENT_NAME)
            }
        }else{
            logger.error("Could not attach details. ClassName or InstanceName does not exist in alert do alert id: ${alert.alertId}. ClassName:  ${CLASS_NAME}, InstanceName: ${INSTANCE_NAME}")
            return
        }
    }
    else if(action == "Acknowledge")
    {
        ds.invokeNotificationOperation(notificationName, "acknowledge", alert.username, "Acknowledged via OpsGenie");
    }
    else if(action == "unacknowledge")
    {
        ds.invokeNotificationOperation(notificationName, "unacknowledge", alert.username, "Unacknowledged via OpsGenie");
    }
    else if(action == "TakeOwnership")
    {
        ds.invokeNotificationOperation(notificationName, "takeOwnership", alert.username, "TakeOwnership via OpsGenie");
    }
    else if(action == "release ownership")
    {
        ds.invokeNotificationOperation(notificationName, "releaseOwnership", alert.username, "ReleaseOwnership via OpsGenie");
    }
    else if(action == "AddNote")
    {
        ds.invokeNotificationOperation(notificationName, "addAuditEntry", alert.username, alert.note);
    }
    else
    {
        throw new Exception("Unknown action ${action}")
    }
}
logger.warn("${LOG_PREFIX} Executed action for alert ${notificationName} on Smarts");


def attach(alertId, className, instanceName){
    String htmlText = createHtml(className, instanceName);
    if(htmlText){
        logger.warn("Attaching ${className}:${instanceName} details");
        println "Attaching ${className}:${instanceName} details"
        response = opsgenie.attach([alertId:alertId, stream:new ByteArrayInputStream(htmlText.getBytes()), fileName:"${className}_${instanceName}.html"])
        if(response.success){
            logger.warn("Successfully attached ${className}:${instanceName} details");
            println "Successfully attached ${className}:${instanceName} details"
        }
        else{
            println "Could not attach ${className}:${instanceName} details"
            logger.warn("Could not attach ${className}:${instanceName} details");
        }
    }
    else{
        logger.warn("No Object found for ${className}:${instanceName}");
        println "No Object found for ${className}:${instanceName}"
    }
}

def createHtml(String className, String instanceName){
    StringBuffer bfr = new StringBuffer();
    bfr.append("""
        <html>
        <head>
            <title>Details of ${className}::${instanceName}</title>
            <style>
                table{
                    border:1px solid #cccccc;
                }

                table thead tr{
                    background-color: #F2F2F2;
                    text-align:left;
                }
                table thead tr th{
                    padding-top:8px;
                    padding-bottom:8px;
                }
                .odd{
                    background: none repeat scroll 0 0 #F9F9F9;
                }
                .well{
                    border: 1px solid #C0C0C0;
                    border-radius: 4px 4px 4px 4px;
                    padding: 5px;
                    margin:10px;
                }
                .lbl{
                    padding-right:30px;
                    font-weight:bold;
                }
                .well .heading{
                    border: 1px solid #212121;
                    color: white;
                    background-color: #323232;
                    border-radius: 4px 4px 0 0;
                    display: block;
                    font-size: 1.8rem;
                    min-width: 0;
                    padding: 15px;
                    position: relative;
                    z-index: 1;
                }
                .well .heading h2{
                    color: white;
                    font-size: 0.7em;
                    font-weight: normal;
                    margin: 0;
                    position: relative;
                    width: 87%;
                }
            </style>
        </head>
        <body>
        <div class="well">
        <div class="heading">
            <h2>${className}::${instanceName} Properties</h2>
        </div>
        <table cellspacing="0" width="100%">
            <thead>
                <tr>
                    <th width="0%"><span>Property Name</span></th>
                    <th width="100%">Property Value</th>
                </tr>
            </thead>
            <tbody>
    """)
    connParams.domain= SOURCE_DOMAIN_NAME;
    Map topologyAttributes = null;
    try{
        topologyAttributes = getTopologyObjectProperties(connParams, className, instanceName);
    }
    catch (Throwable t){
        if(t.toString().indexOf("is not registered with the broker") >= 0){
            connParams.domain= DOMAIN_NAME;
            try{
                topologyAttributes = getTopologyObjectProperties(connParams, className, instanceName);
            }
            catch(Throwable t1){
                if(t1.toString().indexOf("Object of given name and class not found") >= 0){
                    return null;
                }
                else{
                    throw t1;
                }
            }

        }
        else{
            throw t;
        }
    }
    def rowCount = 0;
    topologyAttributes.keySet().sort().each{String attributeName->
        rowCount++;
        StringBuffer valueBfr = new StringBuffer();
        def val = topologyAttributes[attributeName];
        if(val instanceof List){
            val.each{entry->
                valueBfr.append("""<div>${StringEscapeUtils.escapeHtml(entry.creationClassName)}::${StringEscapeUtils.escapeHtml(entry.instanceName)}</div>""")
            }
        }
        else if(val instanceof Map){
            valueBfr.append("""<div>${StringEscapeUtils.escapeHtml(val.creationClassName)}::${StringEscapeUtils.escapeHtml(val.instanceName)}</div>""")
        }
        else{
            valueBfr.append(StringEscapeUtils.escapeHtml(String.valueOf(val)))
        }
        bfr.append("""
        <tr ${rowCount%2==0?"class='odd'":""}>
            <td class="lbl">${StringEscapeUtils.escapeHtml(attributeName)}</td>
            <td>${valueBfr}</td>
        </tr>
    """)
    }
    bfr.append("""
        </tbody>
        </table>
        </div>
        </body>
        </html>
    """)
    return bfr.toString();
}

def getTopologyObjectProperties(connParams, className, instanceName){
    return SmartsDatasource.execute(connParams){ds->
        return ds.getAllProperties(className, instanceName)
    }
}
