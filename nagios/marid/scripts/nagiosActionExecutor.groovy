import org.apache.http.HttpHost
import org.apache.http.conn.scheme.Scheme
import org.apache.http.conn.ssl.SSLSocketFactory
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

import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import java.security.cert.CertificateException
import java.security.cert.X509Certificate

LOG_PREFIX = "[${action}]:";
logger.warn("${LOG_PREFIX} Will execute action for alertId ${alert.alertId}");

CONF_PROPS_TO_CHECK =["host", "port", "user", "password", "http.timeout"];
HTTP_CLIENT = null;
TARGET_HOST = null;
CONF_PREFIX = "nagios.";

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
        def nagiosServer=alertFromOpsgenie.details.nagiosServer
        if (!nagiosServer || nagiosServer == "default")
        {
            CONF_PREFIX = "nagios.";
        }
        else
        {

            CONF_PREFIX = "nagios."+nagiosServer+".";
        }

        def confItemsMissing = [];
        CONF_PROPS_TO_CHECK.each {item ->
            if (!_conf(item)){
                confItemsMissing.add("${CONF_PREFIX}${item}");
            }
        }

        if(confItemsMissing.size()>0)
        {
            def errorMessage = "${LOG_PREFIX} Skipping action, Conf items ${confItemsMissing} is missing. Check your marid conf file.";
            logger.warn(errorMessage);
            throw new Exception(errorMessage);
        }

        //http client preparation
        createHttpClient();
        postToNagios(postParams);
    }
    else {
        logger.warn("${LOG_PREFIX} Alert with id [${alert.alertId}] does not exist in OpsGenie. It is probably deleted.")
    }
}
finally {
    if (HTTP_CLIENT)
    {
        HTTP_CLIENT.getConnectionManager().shutdown();
    }
}


def _conf(confKey)
{
    return conf[CONF_PREFIX+confKey]
}

def createHttpClient(){
    def timeout = _conf("http.timeout").toInteger();
    def scheme =  _conf("http.scheme")
    if(scheme == null) scheme = "http";
    def port = _conf("port").toInteger();
    def host = _conf("host");
    TARGET_HOST = new HttpHost(host, port, scheme);
    HttpParams httpClientParams = new BasicHttpParams();
    HttpConnectionParams.setConnectionTimeout(httpClientParams, timeout);
    HttpConnectionParams.setSoTimeout(httpClientParams, timeout);
    HttpConnectionParams.setTcpNoDelay(httpClientParams, true);
    HTTP_CLIENT = new DefaultHttpClient(httpClientParams);
    AuthScope scope = new AuthScope(TARGET_HOST.getHostName(), TARGET_HOST.getPort());
    HTTP_CLIENT.getCredentialsProvider().setCredentials(scope, new UsernamePasswordCredentials(_conf("user"), _conf("password")));
    if(scheme == "https"){
        SSLSocketFactory sf = createSocketFactory();
        HTTP_CLIENT.getConnectionManager().getSchemeRegistry().register(new Scheme(scheme, port, sf));
    }

}

private SSLSocketFactory createSocketFactory() throws Exception{
    SSLContext sslContext = SSLContext.getInstance("TLS");
    TrustManager tm = new X509TrustManager() {
        public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
        }

        public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
        }

        public X509Certificate[] getAcceptedIssuers() {
            return null;
        }
    };

    sslContext.init(null, [tm] as TrustManager[], null);
    return new SSLSocketFactory(sslContext, SSLSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER);
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



