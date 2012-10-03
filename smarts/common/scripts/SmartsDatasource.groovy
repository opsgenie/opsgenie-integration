import com.smarts.remote.SmRemoteBroker
import com.smarts.remote.SmRemoteDomainManager
import com.smarts.remote.SmRemoteServerInfo
import com.smarts.repos.MR_PropertyNameValue
import com.smarts.repos.MR_Ref
import com.smarts.repos.MR_Choice
import org.apache.commons.codec.binary.Hex
import com.smarts.repos.MR_AnyVal
import com.smarts.repos.MR_AnyValString;
import com.smarts.repos.MR_AnyValUnsignedInt;


public class SmartsDatasource {
    final static String NOTIFICATION_CLASS_NAME = "ICS_Notification";
    public static final String NON_SECURE_BROKER_USERNAME = "BrokerNonsecure";
    public static final String NON_SECURE_BROKER_PASSWORD = "Nonsecure"
    protected SmRemoteDomainManager domainManager = new SmRemoteDomainManager();
    private SmRemoteBroker smBroker;
    public void connect(Map params) throws Exception {
        smBroker = new SmRemoteBroker(params.broker);
        def brokerUsername = params.brokerUsername?params.brokerUsername:NON_SECURE_BROKER_USERNAME
        def brokerPassword = params.brokerPassword?params.brokerPassword:NON_SECURE_BROKER_PASSWORD
        smBroker.attach(brokerUsername, brokerPassword);
        try {
            SmRemoteServerInfo serverInfo = smBroker.getServerInfo(params.domain);
            def timeout = 30;
            if(params.timeout){
                timeout = (int)(params.timeout/1000);
                if (timeout == 0){
                    timeout = 1;
                }
            }
            domainManager.attach(params.domain, params.username, params.password, serverInfo.getHostIPAddress(), serverInfo.getPort(), timeout);
        }
        finally {
            smBroker.detach();
        }
    }
    public void disconnect() {
        if (domainManager != null) {
            domainManager.detach();
        }
    }

    public Map getAttributes(String className, String instanceName){
        return convertToMap(domainManager.getAllProperties(className, instanceName, MR_PropertyNameValue.MR_ATTRS_ONLY));
    }
    public Map getRelations(String className, String instanceName){
        return convertToMap(domainManager.getAllProperties(className, instanceName, MR_PropertyNameValue.MR_RELATIONS_ONLY))
    }
    public Map getAllProperties(String className, String instanceName){
        return convertToMap(domainManager.getAllProperties(className, instanceName, MR_PropertyNameValue.MR_BOTH))
    }

    private void invokeNotificationOperation(String notificationName, String operationName, String userName, String auditTrailText)
    {
        MR_AnyVal[] opParams = [];
        if(operationName == "addAuditEntry")
        {
            opParams = [new MR_AnyValString(userName), new MR_AnyValString("AddNote"), new MR_AnyValString(auditTrailText)];
        }
        else
        {
            opParams = [new MR_AnyValString(userName), new MR_AnyValString(auditTrailText)];
        }
        domainManager.invokeOperation("ICS_Notification", notificationName, operationName, opParams)

        MR_AnyVal[] notifyArgs = [new MR_AnyValUnsignedInt(0)]; //wait for notify
        domainManager.invokeOperation("ICS_Notification", notificationName, "changed", notifyArgs);
    }

    public List<Map> getNotifications(String className, String instanceName, String eventName){
        def notifications = [];
        String notificationName = constructNotificationName(className, instanceName, eventName);
        MR_Ref[] refs = domainManager.findInstances(NOTIFICATION_CLASS_NAME, notificationName, MR_Choice.NONE);
        refs.each {ref->
            notifications << getAllProperties(ref.getClassName(), ref.getInstanceName());
        }
        return notifications;
    }

    private Map convertToMap(MR_PropertyNameValue[] nameValuePairs){
        Map res = new HashMap();
        nameValuePairs.each {MR_PropertyNameValue nameValue->
            def value = nameValue.getPropertyValue().getValue();
            if(value instanceof MR_Ref[]){
                def refs = [];
                value.each{MR_Ref ref->
                    refs << [creationClassName:ref.getClassName(), instanceName:ref.getInstanceName()]
                }
                value = refs;
            }
            if(value instanceof MR_Ref){
                value = [creationClassName:value.getClassName(), instanceName:value.getInstanceName()]
            }
            res.put(nameValue.getPropertyName(), value)

        }
        return res;
    }

    public static Object execute(Map connectionParams, Closure cl){
        SmartsDatasource ds = new SmartsDatasource();
        ds.connect(connectionParams);
        try{
            return cl(ds);
        }
        finally {
            ds.disconnect();
        }
    }

    public static String constructNotificationName(String className, String instanceName, String eventName) {
        StringBuffer sBuffer = new StringBuffer("NOTIFICATION-");
        sBuffer.append(replaceSpacesAndUnderscores(className));
        sBuffer.append("_");
        sBuffer.append(replaceSpacesAndUnderscores(instanceName));
        sBuffer.append("_");
        sBuffer.append(replaceSpacesAndUnderscores(eventName));
        return sBuffer.toString();
    }

    private static String replaceSpacesAndUnderscores(String stringToBeReplaced) {
        StringBuffer buf = new StringBuffer();
        for(int x=0;x<stringToBeReplaced.length();x++)
        {
            char c=stringToBeReplaced.charAt(x);
            if(c>0 && c<32)
            {
                byte [] b = {(byte)c};
                buf.append('_'+Hex.encodeHexString(b).toUpperCase());
            }
            else if(c == 32 ) //space
            {
                buf.append("_20");
            }
            else if(c == 58 || c== 95) //: or _
            {
                buf.append("_"+c);
            }
            else
            {
                buf.append(c);
            }
        }
        return buf.toString();
    }
}