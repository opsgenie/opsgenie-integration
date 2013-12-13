import com.ifountain.opsgenie.client.OpsGenieClientException

/********************** CONFIGURATION ************************/
//recipients can be users or groups created in OpsGenie
recipients = ["all"]
actions = []
ATTACH_GRAPHS = true
def snsMessageConfig = [
    GRAPH_LAST_N_HOURS:24
]
/*************************************************************/
snsMessageConfig.AWS_ACCESS_KEY = conf.AWS_ACCESS_KEY
snsMessageConfig.AWS_SECRET_KEY = conf.AWS_SECRET_KEY
AmazonSnsMessage snsMessage = new AmazonSnsMessage(request, snsMessageConfig)
/*********************************************/
if(AmazonSnsMessage.MESSAGE_TYPE_SUBSCRIPTION_CONFIRMATION.equals(snsMessage.getRequestType())){
    snsMessage.confirmSubscription();
    logger.warn("Subscription Confirmed")
}
else if(AmazonSnsMessage.MESSAGE_TYPE_NOTIFICATION.equals(snsMessage.getRequestType())){
    def alertId = processAlert(snsMessage);
    if(ATTACH_GRAPHS){
        if(!alertId) {
            logger.warn("Will not create graphs, since alert not created");
        }
        else
        {
            if(conf.AWS_ACCESS_KEY == null){
                throw new Exception("AWS_ACCESS_KEY need to be defined in marid.conf file");
            }
            if(conf.AWS_SECRET_KEY == null){
                throw new Exception("AWS_SECRET_KEY need to be defined in marid.conf file");
            }
            def graphs = snsMessage.createMetricGraphs()
            attachMetricGraphs(graphs, alertId);
        }
    }
}

def processAlert(AmazonSnsMessage snsMessage){
    String alertDescription = snsMessage.getCloudwatchAlertDescription()
    Map details = snsMessage.getCloudwatchAlertDetails()
    String subject = snsMessage.getSubject()
    Map cwMessage = snsMessage.getCloudwatchMessage();

    logger.warn("Processing alert cloudWatchMessage:${cwMessage}");

    def alertProps = [:]
    alertProps.recipients = params.recipients?params.recipients.split(","):recipients;
    alertProps.message = params.message?params.message:subject
    alertProps.description = params.description?params.description:alertDescription
    alertProps.actions = params.actions?params.actions.split(","):actions
    alertProps.details = details

    //if setAlias given
    if(params.setAlias){
        String alias;
        cwMessage.keySet().each { key->
            if(key.toString().equalsIgnoreCase(params.setAlias)){
                alias=cwMessage[key.toString()];
            }
        }
        if (alias){
            alertProps.alias=alias ;
        }
    }
    //if alias given
    if(params.alias){
        alertProps.alias = params.alias;
    }

    boolean closeAlert = alertProps.alias && cwMessage.NewStateValue == snsMessage.OK_STATE;
    if(closeAlert){
        try{
            logger.warn("Closing alert with alias:[${alertProps.alias}]")
            opsgenie.closeAlert(alertProps);
        }
        catch (OpsGenieClientException clientEx){
            logger.warn("Can't close alert with alias:[${alertProps.alias}], possibly deleted. Reason: ${clientEx}")
        }
        return "";
    }
    else{
        logger.warn("Creating alert with alias:[${alertProps.alias}] message:[${alertProps.message}]");
        def response = opsgenie.createAlert(alertProps)
        def alertId =  response.alertId;
        logger.warn("Alert is created with id:[${alertId}] message:[${alertProps.message}]");
        return alertId;
    }
}


def attachMetricGraphs(graphs, alertId){
    graphs.each{graph->
        logger.warn("Attaching graphs ${graph.name}");
        def response = opsgenie.attach([alertId:alertId, stream:new ByteArrayInputStream(graph.data), fileName:graph.name])
        if(response.success){
            logger.warn("Successfully attached search results as ${graph.name}");
        }
    }
}