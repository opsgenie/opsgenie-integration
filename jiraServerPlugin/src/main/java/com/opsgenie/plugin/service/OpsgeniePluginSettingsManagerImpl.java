package com.opsgenie.plugin.service;

import com.atlassian.jira.exception.CreateException;
import com.atlassian.jira.exception.PermissionException;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.jira.user.UserDetails;
import com.atlassian.jira.user.util.UserManager;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import com.atlassian.sal.api.pluginsettings.PluginSettings;
import com.atlassian.sal.api.pluginsettings.PluginSettingsFactory;
import com.google.gson.Gson;
import com.opsgenie.plugin.exception.OpsgenieSaveSettingsFailedException;
import com.opsgenie.plugin.exception.OpsgenieUserCreationFailedException;
import com.opsgenie.plugin.listener.SendResult;
import com.opsgenie.plugin.model.ConnectionSetupDto;
import com.opsgenie.plugin.model.ConnectionUpdateDto;
import com.opsgenie.plugin.model.OpsgeniePluginSettings;
import com.opsgenie.plugin.model.OpsgenieUser;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.inject.Inject;
import javax.validation.ValidationException;
import java.util.Optional;

@Component
public class OpsgeniePluginSettingsManagerImpl implements OpsgeniePluginSettingsManager {

    private final UserManager userManager;

    private final OpsgenieClient opsgenieClient;

    private static final Gson gson = new Gson();

    private PluginSettings pluginSettings;

    private static final Logger logger = LoggerFactory.getLogger(OpsgeniePluginSettingsManagerImpl.class);

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
    public void createOpsgenieConnection(OpsgeniePluginSettings opsgeniePluginSettings) throws OpsgenieUserCreationFailedException {
        logger.debug("Creating a connection...");
        final String username = OpsgeniePluginSettingsManager.OPSGENIE_USERNAME;
        final String apiKey = opsgeniePluginSettings.getApiKey();
        final String baseUrl = opsgeniePluginSettings.getBaseUrl();

        if (getUser().isPresent()) {
            throw new OpsgenieUserCreationFailedException("User: " + username + " already exists. Please remove it first!");
        };

        if (StringUtils.isNotBlank(apiKey) && StringUtils.isNotBlank(baseUrl)) {
            OpsgenieUser opsgenieUser = new OpsgenieUser(username, apiKey);
            ConnectionSetupDto connectionSetupDto = new ConnectionSetupDto()
                    .setUsername(opsgenieUser.getUsername())
                    .setPassword(opsgenieUser.getPassword())
                    .setServerId(getServerId().orElseThrow(() -> new ValidationException("serverId is empty!")))
                    .setServerUrl(opsgeniePluginSettings.getServerUrl());
            SendResult result = opsgenieClient.post(baseUrl + SETUP_ENDPOINT, apiKey, gson.toJson(connectionSetupDto));

            if (result.isSuccess()) {
                createUser(opsgenieUser);
            } else {
                throw new OpsgenieUserCreationFailedException(result.getFailReason());
            }
        }
    }

    public Optional<ApplicationUser> getUser() {
        ApplicationUser applicationUser = userManager.getUserByName(OpsgeniePluginSettingsManager.OPSGENIE_USERNAME);
        if (applicationUser == null) {
            return Optional.empty();
        } else {
            return Optional.of(applicationUser);
        }
    }

    @Override
    public String getServerUrl() {
        OpsgeniePluginSettings existingSettings = getSettings().orElseThrow(() -> new ValidationException("There is no configuration!"));
        return existingSettings.getServerUrl();
    }

    private void createUser(OpsgenieUser user) throws OpsgenieUserCreationFailedException {
        logger.debug("Creating user...");
        UserDetails userDetails = new UserDetails(user.getUsername(), user.getUsername())
                .withPassword(user.getPassword());
        try {
            userManager.createUser(userDetails);
        } catch (CreateException | PermissionException e) {
            throw new OpsgenieUserCreationFailedException(e.getMessage());
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
    public void saveSettings(OpsgeniePluginSettings opsgeniePluginSettings) throws OpsgenieSaveSettingsFailedException {
        logger.debug("Saving Settings...");
        if (opsgeniePluginSettings == null) {
            throw new ValidationException("Opsgenie Plugin Settings cannot be null");
        }

        try {
            opsgeniePluginSettings.validateBeforeSave();
            pluginSettings.put(OG_PLUGIN_SETTINGS, gson.toJson(opsgeniePluginSettings));
            logger.info("Settings saved successfully!");
        } catch (Exception e) {
            throw new OpsgenieSaveSettingsFailedException("Could not save settings: " + e.getMessage(), e);
        }
    }

    @Override
    public void updateSettings(OpsgeniePluginSettings opsgeniePluginSettings) throws OpsgenieUserCreationFailedException, OpsgenieSaveSettingsFailedException {
        logger.debug("Settings already exist. Updating...");
        ConnectionUpdateDto connectionUpdateDto = new ConnectionUpdateDto()
                .setServerId(getServerId().orElseThrow(() -> new ValidationException("Could not find server Id!")))
                .setServerUrl(opsgeniePluginSettings.getServerUrl());

        OpsgeniePluginSettings existingSettings = getSettings().orElseThrow(() -> new ValidationException("There is no configuration to update!"));

        OpsgenieUser opsgenieUser = new OpsgenieUser(OpsgeniePluginSettingsManager.OPSGENIE_USERNAME, opsgeniePluginSettings.getApiKey());
        boolean shouldCreateUser = !getUser().isPresent();
        if (shouldCreateUser) {
            connectionUpdateDto.setUsername(opsgenieUser.getUsername());
            connectionUpdateDto.setPassword(opsgenieUser.getPassword());
        }

        String endpoint = SETUP_ENDPOINT;
        if (!StringUtils.equals(opsgeniePluginSettings.getApiKey(), existingSettings.getApiKey())) {
            endpoint = SETUP_ENDPOINT + "/owner";
        }
        SendResult result = opsgenieClient.put(opsgeniePluginSettings.getBaseUrl() + endpoint, opsgeniePluginSettings.getApiKey(), gson.toJson(connectionUpdateDto));

        if (result.isSuccess()) {
            if (shouldCreateUser) {
                createUser(opsgenieUser);
            }
            saveSettings(opsgeniePluginSettings);
        } else {
            throw new ValidationException("Could not update the plugin settings. Reason: " + result.getFailReason());
        }
    }

    @Override
    public void deleteSettings(OpsgeniePluginSettings settings) {
        if (settings == null) {
            return;
        }

        logger.info("Deleting settings...");
        String url = settings.getBaseUrl() + SETUP_ENDPOINT;
        String serverId = getServerId().orElseThrow(() -> new ValidationException("serverId is empty!"));
        SendResult result = opsgenieClient.delete(url , settings.getApiKey(), serverId);

        if (!result.isSuccess()) {
            logger.error("Could not delete the plugin connection. Reason: " + result.getFailReason());
        }

        pluginSettings.remove(OG_PLUGIN_SETTINGS);
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
