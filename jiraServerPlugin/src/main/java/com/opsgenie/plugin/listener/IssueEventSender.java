package com.opsgenie.plugin.listener;

public interface IssueEventSender {

    SendResult send(String baseUrl, String apiKey, String webhookEventAsJson);

    SendMethod method();
}
