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
	"crypto/tls"
)

var API_KEY = ""
var TOTAL_TIME = 60
var configParameters = map[string]string{"apiKey": API_KEY,"zenoss2opsgenie.logger":"warning","opsgenie.api.url":"https://api.opsgenie.com"}
var parameters = make(map[string]interface{})
var configPath = "/etc/opsgenie/conf/opsgenie-integration.conf"
var levels = map [string]log.Level{"info":log.Info,"debug":log.Debug,"warning":log.Warning,"error":log.Error}
var logger log.Logger
var logPrefix string
var eventState string
func main() {
	configFile, err := os.Open(configPath)
	if err == nil{
		readConfigFile(configFile)
	}else{
		panic(err)
	}
	logger = configureLogger()
	printConfigToLog()
	version := flag.String("v","","")
	parseFlags()
	if *version != ""{
		fmt.Println("Version: 1.0")
		return
	}
	logPrefix = "[EventId: " + parameters["evid"].(string)  + "]"
	if(strings.ToLower(eventState) == "close"){
		logger.Info("eventState flag is set to close. Will not try to retrieve event details from zenoss")
	}else{
		getEventDetailsFromZenoss()
	}
	postToOpsGenie()
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
			if l[0] == "zenoss2opsgenie.timeout"{
				TOTAL_TIME,_ = strconv.Atoi(l[1])
			}
		}
	}
	if err := scanner.Err(); err != nil {
		panic(err)
	}
}

func configureLogger ()log.Logger{
	level := configParameters["zenoss2opsgenie.logger"]
	file, err := os.OpenFile("/var/log/opsgenie/zenoss2opsgenie.log", os.O_CREATE|os.O_WRONLY|os.O_APPEND, 0666)
	if err != nil{
		fmt.Println("Logging disabled. Reason: ", err)
	}

	return golog.New(file, levels[strings.ToLower(level)])
}

func getHttpClient (tryNumber int) *http.Client{
	timeout := (TOTAL_TIME/12)*2*tryNumber
	client := &http.Client{
		Transport: &http.Transport{
			TLSClientConfig: &tls.Config{InsecureSkipVerify : true},
			Proxy: http.ProxyFromEnvironment,
			Dial: func(netw, addr string) (net.Conn, error) {
				conn, err := net.DialTimeout(netw, addr, time.Second * time.Duration(timeout))
				if err != nil {
					logger.Error("Error occurred while connecting: ",err)
					return nil, err
				}
				conn.SetDeadline(time.Now().Add(time.Second * time.Duration(timeout)))
				return conn, nil
			},
		},
	}
	return client
}

func getEventDetailsFromZenoss(){
	zenossApiUrl := configParameters["zenoss.command_url"]
	data := [1]interface {}{map[string]interface {}{"evid":parameters["evid"].(string)}}
	zenossParams := map[string]interface{}{"action":"EventsRouter", "method":"detail", "data": data, "type":"rpc", "tid":1}

	logger.Debug("Data to be posted to Zenoss:")
	logger.Debug(zenossParams)

	var buf, _ = json.Marshal(zenossParams)
	body := bytes.NewBuffer(buf)

	logger.Warning(logPrefix + "Trying to get event details from Zenoss")

	request, _ := http.NewRequest("POST", zenossApiUrl, body)
	request.Header.Set("Content-Type", "application/json")
	username := configParameters["zenoss.username"]
	password := configParameters["zenoss.password"]
	request.SetBasicAuth(username, password)
	client := getHttpClient(1)

	resp, error := client.Do(request)
	if error == nil {
		defer resp.Body.Close()
		body, err := ioutil.ReadAll(resp.Body)
		if err == nil{
			if resp.StatusCode == 200{
				logger.Warning(logPrefix + "Retrieved event data from Zenoss successfully;")
				logger.Debug(logPrefix + "Response body: " + string(body[:]))
				var data map[string]interface{}
				if err := json.Unmarshal(body, &data); err != nil {
					logErrorAndExit("Error occurred while unmarshalling event data: ",err)
				}
				parameters["eventData"] = data
			}else{
				logErrorAndExit("Couldn't retrieve event data from Zenoss successfully; Response code: " + strconv.Itoa(resp.StatusCode) + " Response Body: " + string(body[:]),error)
			}
		}else{
			logErrorAndExit("Couldn't read the response from", err)
		}
	}else {
		logErrorAndExit("Failed to get data from Zenoss", error)
	}
	if resp != nil{
		defer resp.Body.Close()
	}
}

func logErrorAndExit(log string, err error){
	logger.Error(logPrefix + log,err)
	os.Exit(1)
}

func postToOpsGenie() {
	logger.Debug("Data to be posted to OpsGenie:")
	logger.Debug(parameters)

	apiUrl := configParameters["opsgenie.api.url"]+ "/v1/json/zenoss"
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
					logger.Warning(logPrefix + "Data from Zenoss posted to " + target + " successfully; response:" + string(body[:]))
				}else{
					logger.Warning(logPrefix + "Couldn't post data from Zenoss to " + target + " successfully; Response code: " + strconv.Itoa(resp.StatusCode) + " Response Body: " + string(body[:]))
				}
			}else{
				logger.Warning(logPrefix + "Couldn't read the response from " + target, err)
			}
			break
		}else if i < 3 {
			logger.Warning(logPrefix + "Error occurred while sending data, will retry.", error)
		}else {
			logger.Error(logPrefix + "Failed to post data from Zenoss to " + target, error)
		}
		if resp != nil{
			defer resp.Body.Close()
		}
	}
}

func parseFlags(){
	apiKey := flag.String("apiKey","","Api Key")
	evid := flag.String("evid","","Event Id")
	recipients := flag.String("recipients","","Recipients")
	tags := flag.String("tags","","Tags")
	teams := flag.String("teams","","Teams")
	state := flag.String("eventState", "", "Event State")
	flag.Parse()

	eventState = *state

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

	if *tags != ""{
		parameters["tags"] = *tags
	}else{
		parameters["tags"] = configParameters ["tags"]
	}

	if *teams != ""{
		parameters["teams"] = *teams
	}else{
		parameters["teams"] = configParameters ["teams"]
	}

	parameters["evid"] = *evid
}





