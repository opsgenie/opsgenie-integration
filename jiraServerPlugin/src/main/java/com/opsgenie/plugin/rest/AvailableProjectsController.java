package com.opsgenie.plugin.rest;

import com.atlassian.jira.avatar.Avatar;
import com.atlassian.jira.avatar.AvatarService;
import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.project.Project;
import com.atlassian.jira.project.ProjectManager;
import com.opsgenie.plugin.service.OpsgeniePluginSettingsManager;

import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Path("/projects")
public class AvailableProjectsController {

    @Inject
    private OpsgeniePluginSettingsManager opsgeniePluginSettingsManager;

    @Inject
    private ProjectManager projectManager;

    private final AvatarService avatarService = ComponentAccessor.getComponent(AvatarService.class);

    @GET
    @Produces({MediaType.APPLICATION_JSON})
    public Response getProjects() {
        List<ProjectResponseDto> projects = new ArrayList<>();
        opsgeniePluginSettingsManager.getSettings().ifPresent(opsgeniePluginSettings -> opsgeniePluginSettings.getSelectedProjects()
                .forEach(projectDto -> {
                    Project project = projectManager.getProjectObj(projectDto.getId());
                    if (project != null) {
                        projects.add(new ProjectResponseDto()
                                .setId(project.getId())
                                .setName(project.getName())
                                .setProjectTypeKey(project.getProjectTypeKey().getKey())
                                .setKey(project.getKey())
                                .setAvatarUrls(getAvatarUrls(project))
                                .setIssueTypes(project.getIssueTypes().stream().map(issueType -> new IssueTypeDto()
                                        .setId(issueType.getId())
                                        .setIconUrl(opsgeniePluginSettingsManager.getServerUrl() + issueType.getIconUrl())
                                        .setDescription(issueType.getDescription())
                                        .setName(issueType.getName())
                                        .setSubTask(issueType.isSubTask())).collect(Collectors.toList())));
                    }
                }));
        return Response.ok(new AvailableProjectsResponse().setProjects(projects)).build();
    }

    private Map<String, String> getAvatarUrls(Project project) {
        Map<String, String> urls = new HashMap<>();
        final String serverUrl = opsgeniePluginSettingsManager.getServerUrl();
        urls.put("16x16", serverUrl + avatarService.getProjectAvatarURL(project, Avatar.Size.SMALL).toString());
        urls.put("24x24", serverUrl + avatarService.getProjectAvatarURL(project, Avatar.Size.NORMAL).toString());
        urls.put("32x32", serverUrl + avatarService.getProjectAvatarURL(project, Avatar.Size.MEDIUM).toString());
        urls.put("48x48", serverUrl + avatarService.getProjectAvatarURL(project, Avatar.Size.LARGE).toString());
        return urls;
    }
}