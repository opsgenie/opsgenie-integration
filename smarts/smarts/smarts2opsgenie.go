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
	log "github.com/alexcesaro/log"
	"fmt"
	"io/ioutil"
	"runtime"
	"path/filepath"
)

var API_KEY = ""
var TOTAL_TIME = 60
var configParameters = map[string]string{"apiKey": API_KEY,"smarts2opsgenie.logger":"warning","opsgenie.api.url":"https://api.opsgenie.com"}
var parameters = make(map[string]string)
var configPath = "/etc/opsgenie/conf/opsgenie-integration.conf"
var logPath = "/var/log/opsgenie/smarts2opsgenie.log"
var levels = map [string]log.Level{"info":log.Info,"debug":log.Debug,"warning":log.Warning,"error":log.Error}
var logger log.Logger

func main() {
	if runtime.GOOS == "windows" {
		dir, _ := filepath.Abs(filepath.Dir(os.Args[0]))
		configPath = string(dir) +"/../conf/opsgenie-integration.conf"
		logPath = string(dir) + "/smarts2opsgenie.log"
		_, err := os.Stat(configPath)
		if err != nil {
			configPath = "../conf/opsgenie-integration.conf"
			logPath = "smarts2opsgenie.log"
		}
	}
	configFile, err := os.Open(configPath)
	if err == nil{
		readConfigFile(configFile)
	}else{
		panic(err)
	}
	logger = configureLogger()
	printConfigToLog()
	version := flag.String("v","","")
	getEnvVariables()
	parseFlags()
	if *version != ""{
		fmt.Println("Version: 1.0")
		return
	}
	http_post()
}

func printConfigToLog(){
	if(logger.LogDebug()){
		logger.Debug("Config:")
		for k, v := range configParameters {
			logger.Debug(k +"="+v)
		}
	}
}

func readConfigFile(file io.Reader){
	scanner := bufio.NewScanner(file)
	for scanner.Scan(){
		line := scanner.Text()

		line = strings.TrimSpace(line)
		if !strings.HasPrefix(line,"#") && line != "" {
			l := strings.Split(line,"=")
			l[0] = strings.TrimSpace(l[0])
			l[1] = strings.TrimSpace(l[1])
			configParameters[l[0]]=l[1]
			if l[0] == "smarts2opsgenie.timeout"{
				TOTAL_TIME,_ = strconv.Atoi(l[1])
			}
		}
	}
	if err := scanner.Err(); err != nil {
		panic(err)
	}
}

func configureLogger ()log.Logger{
	level := configParameters["smarts2opsgenie.logger"]
	file, err := os.OpenFile(logPath, os.O_CREATE|os.O_WRONLY|os.O_APPEND, 0666)
	if err != nil{
		fmt.Println("Logging disabled. Reason: ", err)
	}

	return golog.New(file, levels[strings.ToLower(level)])
}

func getHttpClient (timeout int) *http.Client{
	seconds := (TOTAL_TIME/12)*2*timeout
	client := &http.Client{
		Transport: &http.Transport{
			Proxy: http.ProxyFromEnvironment,
			Dial: func(netw, addr string) (net.Conn, error) {
				conn, err := net.DialTimeout(netw, addr, time.Second * time.Duration(seconds))
				if err != nil {
					logger.Error("Error occurred while connecting: ",err)
					return nil, err
				}
				conn.SetDeadline(time.Now().Add(time.Second * time.Duration(seconds)))
				return conn, nil
			},
		},
	}
	return client
}

func http_post()  {
	var logPrefix = "[NotificationName: " + parameters["notificationName"] + "]"

	logger.Debug("Data to be posted:")
	logger.Debug(parameters)

	apiUrl := configParameters["opsgenie.api.url"] + "/v1/json/smarts"
	viaMaridUrl := configParameters["viaMaridUrl"]
	target := ""

	if viaMaridUrl != ""{
		apiUrl = viaMaridUrl
		target = "Marid"
	}else{
		target = "OpsGenie"
	}

	var buf, _ = json.Marshal(parameters)
	for i := 1; i <= 3; i++ {
		body := bytes.NewBuffer(buf)
		request, _ := http.NewRequest("POST", apiUrl, body)
		client := getHttpClient(i)

		logger.Warning(logPrefix + "Trying to send data to OpsGenie with timeout: ",(TOTAL_TIME/12)*2*i)

		resp, error := client.Do(request)
		if error == nil {
			defer resp.Body.Close()
			body, err := ioutil.ReadAll(resp.Body)
			if err == nil{
				if resp.StatusCode == 200{
					logger.Warning(logPrefix + "Data from Smarts posted to " + target + " successfully; response:" + string(body[:]))
				}else{
					logger.Warning(logPrefix + "Couldn't post data from Smarts to " + target + " successfully; Response code: " + strconv.Itoa(resp.StatusCode) + " Response Body: " + string(body[:]))
				}
			}else{
				logger.Warning(logPrefix + "Couldn't read the response from " + target, err)
			}
			break
		}else if i < 3 {
			logger.Warning(logPrefix + "Error occurred while sending data, will retry.", error)
		}else {
			logger.Error(logPrefix + "Failed to post data from Smarts to " + target, error)
		}
		if resp != nil{
			defer resp.Body.Close()
		}
	}
}

