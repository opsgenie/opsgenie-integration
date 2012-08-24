/********************** CONFIGURATION ************************/
recipients = ["cloudwatchgroup"]
actions = []
ATTACH_GRAPHS = true
def snsMessageConfig = [
    ADDITIONAL_METRICS:[CPUUtilization:["DiskReadBytes", "NetworkIn", "NetworkOut" , "DiskWriteBytes"]],
    LAST_N_DAYS:10,
    STAT_PERIOD:300,
]
/*************************************************************/
snsMessageConfig.AWS_ACCESS_KEY = conf.AWS_ACCESS_KEY
snsMessageConfig.AWS_SECRET_KEY = conf.AWS_SECRET_KEY
AmazonSnsMessage snsMessage = new AmazonSnsMessage(request, snsMessageConfig)
/*********************************************/
if(AmazonSnsMessage.MESSAGE_TYPE_SUBSCRIPTION_CONFIRMATION.equals(snsMessage.getRequestType())){
    snsMessage.confirmSubscription();
}
else if(AmazonSnsMessage.MESSAGE_TYPE_NOTIFICATION.equals(snsMessage.getRequestType())){
    if(ATTACH_GRAPHS){
        if(conf.AWS_ACCESS_KEY == null){
            throw new Exception("AWS_ACCESS_KEY need to be defined in marid.conf file");
        }
        if(conf.AWS_SECRET_KEY == null){
            throw new Exception("AWS_SECRET_KEY need to be defined in marid.conf file");
        }
        def graphs = snsMessage.createMetricGraphs()
        def alertId = createAlert(snsMessage);
        attachMetricGraphs(graphs, alertId);
    }
    else{
        createAlert(snsMessage);
    }
}

def createAlert(AmazonSnsMessage snsMessage){
    String alertDescription = snsMessage.getCloudwatchAlertDescription()
    Map details = snsMessage.getCloudwatchAlertDetails()
    String subject = snsMessage.getSubject()
    def alertProps = [:]
    alertProps.recipients = params.recipients?params.recipients.split(","):recipients;
    alertProps.message = params.message?params.message:subject
    alertProps.description = params.description?params.description:alertDescription
    alertProps.actions = params.actions?params.actions.split(","):actions
    alertProps.details = details
    logger.warn("Creating alert with message ${subject}");
    def response = opsgenie.createAlert(alertProps)
    def alertId =  response.alertId;
    logger.warn("Alert is created with id :"+alertId);
    return alertId;
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