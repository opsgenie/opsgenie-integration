package com.opsgenie.plugin.controller;

import com.google.gson.Gson;
import com.opsgenie.plugin.listener.*;
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
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;
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
                String body = IOUtils.toString(req.getInputStream());
                String apiKey = opsgeniePluginSettingsManager.getApiKey();
                if (StringUtils.isNotBlank(apiKey)) {
                    Map eventMap = gson.fromJson(body, Map.class);
                    eventMap.put("serverId", opsgeniePluginSettingsManager.getServerId());
                    eventMap.put("fromServer", true);
                    IssueEventSender issueEventSender = issueEventSenderFactory.getEventSender(SendMethod.HTTP);
                    SendResult sendResult = issueEventSender.send(Optional.ofNullable(opsgeniePluginSettingsManager.getBaseUrl()).orElse("https://api.opsgenie.com"), apiKey, gson.toJson(eventMap));
                    logger.info(logPrefix() + buildLogMessage(sendResult));
                } else {
                    logger.info(logPrefix() + "Skipped to send webhook event since there is no apiKey configuration found! Please refer the configuration page of the plugin.");
                }
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
            message = "Webhook event could not send to Opsgenie. Reason: " + result.getFailReason();
        }
        return message + " With number of attempts: " + result.getNumberOfAttempts();
    }

    public static String buildErrorLog(Exception e) {
        String message = "[Opsgenie] An error occurred during the webhook submission. Reason: " + e.getMessage();
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

}
