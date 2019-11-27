package com.opsgenie.plugin.service;

import com.opsgenie.plugin.exception.OpsgenieUserCreationFailedException;
import com.opsgenie.plugin.model.OpsgeniePluginSettings;

import java.util.Optional;

public interface OpsgeniePluginSettingsManager {

    String OG_PLUGIN_SETTINGS = "com.opsgenie.plugin";
    String API_KEY = "apiKey";
    String BASE_URL = "baseUrl";
    String SERVER_URL = "serverUrl";
    String SERVER_ID = "serverId";
    String SELECTED_PROJECTS = "selectedProjects";
    String SETUP_ENDPOINT = "/v1/jira-adapter/server/setup";


    void setServerId(String serverId);

    Optional<String> getServerId();

    void createOpsgenieUser() throws OpsgenieUserCreationFailedException;

    Optional<OpsgeniePluginSettings> getSettings();

    void saveSettings(OpsgeniePluginSettings opsgeniePluginSettings);

    void updateSettings(OpsgeniePluginSettings opsgeniePluginSettings);

    void deleteSettings();
}
