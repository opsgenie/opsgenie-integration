package com.opsgenie.plugin.rest;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.List;
import java.util.Objects;

@XmlRootElement
@XmlAccessorType(XmlAccessType.FIELD)
public class AvailableProjectsResponse {

    @XmlElement(name = "values")
    private List<ProjectResponseDto> projects;

    @XmlElement
    private final Boolean isLast = true;

    public List<ProjectResponseDto> getProjects() {
        return projects;
    }

    public AvailableProjectsResponse setProjects(List<ProjectResponseDto> projects) {
        this.projects = projects;
        return this;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AvailableProjectsResponse that = (AvailableProjectsResponse) o;
        return Objects.equals(projects, that.projects) && Objects.equals(isLast, that.isLast);
    }

    @Override
    public int hashCode() {
        return Objects.hash(projects, isLast);
    }

    @Override
    public String toString() {
        return "AvailableProjectsResponse{" +
                "projects=" + projects +
                ", isLast=" + isLast +
                '}';
    }
}