/********************** CONFIGURATION ************************/
recipients = "cloudwatchgroup"
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
    def alertProps = [recipients:recipients, message:subject,  details:details, description:alertDescription]
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