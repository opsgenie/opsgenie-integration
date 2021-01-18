package it.com.opsgenie.plugin;

import com.atlassian.plugins.osgi.test.AtlassianPluginsTestRunner;
import com.atlassian.sal.api.ApplicationProperties;
import com.opsgenie.plugin.api.OpsgeniePluginComponent;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertEquals;

@RunWith(AtlassianPluginsTestRunner.class)
public class OpsgeniePluginComponentWiredTest {
    private final ApplicationProperties applicationProperties;
    private final OpsgeniePluginComponent opsgeniePluginComponent;

    public OpsgeniePluginComponentWiredTest(ApplicationProperties applicationProperties, OpsgeniePluginComponent opsgeniePluginComponent) {
        this.applicationProperties = applicationProperties;
        this.opsgeniePluginComponent = opsgeniePluginComponent;
    }

    @Test
    public void testMyName() {
        assertEquals("names do not match!", "opsgeniePluginComponent:" + applicationProperties.getDisplayName(), opsgeniePluginComponent.getName());
    }
}