package main

import (
	"bytes"
	"encoding/json"
	"flag"
	"net/http"
	"net"
	"time"
	"os"
	"bufio"
	"strings"
	"io"
	"strconv"
	"github.com/alexcesaro/log/golog"
	"github.com/alexcesaro/log"
	"fmt"
	"io/ioutil"
	"crypto/tls"
	"net/url"
	"encoding/base64"
	"encoding/xml"
	"runtime"
)

var TOTAL_TIME = 60
var configParameters = map[string]string{"apiKey": "",
	"bmcFootPrints.url": "",
	"bmcFootPrints.username": "",
	"bmcFootPrints.password": "",
	"bmcFootPrints.workspaceName": "",
	"bmcFootPrints2opsgenie.logger": "warning",
	"opsgenie.api.url": "https://api.opsgenie.com",
	"bmcFootPrints2opsgenie.http.proxy.enabled": "false",
	"bmcFootPrints2opsgenie.http.proxy.port": "1111",
	"bmcFootPrints2opsgenie.http.proxy.host": "localhost",
	"bmcFootPrints2opsgenie.http.proxy.protocol": "http",
	"bmcFootPrints2opsgenie.http.proxy.username": "",
	"bmcFootPrints2opsgenie.http.proxy.password": ""}
var parameters = make(map[string]string)
var logPrefix = "[bmcFootPrints2opsgenie] "
var bmcFootPrintsWebServiceURL string

var configPath string
var levels = map[string]log.Level{"info": log.Info, "debug": log.Debug, "warning": log.Warning, "error": log.Error}
var logger log.Logger
var ogPrefix string = "[OpsGenie] "

type Definition struct {
	DefinitionId          string `xml:"_definitionId"`
	SubtypeName           string `xml:"_subtypeName"`
	DefinitionName        string `xml:"_definitionName"`
	DefinitionDescription string `xml:"_definitionDescription"`
}

type DefinitionResult struct {
	XMLName     xml.Name     `xml:"return"`
	Definitions []Definition `xml:"_definitions"`
}

type ItemValue struct {
	Value string `xml:"value"`
}

type ItemFields struct {
	FieldName  string    `xml:"fieldName"`
	FieldValue ItemValue `xml:"fieldValue"`
}

type CustomFields struct {
	XMLName    xml.Name     `xml:"_customFields"`
	ItemFields []ItemFields `xml:"itemFields"`
}

type Assignees struct {
	XMLName xml.Name `xml:"_assignees"`
	Values  []string `xml:"value"`
}

type DescriptionsDetail struct {
	Stamp    string `xml:"_stamp"`
	Data     string `xml:"_data"`
	UserName string `xml:"_userName"`
}

type AllDescriptionsList struct {
	XMLName             xml.Name             `xml:"_allDescriptionsList"`
	DescriptionsDetails []DescriptionsDetail `xml:"descriptionsDetail"`
}

type TicketDetailsResult struct {
	XMLName             xml.Name `xml:"return"`
	TicketNumber        string   `xml:"_ticketNumber"`
	Submitter           string   `xml:"_submitter"`
	CreateDate          string   `xml:"_createDate"`
	CreateTime          string   `xml:"_createTime"`
	Title               string   `xml:"_title"`
	Status              string   `xml:"_status"`
	Priority            string   `xml:"_priority"`
	Description         string   `xml:"_description"`
	DescriptionStamp    string   `xml:"_descriptionStamp"`
	AllDescriptionsText string   `xml:"_allDescriptionsText"`
	Assignees           Assignees
	CustomFields        CustomFields
	AllDescriptionsList AllDescriptionsList
}

