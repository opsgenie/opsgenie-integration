package com.opsgenie.plugin.service;

import com.opsgenie.plugin.listener.SendResult;

public interface OpsgenieClient {

    SendResult post(String endpoint, String apiKey, String dataAsJson);

    SendResult put(String endpoint, String apiKey, String dataAsJson);

    SendResult delete(String endpoint, String apiKey, String serverId);
}
