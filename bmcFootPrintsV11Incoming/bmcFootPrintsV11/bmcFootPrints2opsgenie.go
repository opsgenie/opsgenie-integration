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
	"encoding/xml"
	"runtime"
	"net/url"
)

var TOTAL_TIME = 60
var configParameters = map[string]string{"apiKey": "",
	"bmcFootPrints.url": "",
	"bmcFootPrints.username": "",
	"bmcFootPrints.password": "",
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

type Item struct {
	Stamp    string `xml:"stamp"`
	Data     string `xml:"data"`
	Internal int    `xml:"internal"`
}

type AllDescriptions struct {
	XMLName xml.Name `xml:"allDescriptions"`
	Items   []Item   `xml:"item"`
}

type Editors struct {
	XMLName xml.Name `xml:"editors"`
	Items   []string `xml:"item"`
}

type MostRecentEdit struct {
	XMLName xml.Name `xml:"mostrecentedit"`
	Items   []string `xml:"item"`
}

type IssueDetailsResult struct {
	XMLName             xml.Name `xml:"return"`
	Priority            int      `xml:"priority"`
	TypeOfIncident      string   `xml:"Type__bof__bIncident"`
	Department          string   `xml:"Department"`
	Resolution          string   `xml:"Resolution"`
	ContactFirstName    string   `xml:"First__bName"`
	ContactLastName     string   `xml:"Last__bName"`
	ContactEmailAddress string   `xml:"Email__bAddress"`
	ContactPhone        string   `xml:"Phone"`
	SubCategory         string   `xml:"Sub__ucategory"`
	MainCategory        string   `xml:"Main__bCategory"`
	Title               string   `xml:"title"`
	Description         string   `xml:"description"`
	SLADueDate          string   `xml:"SLA__bDue__bDate"`
	Assignees           string   `xml:"assignees"`
	Submitter           string   `xml:"submitter"`
	ServiceLevel        string   `xml:"Service__bLevel"`
	SLAResponseTime     string   `xml:"SLA__bResponse__bTime"`
	Supervisor          string   `xml:"Supervisor"`
	Status              string   `xml:"status"`
	Urgency             string   `xml:"Urgency"`
	Impact              string   `xml:"Impact"`
	ClosureCode         string   `xml:"Closure__bCode"`
	Site                string   `xml:"Site"`
	UserID              string   `xml:"User__bID"`
	DescriptionStamp    string   `xml:"description_stamp"`
	AllDescriptions     AllDescriptions
	AllDescs            string   `xml:"alldescs"`
	MRID                int      `xml:"mr"`
	Editors             Editors
	EntryDate           string   `xml:"entrydate"`
	EntryTime           string   `xml:"entrytime"`
	LastDate            string   `xml:"lastdate"`
	LastTime            string   `xml:"lasttime"`
	LastDateServer      string   `xml:"lastdateServer"`
	LastTimeServer      string   `xml:"lasttimeServer"`
	MostRecentEdit      MostRecentEdit
	OpsGenieAlertAlias  string   `xml:"OpsGenie__bAlert__bAlias"`
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

	bmcFootPrintsWebServiceURL = reformatUrl(parameters["url"]) + "/MRcgi/MRWebServices.pl"

	if *version != "" {
		fmt.Println("OpsGenie - BMC FootPrints v11 Integration Package 1.0")
		return
	}

	if parameters["incidentNumber"] == "" && parameters["problemNumber"] == "" {
		if logger != nil {
			logger.Error("Stopping, BMC FootPrints v11 incidentNumber and problemNumber params both have no value, " +
				"please make sure your BMC FootPrints v11 Escalations has the correct external action defined.")
		}

		return
	}

	if parameters["workspaceId"] == "" {
		if logger != nil {
			logger.Error("Stopping, BMC FootPrints v11 workspaceId parameter has no value, " +
				"please make sure your BMC FootPrints v11 Escalations has the correct external action defined.")
		}

		return
	}

	var issueType string
	var issueNumber string

	if len(parameters["incidentNumber"]) > 0 {
		issueType = "Incident"
		issueNumber = parameters["incidentNumber"]
	} else {
		issueType = "Problem"
		issueNumber = parameters["problemNumber"]
	}

	var issueDetails IssueDetailsResult = getIssueDetails(parameters["username"], parameters["password"], parameters["workspaceId"], issueNumber)
	parameters["issueNumber"] = strconv.Itoa(issueDetails.MRID)
	parameters["description"] = issueDetails.Description
	parameters["lastDate"] = issueDetails.LastDate
	parameters["lastTime"] = issueDetails.LastTime
	parameters["lastDateServer"] = issueDetails.LastDateServer
	parameters["lastTimeServer"] = issueDetails.LastTimeServer
	parameters["editors"] = strings.Join(issueDetails.Editors.Items, ", ")
	parameters["mostRecentEdit"] = strings.Join(issueDetails.MostRecentEdit.Items, "\n")
	parameters["opsgenieAlertAlias"] = issueDetails.OpsGenieAlertAlias

	resolution := issueDetails.Resolution

	if len(resolution) > 0 {
		parameters["action"] = "Close"
		parameters["resolution"] = resolution
		parameters["closureCode"] = issueDetails.ClosureCode
	} else if len(issueDetails.AllDescriptions.Items) > 1 {
		if strings.HasPrefix(issueDetails.Description, ogPrefix) {
			logger.Debug("Skipping, Incident or Problem was created from OpsGenie.")
			return
		}

		parameters["action"] = "AddNote"
	} else {
		if strings.HasPrefix(issueDetails.Description, ogPrefix) {
			logger.Debug("Skipping, Incident or Problem was created from OpsGenie.")
			return
		}

		parameters["action"] = "Create"
		parameters["issueType"] = issueType
		parameters["priority"] = strconv.Itoa(issueDetails.Priority)
		parameters["typeOfIncident"] = issueDetails.TypeOfIncident
		parameters["department"] = issueDetails.Department
		parameters["contactFirstName"] = issueDetails.ContactFirstName
		parameters["contactLastName"] = issueDetails.ContactLastName
		parameters["contactEmailAddress"] = issueDetails.ContactEmailAddress
		parameters["contactPhone"] = issueDetails.ContactPhone
		parameters["subCategory"] = issueDetails.SubCategory
		parameters["mainCategory"] = issueDetails.MainCategory
		parameters["title"] = issueDetails.Title
		parameters["descriptionStamp"] = issueDetails.DescriptionStamp
		parameters["assignees"] = issueDetails.Assignees
		parameters["submitter"] = issueDetails.Submitter
		parameters["serviceLevel"] = issueDetails.ServiceLevel
		parameters["slaDueDate"] = issueDetails.SLADueDate
		parameters["slaResponseTime"] = issueDetails.SLAResponseTime
		parameters["supervisor"] = issueDetails.Supervisor
		parameters["status"] = issueDetails.Status
		parameters["urgency"] = issueDetails.Urgency
		parameters["impact"] = issueDetails.Impact
		parameters["site"] = issueDetails.Site
		parameters["userId"] = issueDetails.UserID
		parameters["allDescs"] = issueDetails.AllDescs
		parameters["entryDate"] = issueDetails.EntryDate
		parameters["entryTime"] = issueDetails.EntryTime
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
	apiUrl := configParameters["opsgenie.api.url"] + "/v1/json/bmcfootprintsv11"
	viaMaridUrl := configParameters["viaMaridUrl"]

	if viaMaridUrl != "" {
		apiUrl = viaMaridUrl
	}

	var buf, _ = json.Marshal(parameters)

	postRequest(apiUrl, buf, nil)
}

func getIssueDetails(username string, password string, projectNumber string, mrid string) IssueDetailsResult {
	bodyStr := `<SOAP-ENV:Envelope
					xmlns:SOAP-ENV="http://schemas.xmlsoap.org/soap/envelope/"
					xmlns:SOAP-ENC="http://schemas.xmlsoap.org/soap/encoding/"
					xmlns:namesp2="http://xml.apache.org/xml-soap"
					xmlns:xsd="http://www.w3.org/1999/XMLSchema"
					xmlns:xsi="http://www.w3.org/1999/XMLSchema-instance">
					<SOAP-ENV:Header/>
					<SOAP-ENV:Body>
						<namesp1:MRWebServices__getIssueDetails
							xmlns:namesp1="MRWebServices">
							<user xsi:type="xsd:string">` + username + `</user>
							<password xsi:type="xsd:string">` + password + `</password>
							<extrainfo xsi:type="xsd:string"/>
							<projectnumber xsi:type="xsd:int">` + projectNumber + `</projectnumber>
							<mrid xsi:type="xsd:int">` + mrid + `</mrid>
						</namesp1:MRWebServices__getIssueDetails>
					</SOAP-ENV:Body>
				</SOAP-ENV:Envelope>`

	responseString := postRequest(bmcFootPrintsWebServiceURL, []byte(bodyStr), nil)
	return parseIssueDetails(responseString)
}

func parseIssueDetails(responseStr string) IssueDetailsResult {
	returnTag := getInnerElements(responseStr, "return", true)

	issueDetails := IssueDetailsResult{}

	xml.Unmarshal([]byte(returnTag), &issueDetails)

	return issueDetails
}

func getInnerElements(str string, tag string, includeMainElement bool) string {
	beginString := "<" + tag + ">"
	endString := "</" + tag + ">"

	beginIndex := strings.Index(str, beginString)
	endIndex := strings.LastIndex(str, endString)

	if beginIndex != -1 && endIndex != -1 {
		if includeMainElement {
			return str[beginIndex : endIndex+len(endString)]
		} else {
			return str[beginIndex+len(beginString) : endIndex]
		}
	} else {
		return ""
	}
}

func reformatUrl(url string) string {
	if strings.HasSuffix(url, "/") {
		return url[0 : len(url)-1]
	} else {
		return url
	}
}

func parseFlags() {
	apiKey := flag.String("apiKey", "", "api key")

	incidentNumber := flag.String("incidentNumber", "", "IncidentNumber")
	problemNumber := flag.String("problemNumber", "", "ProblemNumber")

	logPath := flag.String("logPath", "", "LOGPATH")

	tags := flag.String("tags", "", "Tags")
	responders := flag.String("responders", "", "Responders")
	url := flag.String("url", "", "")
	username := flag.String("username", "", "Username")
	password := flag.String("password", "", "Password")
	workspaceId := flag.String("workspaceId", "", "WorkspaceId")

	flag.Parse()

	incidentNumberStr := strings.Replace(*incidentNumber, "\"", "", -1)
	problemNumberStr := strings.Replace(*problemNumber, "\"", "", -1)

	parameters["incidentNumber"] = incidentNumberStr
	parameters["problemNumber"] = problemNumberStr
	parameters["workspaceId"] = *workspaceId

	if *apiKey != "" {
		parameters["apiKey"] = *apiKey
	} else {
		parameters["apiKey"] = configParameters ["apiKey"]
	}

	if *responders != "" {
		parameters["responders"] = *responders
	} else {
		parameters["responders"] = configParameters ["responders"]
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

	args := flag.Args()

	for i := 0; i < len(args); i += 2 {
		if len(args)%2 != 0 && i == len(args)-1 {
			parameters[args[i]] = ""
		} else {
			parameters[args[i]] = args[i+1]
		}
	}
}