func main() {
	if runtime.GOOS == "windows" {
		configPath = "C:\\opsgenie-integration\\conf\\opsgenie-integration.conf"
	} else {
		configPath = "/etc/opsgenie/conf/opsgenie-integration.conf"
	}

	configFile, err := os.Open(configPath)
	if err == nil {
		readConfigFile(configFile)
	} else {
		panic(err)
	}

	version := flag.String("v", "", "")
	parseFlags()

	logger = configureLogger()
	printConfigToLog()

	bmcFootPrintsWebServiceURL = reformatUrl(parameters["url"]) + "/footprints/servicedesk/externalapisoap/ExternalApiServicePort"

	if *version != "" {
		fmt.Println("OpsGenie - BMC FootPrints v12 Integration Package 1.0")
		return
	}

	if parameters["incidentNumber"] == "" && parameters["problemNumber"] == "" {
		if logger != nil {
			logger.Error("Stopping, BMC FootPrints v12 incidentNumber and problemNumber params both have no value, " +
				"please make sure your BMC FootPrints v12 Business Rules has the correct action defined.")
		}

		return
	}

	workspaceId := getWorkspaceId(parameters["workspaceName"])
	var itemDefinitionId string
	var itemId string
	var ticketType string

	if len(parameters["incidentNumber"]) > 0 {
		itemDefinitionId = getItemDefinitionId(workspaceId, "Incident")
		itemId = getItemId(itemDefinitionId, parameters["incidentNumber"])
		ticketType = "Incident"
	} else {
		itemDefinitionId = getItemDefinitionId(workspaceId, "Problem")
		itemId = getItemId(itemDefinitionId, parameters["problemNumber"])
		ticketType = "Problem"
	}

	var itemDetails TicketDetailsResult = getTicketDetails(itemDefinitionId, itemId)
	resolution := getCustomField(itemDetails.CustomFields, "Resolution")
	parameters["ticketNumber"] = itemDetails.TicketNumber
	parameters["opsgenieAlertAlias"] = getCustomField(itemDetails.CustomFields, "OpsGenie Alert Alias")

	if len(resolution) > 0 {
		parameters["action"] = "Close"
		parameters["resolution"] = resolution
		parameters["resolutionDateTime"] = getCustomField(itemDetails.CustomFields, "Resolution Date & Time")
	} else if len(itemDetails.AllDescriptionsList.DescriptionsDetails) > 1 {
		if strings.HasPrefix(itemDetails.Description, ogPrefix) {
			logger.Debug("Skipping, Incident or Problem was created from OpsGenie.")
			return
		}

		parameters["action"] = "AddNote"
		parameters["description"] = itemDetails.Description
		parameters["updatedBy"] = getCustomField(itemDetails.CustomFields, "Updated By")
	} else {
		if strings.HasPrefix(itemDetails.Description, ogPrefix) {
			logger.Debug("Skipping, Incident or Problem was created from OpsGenie.")
			return
		}

		parameters["action"] = "Create"
		parameters["submitter"] = itemDetails.Submitter
		parameters["createDate"] = itemDetails.CreateDate
		parameters["createTime"] = itemDetails.CreateTime
		parameters["title"] = itemDetails.Title
		parameters["status"] = itemDetails.Status
		parameters["priority"] = itemDetails.Priority
		parameters["description"] = itemDetails.Description
		parameters["descriptionStamp"] = itemDetails.DescriptionStamp
		parameters["allDescriptionsText"] = itemDetails.AllDescriptionsText
		parameters["assignees"] = strings.Join(itemDetails.Assignees.Values, ", ")
		parameters["impact"] = getCustomField(itemDetails.CustomFields, "Impact")
		parameters["urgency"] = getCustomField(itemDetails.CustomFields, "Urgency")
		parameters["typeOfIncident"] = getCustomField(itemDetails.CustomFields, "Type of Incident")
		parameters["source"] = getCustomField(itemDetails.CustomFields, "Source")
		parameters["preferredContactMethod"] = getCustomField(itemDetails.CustomFields, "Preferred Contact Method")
		parameters["contactFirstName"] = getCustomField(itemDetails.CustomFields, "First Name")
		parameters["contactLastName"] = getCustomField(itemDetails.CustomFields, "Last Name")
		parameters["contactEmailAddress"] = getCustomField(itemDetails.CustomFields, "Email Address")
		parameters["contactPhone"] = getCustomField(itemDetails.CustomFields, "Phone")
		parameters["category"] = getCustomField(itemDetails.CustomFields, "Category")
		parameters["subCategory"] = getCustomField(itemDetails.CustomFields, "Sub-category")
		parameters["symptom"] = getCustomField(itemDetails.CustomFields, "Symptom")
		parameters["otherSymptom"] = getCustomField(itemDetails.CustomFields, "If OTHER please specify")
		parameters["global"] = getCustomField(itemDetails.CustomFields, "Global")
		parameters["ticketType"] = ticketType
		parameters["ticketId"] = itemId
	}

	postToOpsgenie()
}

