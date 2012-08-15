import com.ifountain.opsgenie.client.util.JsonUtils
/********************** CONFIGURATION ************************/
recipients = "cloudwatchgroup"
/*************************************************************/
Map snsMessage = getMessage(request);
if(AmazonSnsEndPointUtils.MESSAGE_TYPE_SUBSCRIPTION_CONFIRMATION.equals(AmazonSnsEndPointUtils.getRequestType(request))){
    AmazonSnsEndPointUtils.confirmSubscription(snsMessage);
}
else if(AmazonSnsEndPointUtils.MESSAGE_TYPE_NOTIFICATION.equals(AmazonSnsEndPointUtils.getRequestType(request))){
    Map messageProps = AmazonSnsEndPointUtils.getCloudwatchMessage(snsMessage);
    String subject = AmazonSnsEndPointUtils.getSubject(snsMessage);
    def alertProps = [recipients:recipients, message:subject,  details:messageProps]
    logger.warn("Creating alert with message ${subject}");
    def response = opsgenie.createAlert(alertProps)
    def alertId =  response.alertId;
    logger.warn("Alert is created with id :"+alertId);
}


protected Map getMessage(request) throws Exception{
    String contentAsString = request.getContent();
    if (contentAsString != null) {
        return JsonUtils.parse(contentAsString);
    } else {
        throw new Exception("No request content received");
    }
}