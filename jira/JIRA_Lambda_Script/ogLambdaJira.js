var http = require('http');
var https = require('https');

// Config start
var ogApiKey = '<ogApiKey>';

var jiraUsername = '<jiraUsername>';
var jiraPassword = '<jiraPassword>';

// If you're using your own installation of JIRA,
// values in the next section can be completely different.
var jiraHost = '<jiraAccount>.atlassian.net';
var jiraBasePath = '/rest/api/latest';
var jiraProtocol = 'https';
var jiraPort = 443;

var reqTimeout = 3000;

// Set your alert tag to project key mappings here.
// These values are used to determine a JIRA project
// at which an issue will be created for the new alert.
var alertTagToJiraProjectKey = [];
// The following mappings (as well as the mandatory default key) are provided as examples.
// var alertTagToJiraProjectKey = [
//     {tag: 'paymentService', key: 'PAYM'},
//     {tag: 'authenticationService', key: 'AUTH'}
// ];
var jiraDefaultProjectKey = 'DEF';
// Config end

// These values are not expected to be changed.
var ogHost = 'api.opsgenie.com';
var ogProtocol = 'https';
var ogPort = 443;

// This value is not expected to be changed.
var JIRA_ISSUE_KEY_PREFIX = 'jiraIssueKey:';

var jiraReqOpts = {
    host: jiraHost,
    port: jiraPort,
    path: undefined, // To be set.
    method: 'POST',
    headers: {
        'Content-Type': 'application/json'
    },
    agent: false,
    auth: jiraUsername + ':' + jiraPassword
};

var ogReqOpts = {
    host: ogHost,
    port: ogPort,
    path: '/v2/alerts',
    method: 'POST',
    headers: {
        'Content-Type': 'application/json',
        'Authorization': 'GenieKey ' + ogApiKey
    },
    agent: false
};

var ogHttp = ogProtocol === 'https' ? https : http;
var jiraHttp = jiraProtocol === 'https' ? https : http;

var genericSuccessFunc = function (event, context) {
    console.log('Execution completed successfully.');
    context.succeed();
};

function createJiraIssue(event, context) {
    var jiraProjectKey = determineJiraProjectKey(event);
    jiraReqOpts.path = jiraBasePath + '/issue';
    var jiraReqBody = {
        'fields': {
            'project': {
                'key': jiraProjectKey
            },
            'summary': event.alert.message,
            'description': 'Issue created for OpsGenie Alert ' + event.alert.alertId + ' from Integration ' + event.integrationId,
            'issuetype': {
                'name': 'Bug' // Make sure your JIRA project configuration(s) supports this Issue Type.
            }
        }
    };
    doApiCall(event, context, jiraHttp, jiraReqOpts, jiraReqBody, 'JIRA', 'creating issue', 201, addTagToOpsGenieAlert);
}

function addTagToOpsGenieAlert(event, context, jiraResBody) {
    var ogReqBody = {
        'tags': [JIRA_ISSUE_KEY_PREFIX + jiraResBody.key]
    };
    ogReqOpts.path = '/v2/alerts/' + event.alert.alertId + "/tags"
    doApiCall(event, context, ogHttp, ogReqOpts, ogReqBody, 'OpsGenie', 'adding issue key as tag to alert', 202, genericSuccessFunc);
}

function addCommentToJiraIssue(event, context) {
    var jiraReqBody = {
        'body': event.alert.note
    };
    doExistingJiraIssueApiCall(event, context, '/comment', jiraReqBody, 'adding comment to issue', 201);
}

function startJiraIssueProgress(event, context) {
    var jiraReqBody = {
        'transition': {
            'id': '4'
        }
    };
    doExistingJiraIssueApiCall(event, context, '/transitions', jiraReqBody, 'starting issue progress', 204);
}

function closeJiraIssue(event, context) {
    var jiraReqBody = {
        'transition': {
            'id': '2'
        }
    };
    doExistingJiraIssueApiCall(event, context, '/transitions', jiraReqBody, 'closing issue', 204);
}

function doExistingJiraIssueApiCall(event, context, jiraReqPathSuffix, jiraReqBody, happening, successCode) {
    var jiraIssueKey = extractJiraIssueKeyFromAlertTag(event);
    if (jiraIssueKey) {
        jiraReqOpts.path = jiraBasePath + '/issue/' + jiraIssueKey + jiraReqPathSuffix;
        doApiCall(event, context, jiraHttp, jiraReqOpts, jiraReqBody, 'JIRA', happening, successCode, genericSuccessFunc);
    }
    else {
        context.done(new Error('Cannot determine associated JIRA issue. Alert data lacks JIRA issue key tag.'));
    }
}

function doApiCall(event, context, httplib, reqOpts, reqBody, service, happening, successCode, onSuccess) {
    var req = httplib.request(reqOpts, function (res) {
        console.log(service + ' request in progress: ' + JSON.stringify(reqOpts));
        console.log(service + ' request body sent: ' + JSON.stringify(reqBody));
        console.log(service + ' response status: ' + res.statusCode);
        res.on('data', function (chunk) {
            console.log(service + ' response body: ' + chunk);
            if (res.statusCode === successCode) {
                onSuccess(event, context, JSON.parse(chunk));
            } else {
                context.done(new Error(service + ' ' + happening + ' failed.'));
            }
        });
    });
    req.write(JSON.stringify(reqBody));
    req.end();

    req.on('error', function (err) {
        context.done(new Error(service + ' request error: ' + err.message));
    });
    req.setTimeout(reqTimeout, function () {
        context.done(new Error(service + ' request timeout after ' + reqTimeout + ' milliseconds.'));
    });
}

function determineJiraProjectKey(event) {
    var jiraProjectKey = '';
    var tags = event.alert.tags;
    for (i = 0; i < alertTagToJiraProjectKey.length; i++) {
        if (tags.indexOf(alertTagToJiraProjectKey[i].tag) > -1) {
            jiraProjectKey = alertTagToJiraProjectKey[i].key;
            break;
        }
    }
    return jiraProjectKey || jiraDefaultProjectKey;
}

function extractJiraIssueKeyFromAlertTag(event) {
    var tags = event.alert.tags;
    var jiraIssueKey = '';
    for (i = 0; i < tags.length; i++) {
        if (tags[i].substring(0, JIRA_ISSUE_KEY_PREFIX.length) === JIRA_ISSUE_KEY_PREFIX) {
            jiraIssueKey = tags[i].substring(JIRA_ISSUE_KEY_PREFIX.length);
            break;
        }
    }
    return jiraIssueKey;
}

exports.handler = function (event, context) {
    console.log('Received event: ', event);
    if (event.action === 'Create') {
        createJiraIssue(event, context);
    } else if (event.action === 'AddNote') {
        addCommentToJiraIssue(event, context);
    } else if (event.action === 'Acknowledge') {
        startJiraIssueProgress(event, context);
    } else if (event.action === 'Close' || event.action === 'Delete') {
        closeJiraIssue(event, context);
    } else {
        context.done(new Error('Action type "' + event.action + '" not supported.'));
    }
};

