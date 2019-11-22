package com.opsgenie.plugin.controller;

import com.google.gson.Gson;
import com.opsgenie.plugin.listener.IssueEventSender;
import com.opsgenie.plugin.listener.IssueEventSenderFactory;
import com.opsgenie.plugin.listener.SendMethod;
import com.opsgenie.plugin.listener.SendResult;
import com.opsgenie.plugin.service.OpsgeniePluginSettingsManager;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.springframework.stereotype.Component;

import javax.inject.Inject;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import java.util.Optional;

@Component
public class ConfigurationServlet extends HttpServlet {

    private OpsgeniePluginSettingsManager opsgeniePluginSettingsManager;

    private IssueEventSenderFactory issueEventSenderFactory;

    private final Gson gson = new Gson();

    @Inject
    public ConfigurationServlet(OpsgeniePluginSettingsManager opsgeniePluginSettingsManager, IssueEventSenderFactory issueEventSenderFactory) {
        this.issueEventSenderFactory = issueEventSenderFactory;
        this.opsgeniePluginSettingsManager = opsgeniePluginSettingsManager;
    }

    @Override
    public void init() throws ServletException {
        super.init();
    }

    @Override
    protected final void doPost(final HttpServletRequest req, final HttpServletResponse resp) throws ServletException, IOException {
        String body = IOUtils.toString(req.getInputStream());
        String apiKey = opsgeniePluginSettingsManager.getApiKey();
        if (StringUtils.isNotBlank(apiKey)) {
            Map eventMap = gson.fromJson(body, Map.class);
            eventMap.put("serverId", opsgeniePluginSettingsManager.getServerId());
            eventMap.put("fromServer", true);
            IssueEventSender issueEventSender = issueEventSenderFactory.getEventSender(SendMethod.HTTP);
            SendResult sendResult = issueEventSender.send(Optional.ofNullable(opsgeniePluginSettingsManager.getBaseUrl()).orElse("https://api.opsgenie.com"), apiKey, gson.toJson(eventMap));
            System.out.println(sendResult);
            //add logs for result
        }
    }

}