func printConfigToLog() {
	if logger != nil {
		if logger.LogDebug() {
			logger.Debug("Config:")
			for k, v := range configParameters {
				if strings.Contains(k, "password") {
					logger.Debug(k + "=*******")
				} else {
					logger.Debug(k + "=" + v)
				}
			}
		}
	}
}

func readConfigFile(file io.Reader) {
	scanner := bufio.NewScanner(file)

	for scanner.Scan() {
		line := scanner.Text()

		line = strings.TrimSpace(line)
		if !strings.HasPrefix(line, "#") && line != "" {
			l := strings.SplitN(line, "=", 2)
			l[0] = strings.TrimSpace(l[0])
			l[1] = strings.TrimSpace(l[1])
			configParameters[l[0]] = l[1]
		}
	}

	if err := scanner.Err(); err != nil {
		panic(err)
	}
}

func configureLogger() log.Logger {
	level := configParameters["bmcFootPrints2opsgenie.logger"]
	var logFilePath = parameters["logPath"]

	if len(logFilePath) == 0 {
		if runtime.GOOS == "windows" {
			logFilePath = "C:\\opsgenie-integration\\bmcFootPrints2opsgenie.log"
		} else {
			logFilePath = "/var/log/opsgenie/bmcFootPrints2opsgenie.log"
		}
	}

	var tmpLogger log.Logger

	file, err := os.OpenFile(logFilePath, os.O_CREATE|os.O_WRONLY|os.O_APPEND, 0666)

	if err != nil {
		var tmpLogFilePath string

		if runtime.GOOS == "windows" {
			tmpLogFilePath = "C:\\Windows\\Temp\\bmcFootPrints2opsgenie.log"
		} else {
			tmpLogFilePath = "/tmp/bmcFootPrints2opsgenie.log"
		}

		fmt.Println("Could not create log file \""+logFilePath+"\", will log to \""+tmpLogFilePath+"\" file. Error: ", err)
		fileTmp, errTmp := os.OpenFile(tmpLogFilePath, os.O_CREATE|os.O_WRONLY|os.O_APPEND, 0666)

		if errTmp != nil {
			fmt.Println("Logging disabled. Reason: ", errTmp)
		} else {
			tmpLogger = golog.New(fileTmp, levels[strings.ToLower(level)])
		}
	} else {
		tmpLogger = golog.New(file, levels[strings.ToLower(level)])
	}

	return tmpLogger
}

