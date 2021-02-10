package com.opsgenie.plugin.impl;

import com.atlassian.jira.project.ProjectManager;
import com.atlassian.jira.user.util.UserManager;
import com.atlassian.plugin.spring.scanner.annotation.export.ExportAsService;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import com.atlassian.sal.api.ApplicationProperties;
import com.atlassian.sal.api.pluginsettings.PluginSettingsFactory;
import com.opsgenie.plugin.api.OpsgeniePluginComponent;
import com.opsgenie.plugin.listener.IssueEventSenderFactory;
import com.opsgenie.plugin.service.OpsgeniePluginSettingsManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.inject.Inject;
import javax.inject.Named;

@ExportAsService({OpsgeniePluginComponent.class})
@Named("opsgeniePluginComponent")
@Component
public class OpsgeniePluginComponentImpl implements OpsgeniePluginComponent {
    @ComponentImport
    private final ApplicationProperties applicationProperties;

    @ComponentImport
    private final UserManager userManager;

    @ComponentImport
    private final ProjectManager projectManager;

    @Autowired
    private OpsgeniePluginSettingsManager opsgeniePluginSettingsManager;

    @Autowired
    private IssueEventSenderFactory issueEventSenderFactory;

    @ComponentImport
    private PluginSettingsFactory pluginSettingsFactory;

    @Inject
    public OpsgeniePluginComponentImpl(final ApplicationProperties applicationProperties, UserManager userManager, ProjectManager projectManager) {
        this.applicationProperties = applicationProperties;
        this.userManager = userManager;
        this.projectManager = projectManager;
    }

    public String getName() {
        if (null != applicationProperties) {
            return "opsgeniePluginComponent:" + applicationProperties.getDisplayName();
        }

        return "opsgeniePluginComponent";
    }
}