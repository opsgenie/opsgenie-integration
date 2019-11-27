package com.opsgenie.plugin.controller;

import com.google.gson.Gson;
import com.opsgenie.plugin.listener.*;
import com.opsgenie.plugin.model.OpsgeniePluginSettings;
import com.opsgenie.plugin.model.ProjectDto;
import com.opsgenie.plugin.model.WebhookEvent;
import com.opsgenie.plugin.service.OpsgeniePluginSettingsManager;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.inject.Inject;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.validation.ValidationException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Optional;

@Component
public class WebhookListenerServlet extends HttpServlet {

    private OpsgeniePluginSettingsManager opsgeniePluginSettingsManager;

    private IssueEventSenderFactory issueEventSenderFactory;

    private AsyncTaskExecutor asyncTaskExecutor;

    private static final Gson gson = new Gson();

    private static final Logger logger = LoggerFactory.getLogger(WebhookListenerServlet.class);

    @Inject
    public WebhookListenerServlet(OpsgeniePluginSettingsManager opsgeniePluginSettingsManager, IssueEventSenderFactory issueEventSenderFactory, AsyncTaskExecutor asyncTaskExecutor) {
        this.issueEventSenderFactory = issueEventSenderFactory;
        this.opsgeniePluginSettingsManager = opsgeniePluginSettingsManager;
        this.asyncTaskExecutor = asyncTaskExecutor;
    }

    @Override
    protected final void doPost(final HttpServletRequest req, final HttpServletResponse resp) {
        asyncTaskExecutor.execute(() -> {
            try {
                WebhookEvent webhookEvent;
                OpsgeniePluginSettings opsgeniePluginSettings = opsgeniePluginSettingsManager.getSettings()
                        .orElseThrow(() -> new ValidationException("Skipped to post webhook event. " +
                                "Reason: there is no configuration for Opsgenie Plugin."));
                String apiKey = opsgeniePluginSettings.getApiKey();
                if (StringUtils.isBlank(apiKey)) {
                    throw new ValidationException("There is no apiKey configured!");
                }
                String baseUrl = opsgeniePluginSettings.getBaseUrl();
                String body = IOUtils.toString(req.getInputStream());
                if (StringUtils.isBlank(body)) {
                    throw new ValidationException("Received empty body!");
                }
                webhookEvent = gson.fromJson(body, WebhookEvent.class);
                if (loopDetected(webhookEvent)) {
                    logger.info(logPrefix() + "Skipped to post webhook event to prevent loop.");
                    return;
                }
                if (!doesEventBelongOneOfTheTrackedProjects(webhookEvent, opsgeniePluginSettings.getSelectedProjects())) {
                    logger.debug(logPrefix() + "Skipped to post webhook event because project is not tracked.");
                    return;
                }
                IssueEventSender issueEventSender = issueEventSenderFactory.getEventSender(SendMethod.HTTP);
                SendResult sendResult = issueEventSender.send(Optional.ofNullable(baseUrl)
                        .orElse("https://api.opsgenie.com"), apiKey, body);
                logger.info(logPrefix() + buildLogMessage(sendResult));
            } catch (ValidationException e) {
                logger.info(logPrefix() + e.getMessage());
            } catch (Exception e) {
                logger.error(logPrefix() + buildErrorLog(e));
            }
        });
    }

    private String buildLogMessage(SendResult result) {
        String message;
        if (result.isSuccess()) {
            message = "Webhook event successfully sent to Opsgenie.";
        } else {
            message = "Webhook event could not post to Opsgenie. Reason: " + result.getFailReason();
        }
        return message + " With number of attempts: " + result.getNumberOfAttempts();
    }

    public static String buildErrorLog(Exception e) {
        String message = "An error occurred during the webhook submission. Reason: " + e.getClass().getSimpleName() + "_" + e.getMessage();
        String[] stackTrace = ExceptionUtils.getRootCauseStackTrace(e);
        int upperLimit = stackTrace.length < 10 ? stackTrace.length : 10;
        String trace = ArrayUtils.toString(ArrayUtils.subarray(stackTrace, 0, upperLimit));
        return message + " Trace: " + trace;
    }

    private String logPrefix() {
        DateFormat dateFormat = new SimpleDateFormat("dd/MM/yy HH:mm:ss");
        Date date = new Date();
        return "[Opsgenie][" + dateFormat.format(date) + "]";
    }

    private boolean loopDetected(WebhookEvent webhookEvent) {
        if (webhookEvent == null) {
            return false;
        }
        return StringUtils.equalsIgnoreCase("jira:issue_created", webhookEvent.getWebhookEvent())
                && isReportedByOg(webhookEvent.getIssue());
    }

    private boolean isReportedByOg(WebhookEvent.IssueDto issueDto) {
        if (issueDto == null || issueDto.getFields() == null || issueDto.getFields().getReporter() == null) {
            return false;
        }
        return StringUtils.equals("Opsgenie", issueDto.getFields().getReporter().getName());
    }

    private boolean doesEventBelongOneOfTheTrackedProjects(WebhookEvent webhookEvent, List<ProjectDto> selectedProjects) {
        if (webhookEvent.getIssue() != null && webhookEvent.getIssue().getFields() != null && webhookEvent.getIssue().getFields().getProject() != null) {
            WebhookEvent.ProjectDto projectDto = webhookEvent.getIssue().getFields().getProject();
            return selectedProjects.stream().anyMatch(project -> StringUtils.equals(projectDto.getId(), String.valueOf(project.getId())));
        }
        return false;
    }

}
