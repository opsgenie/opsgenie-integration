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
)

var API_KEY = ""
var TOTAL_TIME = 60
var configParameters = map[string]string{"apiKey": API_KEY,"zenoss2opsgenie.logger":"warning","opsgenie.api.url":"https://api.opsgenie.com"}
var parameters = make(map[string]string)
var configPath = "../common/opsgenie-integration.conf.part"
var levels = map [string]log.Level{"info":log.Info,"debug":log.Debug,"warning":log.Warning,"error":log.Error}
var logger log.Logger

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
	file, err := os.OpenFile("zenoss2opsgenie.log", os.O_CREATE|os.O_WRONLY|os.O_APPEND, 0666)
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
	var logPrefix = "[EventId: " + parameters["evid"] + ", EventState: " + parameters["eventState"] + "]"

	logger.Debug("Data to be posted:")
	logger.Debug(parameters)

		apiUrl := configParameters["opsgenie.api.url"] + "/v1/json/zenoss"
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
					logger.Warning(logPrefix + "Data from Nagios posted to " + target + " successfully; response:" + string(body[:]))
				}else{
					logger.Warning(logPrefix + "Couldn't post data from Nagios to " + target + " successfully; Response code: " + strconv.Itoa(resp.StatusCode) + " Response Body: " + string(body[:]))
				}
			}else{
				logger.Warning(logPrefix + "Couldn't read the response from " + target, err)
			}
			break
		}else if i < 3 {
			logger.Warning(logPrefix + "Error occurred while sending data, will retry.", error)
		}else {
			logger.Error(logPrefix + "Failed to post data from Nagios to " + target, error)
		}
		if resp != nil{
			defer resp.Body.Close()
		}
	}
}

func parseFlags()map[string]string{
	apiKey := flag.String("apiKey","","api key")

	evid := flag.String("evid","","")
	eventState := flag.String("eventState","","")
	eventClass := flag.String("eventClass","","")
	eventClassKey := flag.String("eventClassKey","","")
	device_title := flag.String("device_title","","")
	summary := flag.String("summary","","")
	device := flag.String("device","","")
	component := flag.String("component","","")
	severity := flag.String("severity","","")
	lastTime := flag.String("lastTime","","")
	message := flag.String("message","","")

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
	parameters["eventState"] = *eventState
	parameters["eventClass"] = *eventClass
	parameters["eventClassKey"] = *eventClassKey
	parameters["device_title"] = *device_title
	parameters["summary"] = *summary
	parameters["device"] = *device
	parameters["component"] = *component
	parameters["severity"] = *severity
	parameters["lastTime"] = *lastTime
	parameters["message"] = *message

	return parameters
}





