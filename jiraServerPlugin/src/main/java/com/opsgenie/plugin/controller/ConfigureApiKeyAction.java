package com.opsgenie.plugin.controller;

import com.atlassian.jira.web.action.JiraWebActionSupport;
import com.atlassian.plugin.spring.scanner.annotation.component.Scanned;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import com.atlassian.sal.api.pluginsettings.PluginSettingsFactory;

import javax.inject.Inject;

@Scanned
public class ConfigureApiKeyAction extends JiraWebActionSupport {

    private String key;

    private String newApiKey;

    @ComponentImport
    private final PluginSettingsFactory pluginSettingsFactory;

    @Inject
    public ConfigureApiKeyAction(PluginSettingsFactory pluginSettingsFactory) {
        this.pluginSettingsFactory = pluginSettingsFactory;
    }

    @Override
    public String execute() throws Exception {
        setKey("gol");
        return "configureApiKey";
    }

    @Override
    protected String doExecute() throws Exception {
        return super.doExecute();
    }

    public String getKey() {
        return key;
    }

    public ConfigureApiKeyAction setKey(String key) {
        this.key = key;
        return this;
    }

    public String getNewApiKey() {
        return newApiKey;
    }

    public ConfigureApiKeyAction setNewApiKey(String newApiKey) {
        this.newApiKey = newApiKey;
        return this;
    }
}
