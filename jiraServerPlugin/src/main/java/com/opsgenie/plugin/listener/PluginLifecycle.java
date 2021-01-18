package com.opsgenie.plugin.listener;

import com.atlassian.event.api.EventListener;
import com.atlassian.event.api.EventPublisher;
import com.atlassian.plugin.Plugin;
import com.atlassian.plugin.event.events.PluginDisabledEvent;
import com.atlassian.plugin.event.events.PluginUninstalledEvent;
import com.atlassian.plugin.spring.scanner.annotation.component.Scanned;
import com.atlassian.plugin.spring.scanner.annotation.export.ExportAsService;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import com.opsgenie.plugin.service.OpsgeniePluginSettingsManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;

import javax.inject.Inject;
import javax.inject.Named;

@ExportAsService({PluginLifecycle.class})
@Named("pluginLifecycle")
@Scanned
public class PluginLifecycle implements InitializingBean, DisposableBean {

    @ComponentImport
    private final EventPublisher eventPublisher;

    @Inject
    private final OpsgeniePluginSettingsManager opsgeniePluginSettingsManager;

    private static final Logger logger = LoggerFactory.getLogger(PluginLifecycle.class);

    @Inject
    public PluginLifecycle(EventPublisher eventPublisher, OpsgeniePluginSettingsManager opsgeniePluginSettingsManager) {
        this.opsgeniePluginSettingsManager = opsgeniePluginSettingsManager;
        this.eventPublisher = eventPublisher;
    }

    public void afterPropertiesSet() throws Exception {
        eventPublisher.register(this);
    }

    public void destroy() {
    }

    @EventListener
    public void onPluginUninstall(PluginUninstalledEvent vent) throws Exception {
        logger.debug("Plugin Uninstalled!");
        opsgeniePluginSettingsManager.deleteSettings();
        eventPublisher.unregister(this);
    }
}