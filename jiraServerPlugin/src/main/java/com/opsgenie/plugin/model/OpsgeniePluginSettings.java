package com.opsgenie.plugin.model;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.validator.routines.UrlValidator;

import javax.validation.ValidationException;
import java.util.*;

public class OpsgeniePluginSettings {

    private String apiKey;

    private String baseUrl;

    private String serverUrl;

    private List<ProjectDto> selectedProjects;

    public OpsgeniePluginSettings() {
    }

    public OpsgeniePluginSettings(String apiKey, String baseUrl, String serverUrl, List<ProjectDto> selectedProjects) {
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
        this.serverUrl = serverUrl;
        this.selectedProjects = selectedProjects;
    }

    public String getApiKey() {
        return apiKey;
    }

    public OpsgeniePluginSettings setApiKey(String apiKey) {
        this.apiKey = apiKey;
        return this;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public OpsgeniePluginSettings setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
        return this;
    }

    public String getServerUrl() {
        return serverUrl;
    }

    public OpsgeniePluginSettings setServerUrl(String serverUrl) {
        this.serverUrl = serverUrl;
        return this;
    }

    public List<ProjectDto> getSelectedProjects() {
        return selectedProjects;
    }

    public OpsgeniePluginSettings setSelectedProjects(List<ProjectDto> selectedProjects) {
        this.selectedProjects = selectedProjects;
        return this;
    }

    public List<String> validate() {
        List<String> errorMessages = new ArrayList<>();
        if (StringUtils.isNotBlank(baseUrl) && !UrlValidator.getInstance().isValid(baseUrl)) {
            errorMessages.add("Base url is not valid!");
            setBaseUrl("");
        }
        if (StringUtils.isNotBlank(apiKey) && !isApiKeyValid(apiKey)) {
            errorMessages.add("Api key format is not valid!");
            setApiKey("");
        }
        if (StringUtils.isNotBlank(serverUrl) && !UrlValidator.getInstance().isValid(serverUrl)) {
            errorMessages.add("JiraServer url is not valid!");
            setServerUrl("");
        }
        if (StringUtils.isNotBlank(serverUrl) && StringUtils.contains(serverUrl, "opsgenie.com")) {
            errorMessages.add("JiraServer url cannot contains opsgenie.com");
            setServerUrl("");
        }
        if (CollectionUtils.isNotEmpty(selectedProjects) && selectedProjects.stream()
                .anyMatch(projectDto -> StringUtils.isBlank(projectDto.getName()) || projectDto.getId() == 0)) {
            errorMessages.add("Projects dto is not valid!");
            setSelectedProjects(Collections.emptyList());
        }
        return errorMessages;
    }

    public void validateBeforeSave() {
        if (StringUtils.isBlank(apiKey)) {
            throw new ValidationException("apiKey cannot be empty!");
        }
        if (StringUtils.isBlank(baseUrl)) {
            throw new ValidationException("baseUrl cannot be empty!");
        }
        if (CollectionUtils.isEmpty(selectedProjects)) {
            throw new ValidationException("selectedProjects cannot be empty!");
        }
        List<String> violations = validate();
        if (CollectionUtils.isNotEmpty(violations)) {
            throw new ValidationException(violations.toString());
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof OpsgeniePluginSettings)) return false;
        OpsgeniePluginSettings that = (OpsgeniePluginSettings) o;
        return Objects.equals(getApiKey(), that.getApiKey()) &&
                Objects.equals(getBaseUrl(), that.getBaseUrl()) &&
                Objects.equals(getServerUrl(), that.getServerUrl()) &&
                Objects.equals(getSelectedProjects(), that.getSelectedProjects());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getApiKey(), getBaseUrl(), getServerUrl(), getSelectedProjects());
    }

    @Override
    public String toString() {
        return "OpsgeniePluginSettings{" +
                "apiKey='" + apiKey + '\'' +
                ", baseUrl='" + baseUrl + '\'' +
                ", serverUrl='" + serverUrl + '\'' +
                ", selectedProjects=" + selectedProjects +
                '}';
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
