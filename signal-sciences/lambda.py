import json
import requests

############ OpsGenie Configuration ##############
opsGenieAPIKey = ""
opsGenieAPIURL = "https://api.opsgenie.com"
opsGenieAlertEndPoint = "/v1/json/alert"
SIGSCI_EMAIL = ""
SIGSCI_PASSWORD = ""
corpName = ""
siteName = ""
endpoint = 'https://dashboard.signalsciences.net/api/v0'
opsGenieAlertAddNoteEndPoint = opsGenieAlertEndPoint + "/note"


def lambda_handler(event, context):
    def getAlert(alertId):
        params = {
            "apiKey": opsGenieAPIKey,
            "id": alertId
        }

        req = requests.get(opsGenieAPIURL + opsGenieAlertEndPoint, params=params)
        alert = req.json()

        return alert

    def getAlertDetails(alertId):
        alert = getAlert(alertId)

        details = alert["details"]
        return details

    # Authenticate

    auth = requests.post(
        endpoint + '/auth/login',
        data={"email": SIGSCI_EMAIL, "password": SIGSCI_PASSWORD},
        allow_redirects=False
    )

    def addNoteToTheRootAlert(responseData):
        reqData = {
            "apiKey": opsGenieAPIKey,
            "id": event["alert"]["alertId"],
            "note": responseData
        }

        req = requests.post(opsGenieAPIURL + opsGenieAlertAddNoteEndPoint, json=reqData)

        result = req.json()
        print str(result)

    cookies = auth.cookies
    location = auth.headers['Location']

    if location == '/':
        print 'Successfully authenticated to SignalSciences.'
    elif location == '/login?p=invalid':
        print 'Invalid login.'
        return {"Error": "Invalid Login."}
    else:
        print 'Unexpected error (location = {0})'.format(location)
        return {"Error": "Unexpected error (location = {0})".format(location)}

    # Get IP address to block
    alertDetails = getAlertDetails(event["alert"]["alertId"])

    # Prepare body to send the IP to add blockList
    body = {
        "source": alertDetails["IPtoBlock"],
        "note": alertDetails["IPtoBlock"] + " added to blackList."
    }
    print "PUT request is sending to " + endpoint + "/corps/" + corpName + "/sites/" + siteName + "/blacklist"
    print "Body to be sent: " + json.dumps(body)
    # Add to blockList
    headers = {'Content-type': 'application/json'}
    putData = requests.put(endpoint + '/corps/' + corpName + '/sites/' + siteName + '/blacklist', cookies=cookies,
                           data=json.dumps(body),
                           headers=headers)

    print "Status" + str(putData)
    print "Response Content: " + str(putData.json())
    resDataJson = putData.json()
    note = resDataJson["note"]
    addNoteToTheRootAlert(note)
    return {"Success": "Script completed successfully."}
