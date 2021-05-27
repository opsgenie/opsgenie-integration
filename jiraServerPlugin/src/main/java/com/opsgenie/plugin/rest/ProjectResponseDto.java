package com.opsgenie.plugin.rest;

import javax.xml.bind.annotation.XmlElement;
import java.util.List;
import java.util.Map;

public class ProjectResponseDto {

    @XmlElement
    private Long id;

    @XmlElement
    private String key;

    @XmlElement
    private String name;

    @XmlElement
    private String projectTypeKey;

    @XmlElement
    private List<IssueTypeDto> issueTypes;

    @XmlElement
    private Map<String, String> avatarUrls;

    public Long getId() {
        return id;
    }

    public ProjectResponseDto setId(Long id) {
        this.id = id;
        return this;
    }

    public String getKey() {
        return key;
    }

    public ProjectResponseDto setKey(String key) {
        this.key = key;
        return this;
    }

    public String getName() {
        return name;
    }

    public ProjectResponseDto setName(String name) {
        this.name = name;
        return this;
    }

    public String getProjectTypeKey() {
        return projectTypeKey;
    }

    public ProjectResponseDto setProjectTypeKey(String projectTypeKey) {
        this.projectTypeKey = projectTypeKey;
        return this;
    }

    public List<IssueTypeDto> getIssueTypes() {
        return issueTypes;
    }

    public ProjectResponseDto setIssueTypes(List<IssueTypeDto> issueTypes) {
        this.issueTypes = issueTypes;
        return this;
    }

    public Map<String, String> getAvatarUrls() {
        return avatarUrls;
    }

    public ProjectResponseDto setAvatarUrls(Map<String, String> avatarUrls) {
        this.avatarUrls = avatarUrls;
        return this;
    }
}
