package com.opsgenie.plugin.listener;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class IssueEventSenderFactory {

    private Map<SendMethod, IssueEventSender> issueEventSenderMap;

    @Autowired
    public IssueEventSenderFactory(List<IssueEventSender> issueEventSenders) {
        issueEventSenderMap = new HashMap<>();
        issueEventSenders.forEach(issueEventSender -> issueEventSenderMap.put(issueEventSender.method(), issueEventSender));
    }

    public IssueEventSender getEventSender(SendMethod method) {
        return issueEventSenderMap.get(method);
    }
}
