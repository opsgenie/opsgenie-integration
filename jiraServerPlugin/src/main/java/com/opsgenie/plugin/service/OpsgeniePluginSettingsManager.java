package com.opsgenie.plugin.service;

public interface OpsgeniePluginSettingsManager {

    String getApiKey();

    void setApiKey(String apiKey);

    String getBaseUrl();

    void setBaseUrl(String region);

    void setServerId(String serverId);

    String getServerId();
}
