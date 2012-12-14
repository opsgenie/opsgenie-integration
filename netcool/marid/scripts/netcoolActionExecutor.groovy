import groovy.sql.Sql

def LOG_PREFIX = "[${action}]:";
logger.warn("${LOG_PREFIX} Will execute action for alertId ${alert.alertId}");

def dbUrl = "jdbc:sybase:Tds:${conf['netcool.db.host']}:${conf['netcool.db.port']}/?LITERAL_PARAMS=true"
def dbDriver = conf["netcool.db.driver"]
def dbUser = conf["netcool.db.username"]
def dbPass = conf["netcool.db.password"]

def alertFromOpsGenie = opsgenie.getAlert(["alertId": alert.alertId]);
def serial = alertFromOpsGenie.details.Serial.toInteger();
Sql sql = Sql.newInstance(dbUrl, dbUser, dbPass, dbDriver)
try {
    if (action == "Acknowledge") {
        def result = sql.executeUpdate("update alerts.status set Acknowledged=1 where ServerSerial=?", [serial])
        if (result) {
            writeJournal(sql, serial, "Alert acknowledged by ${alert.username}");
            logger.warn("Successfully acknowledged event")
        }
        else {
            logger.warn("No Netcool event found with serial ${serial}")
        }
    }
    else if (action == "deacknowledge") {
        def result = sql.executeUpdate("update alerts.status set Acknowledged=0 where ServerSerial=?", [serial])
        if (result) {
            writeJournal(sql, serial, "Alert deacknowledged by ${alert.username}");
            logger.warn("Successfully deacknowledged event")
        }
        else {
            logger.warn("No Netcool event found with serial ${serial}")
        }
    }
    else if (action == "add to task list") {
        def result = sql.executeUpdate("update alerts.status set TaskList=1 where ServerSerial=?", [serial])
        if (result) {
            logger.warn("Successfully added event to task list")
        }
        else {
            logger.warn("No Netcool event found with serial ${serial}")
        }
    }
    else if (action == "remove from task list") {
        def result = sql.executeUpdate("update alerts.status set TaskList=0 where ServerSerial=?", [serial])
        if (result) {
            logger.warn("Successfully removed event from task list")
        }
        else {
            logger.warn("No Netcool event found with serial ${serial}")
        }
    }
    else {
        throw new Exception("Unknown action ${action}")
    }
}
finally {
    sql.close();
}


def writeJournal(Sql sql, serial, text) {
    int time = (int) (System.currentTimeMillis() / 1000);
    def keyField = "${serial}:0:${time}"
    def insertString = "insert into alerts.journal (Serial, KeyField, Chrono, Text1) values (${serial}, '${keyField}', getdate(), '${text}')".toString()
    sql.execute(insertString, []);
}


