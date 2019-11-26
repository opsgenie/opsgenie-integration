package com.opsgenie.plugin.service;

import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import com.atlassian.sal.api.pluginsettings.PluginSettings;
import com.atlassian.sal.api.pluginsettings.PluginSettingsFactory;
import org.springframework.stereotype.Component;

import javax.inject.Inject;

@Component
public class OpsgeniePluginSettingsManagerImpl implements OpsgeniePluginSettingsManager {

    private static final String API_KEY = "apiKey";
    private static final String BASE_URL = "baseUrl";
    private static final String SERVER_ID = "serverId";

    private final PluginSettingsFactory pluginSettingsFactory;

    @Inject
    public OpsgeniePluginSettingsManagerImpl(@ComponentImport PluginSettingsFactory pluginSettingsFactory) {
        this.pluginSettingsFactory = pluginSettingsFactory;
    }


    @Override
    public String getApiKey() {
        PluginSettings pluginSettings = pluginSettingsFactory.createGlobalSettings();
        return (String) pluginSettings.get(API_KEY);
    }

    @Override
    public void setApiKey(String apiKey) {
        PluginSettings pluginSettings = pluginSettingsFactory.createGlobalSettings();
        pluginSettings.put(API_KEY, apiKey);
    }

    @Override
    public String getBaseUrl() {
        PluginSettings pluginSettings = pluginSettingsFactory.createGlobalSettings();
        return (String) pluginSettings.get(BASE_URL);
    }

    @Override
    public void setBaseUrl(String region) {
        PluginSettings pluginSettings = pluginSettingsFactory.createGlobalSettings();
        pluginSettings.put(BASE_URL, region);
    }

    @Override
    public void setServerId(String serverId) {
        PluginSettings pluginSettings = pluginSettingsFactory.createGlobalSettings();
        pluginSettings.put(SERVER_ID, serverId);
    }

    @Override
    public String getServerId() {
        PluginSettings pluginSettings = pluginSettingsFactory.createGlobalSettings();
        return (String) pluginSettings.get(SERVER_ID);
    }
}
