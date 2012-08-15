import com.ifountain.opsgenie.client.marid.http.HTTPRequest;
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

    public static Map getCloudwatchMessage(Map message) throws IOException {
        String messageStr = (String) message.get("Message");
        return JSON.parse(messageStr);
    }

    public static String getRequestType(HTTPRequest request){
        return request.getHeader(MESSAGE_TYPE_HEADER_NAME);
    }
}