func getHttpClient(timeout int) *http.Client {
	seconds := (TOTAL_TIME / 12) * 2 * timeout
	var proxyEnabled = configParameters["bmcFootPrints2opsgenie.http.proxy.enabled"]
	var proxyHost = configParameters["bmcFootPrints2opsgenie.http.proxy.host"]
	var proxyPort = configParameters["bmcFootPrints2opsgenie.http.proxy.port"]
	var scheme = configParameters["bmcFootPrints2opsgenie.http.proxy.protocol"]
	var proxyUsername = configParameters["bmcFootPrints2opsgenie.http.proxy.username"]
	var proxyPassword = configParameters["bmcFootPrints2opsgenie.http.proxy.password"]
	proxy := http.ProxyFromEnvironment

	if proxyEnabled == "true" {

		u := new(url.URL)
		u.Scheme = scheme
		u.Host = proxyHost + ":" + proxyPort
		if len(proxyUsername) > 0 {
			u.User = url.UserPassword(proxyUsername, proxyPassword)
		}

		if logger != nil {
			logger.Debug("Formed Proxy url: ", u)
		}
		proxy = http.ProxyURL(u)
	}
	client := &http.Client{
		Transport: &http.Transport{
			TLSClientConfig: &tls.Config{InsecureSkipVerify: true},
			Proxy:           proxy,
			Dial: func(netw, addr string) (net.Conn, error) {
				conn, err := net.DialTimeout(netw, addr, time.Second*time.Duration(seconds))
				if err != nil {
					if logger != nil {
						logger.Error("Error occurred while connecting: ", err)
					}
					return nil, err
				}
				conn.SetDeadline(time.Now().Add(time.Second * time.Duration(seconds)))
				return conn, nil
			},
		},
	}
	return client
}

func postRequest(url string, data []byte, headersMap map[string]string) string {
	for i := 1; i <= 3; i++ {
		body := bytes.NewBuffer(data)
		request, _ := http.NewRequest("POST", url, body)

		if len(headersMap) > 0 {
			for key, value := range headersMap {
				request.Header.Set(key, value)
			}
		}

		headersJSON, _ := json.Marshal(headersMap)

		if logger != nil {
			logger.Debug(logPrefix + "Trying to make a POST request to the target URL: " +
				url + " with timeout: " + strconv.Itoa((TOTAL_TIME/12)*2*i) + ". Request Body: " +
				body.String() + ". Request Headers: " + string(headersJSON[:]))
		}

		client := getHttpClient(i)
		resp, error := client.Do(request)

		if error == nil {
			defer resp.Body.Close()
			body, err := ioutil.ReadAll(resp.Body)

			if err == nil {
				if resp.StatusCode == 200 {
					if logger != nil {
						logger.Debug(logPrefix + "Response code: " + strconv.Itoa(resp.StatusCode))
						logger.Debug(logPrefix + "Response: " + string(body[:]))
						logger.Info(logPrefix + "Data posted to " + url + " successfully.")
					}

					return string(body[:])
				} else {
					if logger != nil {
						logger.Error(logPrefix + "Couldn't post data to " + url + " successfully; Response code: "+
							strconv.Itoa(resp.StatusCode)+ " Response Body: "+ string(body[:]), err)
					}
				}
			} else {
				if logger != nil {
					logger.Error(logPrefix+"Couldn't read the response from "+url, err)
				}
			}
			break
		} else if i < 3 {
			if logger != nil {
				logger.Error(logPrefix+"Error occurred while sending data to "+url+", will retry.", error)
			}
		} else {
			if logger != nil {
				logger.Error(logPrefix+"Failed to post data to "+url+".", error)
			}
		}

		if resp != nil {
			defer resp.Body.Close()
		}
	}

	return ""
}

func postToOpsgenie() {
	apiUrl := configParameters["opsgenie.api.url"] + "/v1/json/bmcfootprints"
	viaMaridUrl := configParameters["viaMaridUrl"]

	if viaMaridUrl != "" {
		apiUrl = viaMaridUrl
	}

	var buf, _ = json.Marshal(parameters)

	postRequest(apiUrl, buf, nil)
}

func getWorkspaceId(workspaceName string) string {
	bodyStr := `<soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/" xmlns:ext="http://externalapi.business.footprints.numarasoftware.com/">
					<soapenv:Header/>
					<soapenv:Body>
						<ext:listContainerDefinitions>
							<listContainerDefinitionsRequest>
							</listContainerDefinitionsRequest>
						</ext:listContainerDefinitions>
					</soapenv:Body>
				</soapenv:Envelope>`
	headersMap := map[string]string{
		"Authorization": "Basic " + base64.StdEncoding.EncodeToString([]byte(parameters["username"]+":"+parameters["password"])),
	}

	logger.Debug(logPrefix + "Retrieving Workspace ID for workspace " + workspaceName + ".")

	responseString := postRequest(bmcFootPrintsWebServiceURL, []byte(bodyStr), headersMap)
	return parseWorkspaceId(responseString, workspaceName)
}

