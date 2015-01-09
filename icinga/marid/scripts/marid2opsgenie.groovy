import com.ifountain.opsgenie.client.util.JsonUtils

try {

    def contentParams = JsonUtils.parse(request.getContent())
    logger.warn("Will send request to OpsGenie, host_event_id: ${contentParams.host_event_id}, service_event_id: ${contentParams.service_event_id} ");

    if (logger.isDebugEnabled()) {
        logger.debug("marid2opsgenie params:${contentParams}")
    }
    opsgenie.sendToIntegration("/v1/json/icinga", contentParams, [:])
}
catch (Throwable e){
    logger.warn("Exception occurred while sending request to OpsGenie, request ${request.getContent()}, Reason: ${e}",e)

}