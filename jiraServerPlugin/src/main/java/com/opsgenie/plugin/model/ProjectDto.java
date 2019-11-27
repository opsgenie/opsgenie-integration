package com.opsgenie.plugin.model;

import com.atlassian.jira.project.Project;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class ProjectDto {

    private Long id;

    private String name;

    public ProjectDto() {
    }

    public ProjectDto(Long id, String name) {
        this.id = id;
        this.name = name;
    }

    public Long getId() {
        return id;
    }

    public ProjectDto setId(Long id) {
        this.id = id;
        return this;
    }

    public String getName() {
        return name;
    }

    public ProjectDto setName(String name) {
        this.name = name;
        return this;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ProjectDto)) return false;
        ProjectDto that = (ProjectDto) o;
        return Objects.equals(getId(), that.getId()) &&
                Objects.equals(getName(), that.getName());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getId(), getName());
    }

    @Override
    public String toString() {
        return "ProjectDto{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                '}';
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>(2);
        map.put("id", id);
        map.put("name", name);
        return map;
    }

    public static ProjectDto toDto(Project project) {
        return new ProjectDto(project.getId(), project.getName());
    }
}
