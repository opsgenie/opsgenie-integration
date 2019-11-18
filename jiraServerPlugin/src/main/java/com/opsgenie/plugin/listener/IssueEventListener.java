package com.opsgenie.plugin.listener;

import com.atlassian.event.api.EventListener;
import com.atlassian.event.api.EventPublisher;
import com.atlassian.jira.event.issue.IssueEvent;
import com.atlassian.jira.event.type.EventType;
import com.atlassian.jira.issue.Issue;
import com.atlassian.plugin.spring.scanner.annotation.imports.JiraImport;
import org.codehaus.jackson.map.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;

@Component
public class IssueEventListener {

    private static ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    public IssueEventListener(@JiraImport EventPublisher eventPublisher) {
        eventPublisher.register(this);
    }

    @EventListener
    public void onIssueEvent(IssueEvent issueEvent) throws IOException {
        Long eventTypeId = issueEvent.getEventTypeId();
        Issue issue = issueEvent.getIssue();
        //JsonNode jsonNode = objectMapper.valueToTree(issue);
        Map<String, Object> map = objectMapper.convertValue(issue, Map.class);
        String json = objectMapper.writeValueAsString(issue);
        //String json2 = objectMapper.writeValueAsString(jsonNode);
        if (eventTypeId.equals(EventType.ISSUE_CREATED_ID)) {
            System.out.println("Issue {} has been created at {}." + issue.getKey() + issue.getCreated());
        } else if (eventTypeId.equals(EventType.ISSUE_RESOLVED_ID)) {
            System.out.println("Issue {} has been resolved at {}." + issue.getKey() + issue.getResolutionDate());
        } else if (eventTypeId.equals(EventType.ISSUE_CLOSED_ID)) {
            System.out.println("Issue {} has been closed at {}." + issue.getKey() + issue.getUpdated());
        }
    }
}
