package ut.com.opsgenie.plugin;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.opsgenie.plugin.api.OpsgeniePluginComponent;
import com.opsgenie.plugin.impl.OpsgeniePluginComponentImpl;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class OpsgeniePluginComponentUnitTest {
    @Test
    public void testMyName() {
        OpsgeniePluginComponent component = new OpsgeniePluginComponentImpl(null, null, null);
        assertEquals("names do not match!", "opsgeniePluginComponent", component.getName());
    }

    @Test
    public void name() {
        Gson gson = new Gson();
        String body = "{\"customerId\":\"xfY6esv1C1\",\"type\":\"vjEoQFOWGx\",\"actionId\":\"utkZghNzyD\",\"chatType\":\"slack\",\"connection\":{\"id\":\"t1ga39yHZM\",\"name\":\"cYaaf4vvh1\",\"scopeType\":\"Gtpf6p9GId\"},\"message\":{\"providedId\":\"gAQRIyutIg\",\"messageId\":\"9QqpgzWebW\",\"channelIssuerId\":\"lYhcgLNdAQ\"},\"channel\":{\"issuerId\":\"wmHBBKmArF\",\"name\":\"yrc87bKybi\",\"active\":true},\"advice\":{\"a1\":\"v1\",\"a2\":\"v2\",\"a3\":\"v3\"},\"time\":1575133867306}";
        JsonElement jsonElement = gson.toJsonTree(body);
    }
}