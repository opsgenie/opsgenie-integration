import com.ifountain.opsgenie.client.util.JsonUtils

def contentParams = JsonUtils.parse(request.getContent())
logger.debug("marid2opsgenie params:${contentParams}")
opsgenie.sendToIntegration("/v1/json/nagios", contentParams, [:])