func getItemDefinitionId(workspaceId string, itemType string) string {
	bodyStr := `<soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/" xmlns:ext="http://externalapi.business.footprints.numarasoftware.com/">
					<soapenv:Header/>
					<soapenv:Body>
						<ext:listItemDefinitions>
							<listItemDefinitionsRequest>
								<_containerDefinitionId>` + workspaceId + `</_containerDefinitionId>
							</listItemDefinitionsRequest>
						</ext:listItemDefinitions>
					</soapenv:Body>
				</soapenv:Envelope>`
	headersMap := map[string]string{
		"Authorization": "Basic " + base64.StdEncoding.EncodeToString([]byte(parameters["username"]+":"+parameters["password"])),
	}

	logger.Debug(logPrefix + "Retrieving Item Definition ID with Workspace ID " + workspaceId + ".")

	responseString := postRequest(bmcFootPrintsWebServiceURL, []byte(bodyStr), headersMap)
	return parseItemDefinitionId(responseString, itemType)
}

func getItemId(itemDefinitionId string, itemNumber string) string {
	bodyStr := `<soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/" xmlns:ext="http://externalapi.business.footprints.numarasoftware.com/">
					<soapenv:Header/>
					<soapenv:Body>
						<ext:getItemId>
						<getItemIdRequest>
							<_itemDefinitionId>` + itemDefinitionId + `</_itemDefinitionId>
							<_itemNumber>` + itemNumber + `</_itemNumber>
						</getItemIdRequest>
						</ext:getItemId>
					</soapenv:Body>
				</soapenv:Envelope>`
	headersMap := map[string]string{
		"Authorization": "Basic " + base64.StdEncoding.EncodeToString([]byte(parameters["username"]+":"+parameters["password"])),
	}

	logger.Debug(logPrefix + "Retrieving Item ID with Item Definition ID " + itemDefinitionId + " and Item Number " + itemNumber + ".")

	responseString := postRequest(bmcFootPrintsWebServiceURL, []byte(bodyStr), headersMap)
	return parseItemId(responseString)
}

func getTicketDetails(itemDefinitionId string, ticketId string) TicketDetailsResult {
	bodyStr := `<soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/" xmlns:ext="http://externalapi.business.footprints.numarasoftware.com/">
   					<soapenv:Header/>
   					<soapenv:Body>
      					<ext:getTicketDetails>
         					<getItemDetailsRequest>
            					<_itemDefinitionId>` + itemDefinitionId + `</_itemDefinitionId>
            					<_itemId>` + ticketId + `</_itemId>
         					</getItemDetailsRequest>
      					</ext:getTicketDetails>
   					</soapenv:Body>
				</soapenv:Envelope>`
	headersMap := map[string]string{
		"Authorization": "Basic " + base64.StdEncoding.EncodeToString([]byte(parameters["username"]+":"+parameters["password"])),
	}

	logger.Debug(logPrefix + "Retrieving Ticket Details with Item Definition ID " + itemDefinitionId + " and Item ID " + ticketId + ".")

	responseString := postRequest(bmcFootPrintsWebServiceURL, []byte(bodyStr), headersMap)
	return parseTicketDetails(responseString)
}

func parseWorkspaceId(responseStr string, containerName string) string {
	returnTag := getInnerElements(responseStr, "return", true)
	v := DefinitionResult{}

	xml.Unmarshal([]byte(returnTag), &v)

	for _, elm := range v.Definitions {
		if elm.DefinitionName == containerName {
			return elm.DefinitionId
		}
	}

	return ""
}

