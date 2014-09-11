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
var parameters = map[string]string{}
var configParameters = map[string]string{"apiKey": API_KEY, "opsgenie.api.url" : "https://api.opsgenie.com", "logger":"warning"}
var configPath = "/etc/opsgenie/conf/opsgenie-integration.conf"
var levels = map [string]log.Level{"info":log.Info,"debug":log.Debug,"warning":log.Warning,"error":log.Error}
var logger log.Logger

func main() {
	configFile, err := os.Open(configPath)
	if err == nil{
		readConfigFile(configFile)
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
	logger.Debug("Config:")
	for k, v := range configParameters {
		logger.Debug(k +"="+v)
	}
}

func readConfigFile(file io.Reader){
	scanner := bufio.NewScanner(file)
	for scanner.Scan(){
		line := scanner.Text()

		line = strings.TrimSpace(line)
		if !strings.HasPrefix(line,"#") && line != "" {
			l := strings.Split(line,"=")
			configParameters[l[0]] = l[1]
			if l[0] == "timeout"{
				TOTAL_TIME,_ = strconv.Atoi(l[1])
			}
		}
	}
    if err := scanner.Err(); err != nil {
		panic(err)
    }
}

func configureLogger ()log.Logger{
	level := configParameters["logger"]
	var logFilePath = "/var/log/opsgenie/zabbix2opsgenie.log"
	file, err := os.OpenFile(logFilePath, os.O_CREATE|os.O_WRONLY|os.O_APPEND, 0666)
	if err != nil {
		panic(err)
	}

	return golog.New(file, levels[strings.ToLower(level)])
}

func getHttpClient (timeout int) *http.Client{
	seconds := (TOTAL_TIME/12)*2*timeout
	client := &http.Client{
		Transport: &http.Transport{
			Dial: func(netw, addr string) (net.Conn, error) {
				conn, err := net.DialTimeout(netw, addr, time.Second * time.Duration(seconds))
				if err != nil {
					logger.Error("Error occured while connecting: ",err)
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
	parameters["apiKey"] = configParameters["apiKey"]

	logger.Debug("Data to be posted:")
	logger.Debug(parameters)

    apiUrl := configParameters["opsgenie.api.url"] + "/v1/json/zabbix"
	useMarid := configParameters["useMarid"]
	target := ""
	if useMarid == "true"{
		var maridHost=""
		var maridPort=""
		if configParameters["http.server.enabled"] == "true"{
			maridHost = "http://" + configParameters["http.server.host"]
			maridPort = configParameters["http.server.port"]
		} else if configParameters["https.server.enabled"] == "true"{
			maridHost = "https://" +configParameters["https.server.host"]
			maridPort = configParameters["https.server.port"]
		} else{

			panic("Http server is not enabled for Marid")
		}

		apiUrl = maridHost + ":" + maridPort + "/script/marid2opsgenie.groovy" + "?async=true"
		target = "Marid"
	}else{
		target = "OpsGenie"
	}

	var buf, _ = json.Marshal(parameters)
	body := bytes.NewBuffer(buf)
	request, _ := http.NewRequest("POST", apiUrl, body)
	for i := 1; i <= 3; i++ {
		client := getHttpClient(i)
		logger.Info("Trying to send data to " + target + " with timeout: ", (TOTAL_TIME/12)*2*i)
		resp, error := client.Do(request)
		if error == nil {
			defer resp.Body.Close()
			body, err := ioutil.ReadAll(resp.Body)
			if err == nil{
				logger.Info("Data from Zabbix posted to " + target + " successfully; response:" + string(body[:]))
			}else{
				logger.Warning("Couldn't read the response from " + target, err)
			}
			break
		}else if i < 3 {
			logger.Warning("Error occurred while sending data, will retry.", error)
		}else {
			logger.Error("Failed to post data from Zabbix to " + target, error)
		}
		if resp != nil{
			defer resp.Body.Close()
		}
	}
}

func parseFlags()map[string]string{
	apiKey := flag.String("apiKey","","apiKey")

	triggerName := flag.String("triggerName", "", "TRIGGER.NAME")
	triggerId := flag.String("triggerId", "", "TRIGGER.ID")
	triggerStatus := flag.String("triggerStatus", "", "TRIGGER.STATUS")
	triggerSeverity := flag.String("triggerSeverity", "", "TRIGGER.SEVERITY")
	triggerDescription := flag.String("triggerDescription", "", "TRIGGER.DESCRIPTION")
	triggerUrl := flag.String("triggerUrl", "", "TRIGGER.URL")
	triggerValue := flag.String("triggerValue","","TRIGGER.VALUE")
	hostName := flag.String("hostName","","HOSTNAME")
	ipAddress := flag.String("ipAddress", "", "IPADDRESS")
	date := flag.String("date", "", "DATE")
	time := flag.String("time","","TIME")
	itemKey := flag.String("itemKey","","ITEM.KEY")
	itemValue := flag.String("itemValue", "", "ITEM.VALUE")
	eventId := flag.String ("eventId","","EVENT.ID")
	tags := flag.String ("tags","","tags")
	recipients := flag.String ("recipients","","recipients")

	flag.Parse()

	parameters["triggerName"] = *triggerName
	parameters["triggerId"] = *triggerId
	parameters["triggerStatus"] = *triggerStatus
	parameters["triggerSeverity"] = *triggerSeverity
	parameters["triggerDescription"] = *triggerDescription
	parameters["triggerUrl"] = *triggerUrl
	parameters["triggerValue"] = *triggerValue
	parameters["hostName"] = *hostName
	parameters["ipAddress"] = *ipAddress
	parameters["date"] = *date
	parameters["time"] = *time
	parameters["itemKey"] = *itemKey
	parameters["itemValue"] = *itemValue
	parameters["eventId"] = *eventId

	if *apiKey != ""{
		configParameters["apiKey"] = *apiKey
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

	return parameters
}
