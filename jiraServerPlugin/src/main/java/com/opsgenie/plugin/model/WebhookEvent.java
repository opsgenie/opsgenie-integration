package com.opsgenie.plugin.model;

import java.util.Objects;

public class WebhookEvent {

    private String webhookEvent;

    private IssueDto issue;

    public String getWebhookEvent() {
        return webhookEvent;
    }

    public WebhookEvent setWebhookEvent(String webhookEvent) {
        this.webhookEvent = webhookEvent;
        return this;
    }

    public IssueDto getIssue() {
        return issue;
    }

    public WebhookEvent setIssue(IssueDto issue) {
        this.issue = issue;
        return this;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof WebhookEvent)) return false;
        WebhookEvent that = (WebhookEvent) o;
        return Objects.equals(getWebhookEvent(), that.getWebhookEvent()) &&
                Objects.equals(getIssue(), that.getIssue());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getWebhookEvent(), getIssue());
    }

    @Override
    public String toString() {
        return "WebhookEvent{" +
                "webhookEvent='" + webhookEvent + '\'' +
                ", issue=" + issue +
                '}';
    }

    public static class IssueDto {

        private FieldsDto fields;

        public FieldsDto getFields() {
            return fields;
        }

        public IssueDto setFields(FieldsDto fields) {
            this.fields = fields;
            return this;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof IssueDto)) return false;
            IssueDto issueDto = (IssueDto) o;
            return Objects.equals(getFields(), issueDto.getFields());
        }

        @Override
        public int hashCode() {
            return Objects.hash(getFields());
        }

        @Override
        public String toString() {
            return "IssueDto{" +
                    "fields=" + fields +
                    '}';
        }
    }

    public static class FieldsDto {

        private ProjectDto project;

        private ReporterDto reporter;

        public ProjectDto getProject() {
            return project;
        }

        public FieldsDto setProject(ProjectDto project) {
            this.project = project;
            return this;
        }

        public ReporterDto getReporter() {
            return reporter;
        }

        public FieldsDto setReporter(ReporterDto reporter) {
            this.reporter = reporter;
            return this;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof FieldsDto)) return false;
            FieldsDto fieldsDto = (FieldsDto) o;
            return Objects.equals(getProject(), fieldsDto.getProject()) &&
                    Objects.equals(getReporter(), fieldsDto.getReporter());
        }

        @Override
        public int hashCode() {
            return Objects.hash(getProject(), getReporter());
        }

        @Override
        public String toString() {
            return "FieldsDto{" +
                    "project=" + project +
                    ", reporter=" + reporter +
                    '}';
        }
    }

    public static class ProjectDto {

        private String id;

        private String key;

        private String name;

        public String getId() {
            return id;
        }

        public ProjectDto setId(String id) {
            this.id = id;
            return this;
        }

        public String getKey() {
            return key;
        }

        public ProjectDto setKey(String key) {
            this.key = key;
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
                    Objects.equals(getKey(), that.getKey()) &&
                    Objects.equals(getName(), that.getName());
        }

        @Override
        public int hashCode() {
            return Objects.hash(getId(), getKey(), getName());
        }

        @Override
        public String toString() {
            return "ProjectDto{" +
                    "id='" + id + '\'' +
                    ", key='" + key + '\'' +
                    ", name='" + name + '\'' +
                    '}';
        }
    }

    public static class ReporterDto {

        private String name;

        public String getName() {
            return name;
        }

        public ReporterDto setName(String name) {
            this.name = name;
            return this;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof ReporterDto)) return false;
            ReporterDto that = (ReporterDto) o;
            return Objects.equals(getName(), that.getName());
        }

        @Override
        public int hashCode() {
            return Objects.hash(getName());
        }

        @Override
        public String toString() {
            return "ReporterDto{" +
                    "name='" + name + '\'' +
                    '}';
        }
    }
}
