import com.ifountain.opsgenie.client.marid.MemoryStore
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smackx.muc.MultiUserChat

final String CONNECTION = "connection"
CONF_PREFIX = "xmpp.";
LOG_PREFIX = "[${action}]:"
logger.warn("${LOG_PREFIX} Alert: AlertId:[${alert.alertId}] Note:[${alert.note}] Source: [${source}]");

alertFromOpsgenie = opsgenie.getAlert(alertId: alert.alertId)
XMPPConnection connection = MemoryStore.lookup(CONNECTION);
if (connection == null || !connection.isConnected()) {
    connection = new XMPPConnection(_conf("hostUrl", true));
    connection.connect();
    connection.login(_conf("username", true), _conf("password", true));
    MemoryStore.store(CONNECTION, connection);
    MultiUserChat multiUserChat = new MultiUserChat(connection, _conf("room", true));
    multiUserChat.join("OpsGenie");
}
MultiUserChat multiUserChat = new MultiUserChat(connection, _conf("room", true));
String messageToSend = createMessage()
multiUserChat.sendMessage(messageToSend);

def _conf(confKey, boolean isMandatory) {
    def confVal = conf[CONF_PREFIX + confKey]
    logger.debug("confVal ${CONF_PREFIX + confKey} from file is ${confVal}");
    if (isMandatory && confVal == null) {
        def errorMessage = "${LOG_PREFIX} Skipping action, Mandatory Conf item ${CONF_PREFIX + confKey} is missing. Check your marid conf file.";
        logger.warn(errorMessage);
        throw new Exception(errorMessage);
    }
    return confVal
}

def createMessage() {
    String message = "";
    if (alertFromOpsgenie.size() > 0) {
        if (action == "Create") {
            message = "New alert: \"" + alert.message + "\"";
        } else if (action == "Acknowledge") {
            message = alert.username + " acknowledged alert: \"" + alert.message + "\"";
        } else if (action == "AddNote") {
            message = alert.username + " added note \"" + alert.note + "\" to the alert: \"" + alert.message + "\"";
        } else if (action == "Close") {
            message = alert.username + " closed alert: \"" + alert.message + "\"";
        } else {
            message = alert.username + " executed [" + action + "] action on alert: \"" + alert.message + "\"";
        }

        logger.info("${LOG_PREFIX} ${message}");
    }

    return message
}