logger.debug("marid2opsgenie params:${params}")
opsgenie.sendToIntegration("/v1/json/zabbix", params, [:])