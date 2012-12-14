import org.apache.http.HttpHost
import org.apache.http.params.HttpParams
import org.apache.http.params.BasicHttpParams
import org.apache.http.params.HttpConnectionParams
import org.apache.http.impl.client.DefaultHttpClient
import org.apache.http.auth.AuthScope
import org.apache.http.auth.UsernamePasswordCredentials
import org.apache.http.client.methods.HttpPost
import org.apache.http.client.entity.UrlEncodedFormEntity
import org.apache.http.message.BasicNameValuePair
import org.apache.http.util.EntityUtils

LOG_PREFIX = "[${action}]:";
logger.warn("${LOG_PREFIX} Will execute action for alertId ${alert.alertId}");

//http client preparation
def timeout = conf["nagios.http.timeout"].toInteger();
TARGET_HOST = new HttpHost(conf["nagios.host"], conf["nagios.port"].toInteger(), "http");
HttpParams httpClientParams = new BasicHttpParams();
HttpConnectionParams.setConnectionTimeout(httpClientParams, timeout);
HttpConnectionParams.setSoTimeout(httpClientParams, timeout);
HttpConnectionParams.setTcpNoDelay(httpClientParams, true);
HTTP_CLIENT = new DefaultHttpClient(httpClientParams);
AuthScope scope = new AuthScope(TARGET_HOST.getHostName(), TARGET_HOST.getPort());
HTTP_CLIENT.getCredentialsProvider().setCredentials(scope, new UsernamePasswordCredentials(conf["nagios.user"], conf["nagios.password"]));


try {
    def alertFromOpsgenie = opsgenie.getAlert(alertId: alert.alertId)
    if (alertFromOpsgenie.size() > 0) {
        def host = alertFromOpsgenie.details.host
        def service = alertFromOpsgenie.details.service
        def postParams = ["btnSubmit": "Commit", "persistent": "on", "cmd_mod": "2", "send_notification": "off", "host": host]
        if(service) postParams.service = service;
        if(action == "Acknowledge"){
            postParams.com_data = "Acknowledged by ${alert.username} via OpsGenie"
            postParams.sticky_ack = "on"
            postParams.cmd_typ = service ? "34" : "33";
        }
        else if(action == "TakeOwnership"){
            postParams.com_data = "alert ownership taken by ${alert.username}"
            postParams.cmd_typ = service ? "3" : "1";
        }
        else if(action == "AssignOwnership"){
            postParams.com_data = "alert ownership assigned to ${alert.owner}"
            postParams.cmd_typ = service ? "3" : "1";
        }
        else if(action == "AddNote"){
            postParams.com_data = "${alert.note} by ${alert.username}"
            postParams.cmd_typ = service ? "3" : "1";
        }
        postToNagios(postParams)
    }
    else {
        logger.warn("${LOG_PREFIX} Alert with id [${alert.alertId}] does not exist in OpsGenie. It is probably deleted.")
    }
}
finally {
    HTTP_CLIENT.getConnectionManager().shutdown();
}



def postToNagios(postParams){
    logger.debug("${LOG_PREFIX} Posting to Nagios.")
    HttpPost post = new HttpPost("/nagios/cgi-bin/cmd.cgi")
    def formparams = [];
    postParams.each{key, value ->
        formparams << new BasicNameValuePair(key, value)
    }
    UrlEncodedFormEntity entity = new UrlEncodedFormEntity(formparams, "UTF-8");
    post.setEntity(entity);
    def response = HTTP_CLIENT.execute(TARGET_HOST, post);
    if(response.getStatusLine().getStatusCode() == 200){
        def resp = EntityUtils.toString(response.getEntity());
        logger.info("${LOG_PREFIX} Successfully executed at Nagios.");
        logger.debug("${LOG_PREFIX} Nagios response: ${resp}")
    }
    else{
        logger.warn("${LOG_PREFIX} Could not execute at Nagios.")
    }
}



