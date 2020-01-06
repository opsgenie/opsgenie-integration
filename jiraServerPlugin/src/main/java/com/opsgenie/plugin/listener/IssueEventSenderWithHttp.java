package com.opsgenie.plugin.listener;

import com.opsgenie.plugin.service.OpsgenieClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class IssueEventSenderWithHttp implements IssueEventSender {


    private OpsgenieClient opsgenieClient;

    private static final String WEBHOOK_ENDPOINT = "/v1/jira-adapter/server/webhook";

    @Autowired
    public IssueEventSenderWithHttp(OpsgenieClient opsgenieClient) {
        this.opsgenieClient = opsgenieClient;
    }

    @Override
    public SendResult send(String baseUrl, String apiKey, String webhookEventAsJson) {
        return opsgenieClient.post(baseUrl + WEBHOOK_ENDPOINT, apiKey, webhookEventAsJson);
    }

    @Override
    public SendMethod method() {
        return SendMethod.HTTP;
    }

}