package com.opsgenie.plugin.rest;

import javax.xml.bind.annotation.XmlElement;
import java.util.Objects;

public class IssueTypeDto {

    @XmlElement
    private String id;

    @XmlElement
    private String description;

    @XmlElement
    private String name;

    @XmlElement
    private Boolean subTask;

    @XmlElement
    private String iconUrl;


    public String getId() {
        return id;
    }

    public IssueTypeDto setId(String id) {
        this.id = id;
        return this;
    }

    public String getDescription() {
        return description;
    }

    public IssueTypeDto setDescription(String description) {
        this.description = description;
        return this;
    }

    public String getName() {
        return name;
    }

    public IssueTypeDto setName(String name) {
        this.name = name;
        return this;
    }

    public Boolean getSubTask() {
        return subTask;
    }

    public IssueTypeDto setSubTask(Boolean subTask) {
        this.subTask = subTask;
        return this;
    }

    public String getIconUrl() {
        return iconUrl;
    }

    public IssueTypeDto setIconUrl(String iconUrl) {
        this.iconUrl = iconUrl;
        return this;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        IssueTypeDto that = (IssueTypeDto) o;
        return Objects.equals(id, that.id) && Objects.equals(description, that.description) && Objects.equals(name, that.name) && Objects.equals(subTask, that.subTask) && Objects.equals(iconUrl, that.iconUrl);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, description, name, subTask, iconUrl);
    }

    @Override
    public String toString() {
        return "IssueTypeDto{" +
                "id='" + id + '\'' +
                ", description='" + description + '\'' +
                ", name='" + name + '\'' +
                ", subTask=" + subTask +
                ", iconUrl='" + iconUrl + '\'' +
                '}';
    }
}
