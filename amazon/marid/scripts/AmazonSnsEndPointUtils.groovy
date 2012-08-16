import com.ifountain.opsgenie.client.marid.http.HTTPRequest
import com.ifountain.opsgenie.client.util.JsonUtils;
public class AmazonSnsEndPointUtils {
    public static final String MESSAGE_TYPE_SUBSCRIPTION_CONFIRMATION = "SubscriptionConfirmation";
    public static final String MESSAGE_TYPE_NOTIFICATION = "Notification";
    public static final String MESSAGE_TYPE_HEADER_NAME = "x-amz-sns-message-type";
    public static boolean confirmSubscription(Map message) throws Exception{
        String subscribeURL = (String) message.get("SubscribeURL");
        HttpURLConnection connection = (HttpURLConnection) new URL(subscribeURL).openConnection();
        connection.setConnectTimeout(30000);
        connection.setReadTimeout(30000);
        connection.connect();
        return connection.getResponseCode() == 200;
    }

    public static String getMessageStr(Map message){
        return (String) message.get("Message");
    }

    public static String getSubject(Map message){
        return (String) message.get("Subject");
    }

    public static Map getCloudwatchMessageMap(Map message) throws IOException {
        String messageStr = getMessageStr(message)
        return JsonUtils.parse(messageStr);
    }

    public static String getCloudwatchAlertDescription(Map message) throws IOException {
        Map cloudWatchMessage = getCloudwatchMessageMap(message);
        return "${cloudWatchMessage.AlarmDescription}. ${cloudWatchMessage.NewStateReason}"
    }
    public static Map getCloudwatchAlertDetails(Map message) throws IOException {
        Map cloudWatchMessage = getCloudwatchMessageMap(message);
        return [AlarmName:cloudWatchMessage.AlarmName, StateChangeTime:cloudWatchMessage.StateChangeTime,
                Region:cloudWatchMessage.Region, MetricName:cloudWatchMessage.Trigger.MetricName,
                Namespace:cloudWatchMessage.Trigger.Namespace
        ]
    }

    public static String getRequestType(HTTPRequest request){
        return request.getHeader(MESSAGE_TYPE_HEADER_NAME);
    }
}