func parseFlags(){
	apiKey := flag.String("apiKey","","api key")
	recipients := flag.String("recipients","","Recipients")
	tags := flag.String("tags","","Tags")
	teams := flag.String("teams","","Teams")

	flag.Parse()

	if *apiKey != ""{
		parameters["apiKey"] = *apiKey
	}else{
		parameters["apiKey"] = configParameters ["apiKey"]
	}

	if *recipients != ""{
		parameters["recipients"] = *recipients
	}else{
		parameters["recipients"] = configParameters ["recipients"]
	}

	if *teams != ""{
		parameters["teams"] = *teams
	}else{
		parameters["teams"] = configParameters ["teams"]
	}

	if *tags != ""{
		parameters["tags"] = *tags
	}else{
		parameters["tags"] = configParameters ["tags"]
	}
}

func getEnvVariables(){
	parameters["notificationName"] =  os.Getenv("SM_OBJ_Name")
	parameters["domainName"] =  os.Getenv("SM_SERVER_NAME")
	parameters["className"] = os.Getenv("SM_OBJ_ClassName")
	parameters["instanceName"] = os.Getenv("SM_OBJ_InstanceName")
	parameters["eventName"] = os.Getenv("SM_OBJ_EventName")
	parameters["classDisplayName"] = os.Getenv("SM_OBJ_ClassDisplayName")
	parameters["instanceDisplayName"] = os.Getenv("SM_OBJ_InstanceDisplayName")
	parameters["eventDisplayName"] = os.Getenv("SM_OBJ_EventDisplayName")
	parameters["elementName"] = os.Getenv("SM_OBJ_ElementName")
	parameters["elementClassName"] = os.Getenv("SM_OBJ_ElementClassName")
	parameters["sourceDomainName"] = os.Getenv("SM_OBJ_SourceDomainName")
	parameters["active"] = os.Getenv("SM_OBJ_Active")
	parameters["occurenceCount"] = os.Getenv("SM_OBJ_OccurenceCount")
	parameters["firstNotifiedAt"] = os.Getenv("SM_OBJ_FirstNotifiedAt")
	parameters["lastNotifiedAt"] = os.Getenv("SM_OBJ_LastNotifiedAt")
	parameters["lastClearedAt"] = os.Getenv("SM_OBJ_LastClearedAt")
	parameters["lastChangedAt"] = os.Getenv("SM_OBJ_LastChangedAt")
	parameters["isRoot"] = os.Getenv("SM_OBJ_IsRoot")
	parameters["acknowledged"] = os.Getenv("SM_OBJ_Acknowledged")
	parameters["clearOnAcknowledge"] = os.Getenv("SM_OBJ_ClearOnAcknowledge")
	parameters["category"] = os.Getenv("SM_OBJ_Category")
	parameters["eventText"] = os.Getenv("SM_OBJ_EventText")
	parameters["severity"] = os.Getenv("SM_OBJ_Severity")
	parameters["impact"] = os.Getenv("SM_OBJ_Impact")
	parameters["certainty"] = os.Getenv("SM_OBJ_Certainty")
	parameters["inMaintenance"] = os.Getenv("SM_OBJ_InMaintenance")
	parameters["troubleTicketId"] = os.Getenv("SM_OBJ_TroubleTicketID")
	parameters["owner"] = os.Getenv("SM_OBJ_Owner")
	parameters["userDefined1"] = os.Getenv("SM_OBJ_UserDefined1")
	parameters["userDefined2"] = os.Getenv("SM_OBJ_UserDefined2")
	parameters["userDefined3"] = os.Getenv("SM_OBJ_UserDefined3")
	parameters["userDefined4"] = os.Getenv("SM_OBJ_UserDefined4")
	parameters["userDefined5"] = os.Getenv("SM_OBJ_UserDefined5")
	parameters["userDefined6"] = os.Getenv("SM_OBJ_UserDefined6")
	parameters["userDefined7"] = os.Getenv("SM_OBJ_UserDefined7")
	parameters["userDefined8"] = os.Getenv("SM_OBJ_UserDefined8")
	parameters["userDefined9"] = os.Getenv("SM_OBJ_UserDefined9")
	parameters["userDefined10"] = os.Getenv("SM_OBJ_UserDefined10")
}






