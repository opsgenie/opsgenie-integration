import com.ifountain.opsgenie.client.script.util.ScriptProxy
import com.ifountain.opsgenie.client.marid.MaridConfig

public class SmartsActionExecutor{
    static def SMARTS_USERNAME = "admin"
    static def SMARTS_PASSWORD = "changeme"
    static def SM_BROKER = "localhost:426"

    public static void execute(def logger, def alert)
    {


        def LOG_PREFIX ="[${alert.action}]:";
        logger.warn("${LOG_PREFIX} Will execute action for alertId ${alert.alertId}");

        def opsgenie = new ScriptProxy(MaridConfig.getInstance().getOpsGenieClient(), MaridConfig.getInstance().getCustomerKey());

        def alertFromOpsGenie = opsgenie.getAlert(["alertId": alert.alertId]);
        def notificationName = alertFromOpsGenie.details["NotificationName"];
        if(!notificationName)
        {
            logger.warn("${LOG_PREFIX} notificationName does not exists in alert details for alert id : ${alert.alertId}");
            throw new Exception("notificationName does not exists in alert details")
        }


        def domainName = alertFromOpsGenie.details["DomainName"];
        if(!domainName)
        {
            logger.warn("${LOG_PREFIX} domainName does not exists in alert details for alert id : ${alert.alertId}");
            throw new Exception("domainName does not exists in alert details")
        }


        logger.warn("${LOG_PREFIX} Will execute action for alert ${notificationName} on Smarts");
        def connParams = [broker: SM_BROKER, domain: domainName, password: SMARTS_PASSWORD, username: SMARTS_USERNAME];
        SmartsDatasource.execute(connParams){ds->
            if(alert.action == "acknowledge")
            {
                ds.invokeNotificationOperation(notificationName, "acknowledge", alert.username, "Acknowledged via OpsGenie");
            }
            else if(alert.action == "unacknowledge")
            {
                ds.invokeNotificationOperation(notificationName, "unacknowledge", alert.username, "Unacknowledged via OpsGenie");
            }
            else if(alert.action == "take ownership")
            {
                ds.invokeNotificationOperation(notificationName, "takeOwnership", alert.username, "TakeOwnership via OpsGenie");
            }
            else if(alert.action == "release ownership")
            {
                ds.invokeNotificationOperation(notificationName, "releaseOwnership", alert.username, "ReleaseOwnership via OpsGenie");
            }
            else
            {
                throw new Exception("Unknown action ${alert.action}")
            }
        }
        logger.warn("${LOG_PREFIX} Executed action for alert ${notificationName} on Smarts");
    }
}