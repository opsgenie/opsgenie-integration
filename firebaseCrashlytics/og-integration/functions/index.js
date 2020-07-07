'use strict';

const functions = require('firebase-functions');
const rp = require('request-promise');

function createOgIssue(payload) {
  const url = functions.config().og.webhookurl
  console.log(`Calling webhook url ${url}`);
  return rp({
    method: 'POST',
    uri: url,
    body: payload,
    json: true
  });
}

function createCommonPayload(issue) {
  const payload = {
    issueId : issue.issueId,
    issueTitle : issue.issueTitle,
    appInfo : {
      appName : issue.appInfo.appName,
      appId : issue.appInfo.appId,
      appPlatform : issue.appInfo.appPlatform,
      latestAppVersion : issue.appInfo.latestAppVersion
    }
  };
  return payload;
}

exports.createNewIssueOG = functions.crashlytics.issue().onNew(async (issue) => {
  const payload = createCommonPayload(issue);
  payload.issueType = "new";
  await createOgIssue(payload);
  console.log(`Sent new issue ${issue.issueId} details to OpsGenie`);
});

exports.createRegressedIssueOG = functions.crashlytics.issue().onRegressed(async (issue) => {
  const payload = createCommonPayload(issue);
  payload.issueType = "regressed";
  await createOgIssue(payload);
  console.log(`Sent regressed issue ${issue.issueId} details to OpsGenie`);
});

exports.createVelocityAlertOG = functions.crashlytics.issue().onVelocityAlert(async (issue) => {
  const payload = createCommonPayload(issue);
  payload.issueType = "velocity";
  payload.crashCount = issue.velocityAlert.crashes;
  payload.crashPercentage = issue.velocityAlert.crashPercentage;
  await createOgIssue(payload);
  console.log(`Created velocity issue ${issue.issueId} details in OpsGenie`);
});
