import com.ifountain.opsgenie.client.util.JsonUtils

try {

    def contentParams = JsonUtils.parse(request.getContent())
    logger.warn("Will send request to OpsGenie, evid: ${contentParams.evid}, eventState: ${contentParams.eventState} ");

    if (logger.isDebugEnabled()) {
        logger.debug("marid2opsgenie params:${contentParams}")
    }
    opsgenie.sendToIntegration("/v1/json/zenoss", contentParams, [:])
}
catch (Throwable e){
    logger.warn("Exception occurred while sending request to OpsGenie, request ${request.getContent()}, Reason: ${e}",e)

}