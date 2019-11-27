package com.opsgenie.plugin.service;

import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.jira.user.UserDetails;
import com.atlassian.jira.user.util.UserManager;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import com.atlassian.sal.api.pluginsettings.PluginSettings;
import com.atlassian.sal.api.pluginsettings.PluginSettingsFactory;
import com.google.gson.Gson;
import com.opsgenie.plugin.exception.OpsgenieUserCreationFailedException;
import com.opsgenie.plugin.listener.SendResult;
import com.opsgenie.plugin.model.OpsgeniePluginSettings;
import com.opsgenie.plugin.model.OpsgenieUser;
import org.apache.commons.lang.StringUtils;
import org.springframework.stereotype.Component;

import javax.inject.Inject;
import javax.validation.ValidationException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Component
public class OpsgeniePluginSettingsManagerImpl implements OpsgeniePluginSettingsManager {

    private final UserManager userManager;

    private final OpsgenieClient opsgenieClient;

    private static final Gson gson = new Gson();

    private PluginSettings pluginSettings;

    @Inject
    public OpsgeniePluginSettingsManagerImpl(@ComponentImport PluginSettingsFactory pluginSettingsFactory, UserManager userManager, OpsgenieClient opsgenieClient) {
        this.userManager = userManager;
        this.opsgenieClient = opsgenieClient;
        pluginSettings = pluginSettingsFactory.createSettingsForKey(OG_PLUGIN_SETTINGS);
    }


    @Override
    public void setServerId(String serverId) {
        pluginSettings.put(SERVER_ID, serverId);
    }

    @Override
    public Optional<String> getServerId() {
        return Optional.ofNullable(castToTarget(pluginSettings.get(SERVER_ID), String.class));
    }

    @Override
    public void createOpsgenieUser() throws OpsgenieUserCreationFailedException {
        final OpsgeniePluginSettings opsgeniePluginSettings = getSettings()
                .orElseThrow(() -> new ValidationException("There is no configuration found!"));
        final String username = "Opsgenie";
        final String apiKey = opsgeniePluginSettings.getApiKey();
        if (StringUtils.isBlank(apiKey)) {
            throw new ValidationException("In order to create plugin user, an apiKey have to be configured first!");
        }
        final String baseUrl = opsgeniePluginSettings.getBaseUrl();
        if (StringUtils.isBlank(baseUrl)) {
            throw new ValidationException("In order to create plugin user, a baseUrl have to be configured first!");
        }
        ApplicationUser applicationUser = userManager.getUserByName(username);
        //plugin settings may not be sync with the user existence. If user deleted then the customer need to reinstall the plugin in order to add new user :((
        if (applicationUser == null && StringUtils.isNotBlank(apiKey) && StringUtils.isNotBlank(baseUrl)) {
            UserDetails userDetails = new UserDetails(username, username)
                    .withPassword(apiKey);
            try {
                String serverUrl = opsgeniePluginSettings.getServerUrl();
                OpsgenieUser opsgenieUser = new OpsgenieUser(username, apiKey);
                userManager.createUser(userDetails);
                SendResult result = opsgenieClient.post(baseUrl + SETUP_ENDPOINT, apiKey, gson.toJson(buildDto(opsgenieUser, serverUrl)));
                if (!result.isSuccess()) {
                    throw new Exception(result.getFailReason() +
                            ". Created User needs to be deleted and plugin needs to be reconfigured.");
                }
            } catch (Exception e) {
                deleteSettings();
                throw new OpsgenieUserCreationFailedException(e.getMessage());
            }
        }
    }


    @Override
    public Optional<OpsgeniePluginSettings> getSettings() {
        String settingsAsJson = castToTarget(pluginSettings.get(OG_PLUGIN_SETTINGS), String.class);
        if (StringUtils.isNotBlank(settingsAsJson)) {
            try {
                return Optional.of(gson.getAdapter(OpsgeniePluginSettings.class).fromJson(settingsAsJson));
            } catch (Exception e) {
                throw new RuntimeException("Could not get settings. Reason: " + e.getMessage(), e);
            }
        }
        return Optional.empty();
    }

    @Override
    public void saveSettings(OpsgeniePluginSettings opsgeniePluginSettings) {
        if (opsgeniePluginSettings == null) {
            throw new ValidationException("OpsgeniePluginSettings cannot be null");
        }
        opsgeniePluginSettings.validateBeforeSave();
        pluginSettings.put(OG_PLUGIN_SETTINGS, gson.toJson(opsgeniePluginSettings));
    }

    @Override
    public void updateSettings(OpsgeniePluginSettings opsgeniePluginSettings) {
        Map<String, Object> dto = new HashMap<>();
        dto.put("serverUrl", opsgeniePluginSettings.getServerUrl());
        dto.put("serverId", getServerId().orElseThrow(() -> new ValidationException("serverId is empty!")));
        /*SendResult result = opsgenieClient.put(opsgeniePluginSettings.getBaseUrl() + SETUP_ENDPOINT, opsgeniePluginSettings.getApiKey(), gson.toJson(dto));
        if (!result.isSuccess()) {
            throw new ValidationException("Could not update serverUrl. Reason: " + result.getFailReason());
        } else {
            saveSettings(opsgeniePluginSettings);
        }*/
        saveSettings(opsgeniePluginSettings);
    }

    @Override
    public void deleteSettings() {
        pluginSettings.remove(OG_PLUGIN_SETTINGS);
    }

    private Map<String, Object> buildDto(OpsgenieUser opsgenieUser, String serverUrl) {
        Map<String, Object> dto = new HashMap<>();
        dto.put("username", opsgenieUser.getUsername());
        dto.put("password", opsgenieUser.getPassword());
        dto.put("serverId", getServerId().orElseThrow(() -> new ValidationException("serverId is empty!")));
        dto.put("serverUrl", serverUrl);
        return dto;
    }

    private <T> T castToTarget(Object value, Class<T> target) {
        if (value == null) {
            return null;
        }
        if (value.getClass().isAssignableFrom(target)) {
            return ((T) value);
        }
        return null;
    }


}
