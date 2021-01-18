package com.opsgenie.plugin.service;

import com.opsgenie.plugin.exception.OpsgenieUserCreationFailedException;
import com.opsgenie.plugin.model.OpsgeniePluginSettings;

import java.util.Optional;

public interface OpsgeniePluginSettingsManager {

    String OG_PLUGIN_SETTINGS = "com.opsgenie.plugin";
    String SERVER_ID = "serverId";
    String SETUP_ENDPOINT = "/v1/jira-adapter/server/setup";
    String OPSGENIE_USERNAME = "Opsgenie";

    void setServerId(String serverId);

    Optional<String> getServerId();

    void createOpsgenieConnection(OpsgeniePluginSettings opsgeniePluginSettings) throws OpsgenieUserCreationFailedException;

    Optional<OpsgeniePluginSettings> getSettings();

    void saveSettings(OpsgeniePluginSettings opsgeniePluginSettings);

    void updateSettings(OpsgeniePluginSettings opsgeniePluginSettings);

    void deleteSettings();

}
