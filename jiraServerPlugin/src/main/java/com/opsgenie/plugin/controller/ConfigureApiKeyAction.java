package com.opsgenie.plugin.controller;

import com.atlassian.jira.web.action.JiraWebActionSupport;
import com.atlassian.plugin.spring.scanner.annotation.component.Scanned;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.opsgenie.plugin.exception.OpsgenieUserCreationFailedException;
import com.opsgenie.plugin.model.OpsgeniePluginSettings;
import com.opsgenie.plugin.model.ProjectDto;
import com.opsgenie.plugin.service.OpsgeniePluginSettingsManager;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.validation.ValidationException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

@Scanned
public class ConfigureApiKeyAction extends JiraWebActionSupport {

    @Inject
    private final OpsgeniePluginSettingsManager opsgeniePluginSettingsManager;

    private String apiKey;

    private String baseUrl;

    private String serverUrl;

    private List<ProjectDto> projects;

    private List<ProjectDto> selectedProjects;

    private static final Logger logger = LoggerFactory.getLogger(ConfigureApiKeyAction.class);

    private Gson gson = new Gson();


    @Inject
    public ConfigureApiKeyAction(OpsgeniePluginSettingsManager opsgeniePluginSettingsManager) {
        this.opsgeniePluginSettingsManager = opsgeniePluginSettingsManager;
        this.opsgeniePluginSettingsManager.setServerId(getServerId());
    }

    @Override
    protected void doValidation() {
        OpsgeniePluginSettings settings = requestParamsToPluginSettings(getHttpRequest().getParameterMap());
        settings.validate().forEach(this::addErrorMessage);
        if (CollectionUtils.isNotEmpty(getErrorMessages())) {
            toDto(settings);
        }
        try {
            if (settings != null && isThereAnyNonEmptyField()) {
                settings.validateBeforeSave();
            }
        } catch (ValidationException e) {
            toDto(opsgeniePluginSettingsManager.getSettings().orElse(null));
            addErrorMessage(e.getMessage());
        }
    }

    @Override
    protected String doExecute() throws Exception {
        String requestMethod = getHttpRequest().getMethod();
        Optional<OpsgeniePluginSettings> optionalExistingSettings = opsgeniePluginSettingsManager.getSettings();
        OpsgeniePluginSettings newSettings = requestParamsToPluginSettings(getHttpRequest().getParameterMap());

        logger.info("Got Request: " + requestMethod + " Params: " + gson.toJson(getHttpRequest().getParameterMap()));
        try {
            if (requestMethod.equals("GET")) {
                toDto(opsgeniePluginSettingsManager.getSettings().orElse(null));
            } else if(optionalExistingSettings.isPresent()) {
                logger.debug("Settings already exist. Updating...");
                opsgeniePluginSettingsManager.updateSettings(newSettings);
            } else {
                logger.debug("Creating a connection...");
                opsgeniePluginSettingsManager.createOpsgenieConnection(newSettings);
                logger.debug("Connection creation successful. Saving Settings...");
                opsgeniePluginSettingsManager.saveSettings(newSettings);
            }
        } catch (ValidationException | OpsgenieUserCreationFailedException e) {
            addErrorMessage(e.getMessage());
        } finally {
            toDto(opsgeniePluginSettingsManager.getSettings().orElse(null));
            logger.info("...done!");
        }
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

    public String getServerUrl() {
        return serverUrl;
    }

    public ConfigureApiKeyAction setServerUrl(String serverUrl) {
        this.serverUrl = serverUrl;
        return this;
    }

    public List<ProjectDto> getProjects() {
        return projects;
    }

    public ConfigureApiKeyAction setProjects(List<ProjectDto> projects) {
        this.projects = projects;
        return this;
    }

    public List<ProjectDto> getSelectedProjects() {
        return selectedProjects;
    }

    public ConfigureApiKeyAction setSelectedProjects(List<ProjectDto> selectedProjects) {
        this.selectedProjects = selectedProjects;
        return this;
    }

    private List<ProjectDto> getAllProjects() {
        return getProjectManager().getProjects()
                .stream()
                .map(jiraProject -> new ProjectDto(jiraProject.getId(), jiraProject.getName()))
                .collect(Collectors.toList());
    }

    private OpsgeniePluginSettings requestParamsToPluginSettings(Map<String, String[]> parameterMap) {
        JsonObject requestJson = new JsonObject();
        for (Map.Entry<String, String[]> entry : parameterMap.entrySet()) {
            if ("project-select".equals(entry.getKey())) {
                requestJson.add("selectedProjects", gson.toJsonTree(toSelectedProjects(parameterMap.get("project-select"))));
            } else {
                requestJson.addProperty(entry.getKey(), entry.getValue()[0]);
            }
        }
        return gson.fromJson(requestJson, OpsgeniePluginSettings.class);
    }

    private List<ProjectDto> toSelectedProjects(String[] projectIds) {
        if (ArrayUtils.isEmpty(projectIds)) {
            throw new ValidationException("At least one project must be selected!");
        }
        List<ProjectDto> selectedProjects = new ArrayList<>();
        Arrays.stream(projectIds)
                .distinct()
                .forEach(projectId -> {
                    ProjectDto selectedProject = findProject(projectId);
                    if (selectedProject != null) {
                        selectedProjects.add(selectedProject);
                    }
                });
        return selectedProjects;
    }

    private ProjectDto findProject(String projectId) {
        return Optional.ofNullable(getAllProjects()).orElse(Collections.emptyList())
                .stream()
                .filter(projectDto -> String.valueOf(projectDto.getId()).equals(projectId))
                .findFirst()
                .orElse(null);
    }

    private String logPrefix() {
        return "[Opsgenie] ";
    }

    private void toDto(OpsgeniePluginSettings settings) {
        if (settings != null) {
            setApiKey(settings.getApiKey());
            setServerUrl(settings.getServerUrl());
            setBaseUrl(settings.getBaseUrl());
            setSelectedProjects(settings.getSelectedProjects());
        }
        if (StringUtils.isBlank(getBaseUrl())) {
            setBaseUrl("https://api.opsgenie.com");
        }
        setProjects(getAllProjects());
    }

    private boolean isThereAnyNonEmptyField() {
        return StringUtils.isNotBlank(apiKey) || StringUtils.isNotBlank(serverUrl) || StringUtils.isNotBlank(baseUrl) || CollectionUtils.isNotEmpty(selectedProjects);
    }
}
