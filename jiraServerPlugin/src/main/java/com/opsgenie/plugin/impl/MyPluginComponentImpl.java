package com.opsgenie.plugin.impl;

import com.atlassian.plugin.spring.scanner.annotation.export.ExportAsService;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import com.atlassian.sal.api.ApplicationProperties;
import com.atlassian.sal.api.pluginsettings.PluginSettingsFactory;
import com.opsgenie.plugin.api.MyPluginComponent;
import com.opsgenie.plugin.listener.IssueEventSenderFactory;
import com.opsgenie.plugin.service.OpsgeniePluginSettingsManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.inject.Inject;
import javax.inject.Named;

@ExportAsService({MyPluginComponent.class})
@Named("myPluginComponent")
@Component
public class MyPluginComponentImpl implements MyPluginComponent {
    @ComponentImport
    private final ApplicationProperties applicationProperties;

    @Autowired
    private OpsgeniePluginSettingsManager opsgeniePluginSettingsManager;

    @Autowired
    private IssueEventSenderFactory issueEventSenderFactory;

    @ComponentImport
    private PluginSettingsFactory pluginSettingsFactory;

    @Inject
    public MyPluginComponentImpl(final ApplicationProperties applicationProperties) {
        this.applicationProperties = applicationProperties;
    }

    public String getName() {
        if (null != applicationProperties) {
            return "myComponent:" + applicationProperties.getDisplayName();
        }

        return "myComponent";
    }
}