func parseItemDefinitionId(responseStr string, itemName string) string {
	returnTag := getInnerElements(responseStr, "return", true)
	v := DefinitionResult{}

	xml.Unmarshal([]byte(returnTag), &v)

	for _, elm := range v.Definitions {
		if elm.DefinitionName == itemName {
			return elm.DefinitionId
		}
	}

	return ""
}

func parseItemId(responseStr string) string {
	return getInnerElements(responseStr, "return", false)
}

func parseTicketDetails(responseStr string) TicketDetailsResult {
	returnTag := getInnerElements(responseStr, "return", true)
	v := TicketDetailsResult{}

	xml.Unmarshal([]byte(returnTag), &v)

	return v
}

func getInnerElements(str string, tag string, includeMainElement bool) string {
	beginString := "<" + tag + ">"
	endString := "</" + tag + ">"

	beginIndex := strings.Index(str, beginString)
	endIndex := strings.LastIndex(str, endString)

	if beginIndex != -1 && endIndex != -1 {
		if includeMainElement {
			return str[beginIndex:endIndex+len(endString)]
		} else {
			return str[beginIndex+len(beginString):endIndex]
		}
	} else {
		return ""
	}
}

func getCustomField(customFields CustomFields, customFieldName string) string {
	for _, elm := range customFields.ItemFields {
		if elm.FieldName == customFieldName {
			return elm.FieldValue.Value
		}
	}

	return ""
}

func reformatUrl(url string) string {
	if strings.HasSuffix(url, "/") {
		return url[0:len(url)-1]
	} else {
		return url
	}
}

func parseFlags() {
	apiKey := flag.String("apiKey", "", "api key")

	incidentNumber := flag.String("incidentNumber", "", "IncidentNumber")
	problemNumber := flag.String("problemNumber", "", "ProblemNumber")

	logPath := flag.String("logPath", "", "LOGPATH")

	recipients := flag.String("recipients", "", "Recipients")
	tags := flag.String("tags", "", "Tags")
	teams := flag.String("teams", "", "Teams")
	url := flag.String("url", "", "")
	username := flag.String("username", "", "Username")
	password := flag.String("password", "", "Password")
	workspaceName := flag.String("workspaceName", "", "WorkspaceName")

	flag.Parse()

	incidentNumberStr := strings.Replace(*incidentNumber, "\"", "", -1)
	problemNumberStr := strings.Replace(*problemNumber, "\"", "", -1)

	parameters["incidentNumber"] = incidentNumberStr
	parameters["problemNumber"] = problemNumberStr

	if *apiKey != "" {
		parameters["apiKey"] = *apiKey
	} else {
		parameters["apiKey"] = configParameters ["apiKey"]
	}

	if *recipients != "" {
		parameters["recipients"] = *recipients
	} else {
		parameters["recipients"] = configParameters ["recipients"]
	}

	if *teams != "" {
		parameters["teams"] = *teams
	} else {
		parameters["teams"] = configParameters ["teams"]
	}

	if *tags != "" {
		parameters["tags"] = *tags
	} else {
		parameters["tags"] = configParameters ["tags"]
	}

	if *logPath != "" {
		parameters["logPath"] = *logPath
	} else {
		parameters["logPath"] = configParameters["logPath"]
	}

	if *url != "" {
		parameters["url"] = *url
	} else {
		parameters["url"] = configParameters["bmcFootPrints.url"]
	}

	if *username != "" {
		parameters["username"] = *username
	} else {
		parameters["username"] = configParameters["bmcFootPrints.username"]
	}

	if *password != "" {
		parameters["password"] = *password
	} else {
		parameters["password"] = configParameters["bmcFootPrints.password"]
	}

	if *workspaceName != "" {
		parameters["workspaceName"] = *workspaceName
	} else {
		parameters["workspaceName"] = configParameters["bmcFootPrints.workspaceName"]
	}

	args := flag.Args()
	for i := 0; i < len(args); i += 2 {
		if len(args)%2 != 0 && i == len(args)-1 {
			parameters[args[i]] = ""
		} else {
			parameters[args[i]] = args[i+1]
		}
	}
}
