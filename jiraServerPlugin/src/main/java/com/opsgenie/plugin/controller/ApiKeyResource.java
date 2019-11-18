package com.opsgenie.plugin.controller;

import com.atlassian.plugin.spring.scanner.annotation.component.Scanned;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import com.atlassian.sal.api.pluginsettings.PluginSettings;
import com.atlassian.sal.api.pluginsettings.PluginSettingsFactory;
import com.opsgenie.plugin.model.ApiKey;

import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.Optional;

@Path("/apiKey")
@Scanned
public class ApiKeyResource {

    @ComponentImport
    private final PluginSettingsFactory pluginSettingsFactory;

    @Inject
    public ApiKeyResource(PluginSettingsFactory pluginSettingsFactory) {
        this.pluginSettingsFactory = pluginSettingsFactory;
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response get(@Context HttpServletRequest request) {
        System.out.println("apiKeyResource ------get");
        PluginSettings pluginSettings = pluginSettingsFactory.createGlobalSettings();
        Object apikeyValue = pluginSettings.get("og-apiKey");
        ApiKey apiKey = new ApiKey();
        apiKey.setKey((String) Optional.ofNullable(apikeyValue).orElse(""));
        return Response.ok(apiKey).build();
    }

    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    public Response put(final ApiKey apiKey, @Context HttpServletRequest request) {
        System.out.println("apiKeyResource ------put");
        PluginSettings pluginSettings = pluginSettingsFactory.createGlobalSettings();
        pluginSettings.put("og-apiKey", apiKey.getKey());
        return Response.ok(apiKey).build();
    }

}
