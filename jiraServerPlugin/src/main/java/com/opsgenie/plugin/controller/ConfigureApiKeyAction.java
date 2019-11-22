package com.opsgenie.plugin.controller;

import com.atlassian.jira.web.action.JiraWebActionSupport;
import com.atlassian.plugin.spring.scanner.annotation.component.Scanned;
import com.opsgenie.plugin.service.OpsgeniePluginSettingsManager;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.validator.routines.UrlValidator;

import javax.inject.Inject;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Scanned
public class ConfigureApiKeyAction extends JiraWebActionSupport {

    private final OpsgeniePluginSettingsManager opsgeniePluginSettingsManager;

    private String apiKey;

    private String baseUrl;

    private static final String API_KEY = "apiKey";
    private static final String REGION = "baseUrl";


    @Inject
    public ConfigureApiKeyAction(OpsgeniePluginSettingsManager opsgeniePluginSettingsManager) {
        this.opsgeniePluginSettingsManager = opsgeniePluginSettingsManager;
        this.opsgeniePluginSettingsManager.setServerId(getServerId());
    }

    @Override
    protected void doValidation() {
        if (StringUtils.isNotBlank(baseUrl) && !UrlValidator.getInstance().isValid(baseUrl)) {
            addErrorMessage("Base url is not valid!");
            setBaseUrl("");
        }
        if (StringUtils.isNotBlank(apiKey) && !isApiKeyValid(apiKey)) {
            addErrorMessage("Api key format is not valid!");
            setApiKey("");
        }
    }

    @Override
    public String doDefault() throws Exception {
        setApiKey(opsgeniePluginSettingsManager.getApiKey());
        setBaseUrl(Optional.ofNullable(opsgeniePluginSettingsManager.getBaseUrl())
                .orElse("https://api.opsgenie.com"));
        return super.doDefault();
    }

    @Override
    protected String doExecute() throws Exception {
        String apiKey = Optional.ofNullable(retrieveParameter(API_KEY, getHttpRequest().getParameterMap()))
                .orElse(opsgeniePluginSettingsManager.getApiKey());
        String region = Optional.ofNullable(retrieveParameter(REGION, getHttpRequest().getParameterMap()))
                .orElse(opsgeniePluginSettingsManager.getBaseUrl());
        opsgeniePluginSettingsManager.setApiKey(apiKey);
        opsgeniePluginSettingsManager.setBaseUrl(region);
        setApiKey(apiKey);
        setBaseUrl(region);
        return super.doExecute();
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    private String retrieveParameter(String key, Map<String, String[]> parameterMap) {
        String[] params = parameterMap.get(key);
        if (ArrayUtils.isNotEmpty(params)) {
            return params[0];
        }
        return null;
    }

    private boolean isApiKeyValid(String apiKey) {
        try {
            UUID.fromString(apiKey);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
