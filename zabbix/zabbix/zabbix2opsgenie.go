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
)

var API_KEY = ""
var TOTAL_TIME = 60
var parameters = map[string]string{}
var configParameters = map[string]string{"apiKey": API_KEY,
	"opsgenie.api.url" : "https://api.opsgenie.com",
	"zabbix2opsgenie.logger":"warning",
	"zabbix2opsgenie.http.proxy.enabled" : "false",
	"zabbix2opsgenie.http.proxy.port" : "1111",
	"zabbix2opsgenie.http.proxy.host": "localhost",
	"zabbix2opsgenie.http.proxy.protocol":"http",
	"zabbix2opsgenie.http.proxy.username": "",
	"zabbix2opsgenie.http.proxy.password": "",
	"zabbix2opsgenie.severity.information": "P5",
	"zabbix2opsgenie.severity.warning": "P4",
	"zabbix2opsgenie.severity.average": "P3",
	"zabbix2opsgenie.severity.high": "P2",
	"zabbix2opsgenie.severity.disaster": "P1"}
var configPath = "/etc/opsgenie/conf/opsgenie-integration.conf"
var levels = map [string]log.Level{"info":log.Info,"debug":log.Debug,"warning":log.Warning,"error":log.Error}
var logger log.Logger

func main() {

	configFile, err := os.Open(configPath)

	if err == nil{
		readConfigFile(configFile)
	}else{
		panic(err)
	}

	version := flag.String("v","","")
	parseFlags()

	logger = configureLogger()
	printConfigToLog()

	if *version != ""{
		fmt.Println("Version: 1.1")
		return
	}

	http_post()
}

func printConfigToLog(){
	if logger != nil {
		if (logger.LogDebug()) {
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

func readConfigFile(file io.Reader){
	scanner := bufio.NewScanner(file)

	for scanner.Scan(){
		line := scanner.Text()

		line = strings.TrimSpace(line)
		if !strings.HasPrefix(line,"#") && line != "" {
			l := strings.SplitN(line,"=",2)
			l[0] = strings.TrimSpace(l[0])
			l[1] = strings.TrimSpace(l[1])
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
	level := configParameters["zabbix2opsgenie.logger"]
	var logFilePath = parameters["logPath"]

	if len(logFilePath) == 0 {
		logFilePath = "/var/log/opsgenie/zabbix2opsgenie.log"
	}

	var tmpLogger log.Logger

	file, err := os.OpenFile(logFilePath, os.O_CREATE|os.O_WRONLY|os.O_APPEND, 0666)

	if err != nil {
		fmt.Println("Could not create log file \"" + logFilePath + "\", will log to \"/tmp/zabbix2opsgenie.log\" file. Error: ", err)

		fileTmp, errTmp := os.OpenFile("/tmp/zabbix2opsgenie.log", os.O_CREATE | os.O_WRONLY | os.O_APPEND, 0666)

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

func getHttpClient (timeout int) *http.Client {
	seconds := (TOTAL_TIME / 12) * 2 * timeout
	var proxyEnabled = configParameters["zabbix2opsgenie.http.proxy.enabled"]
	var proxyHost = configParameters["zabbix2opsgenie.http.proxy.host"]
	var proxyPort = configParameters["zabbix2opsgenie.http.proxy.port"]
	var scheme = configParameters["zabbix2opsgenie.http.proxy.protocol"]
	var proxyUsername = configParameters["zabbix2opsgenie.http.proxy.username"]
	var proxyPassword = configParameters["zabbix2opsgenie.http.proxy.password"]
	proxy := http.ProxyFromEnvironment


	if proxyEnabled == "true" {

		u := new(url.URL)
		u.Scheme = scheme
		u.Host =  proxyHost + ":" + proxyPort
		if len(proxyUsername) > 0 {
			u.User = url.UserPassword(proxyUsername,proxyPassword)
		}
		if logger != nil {
			logger.Debug("Formed Proxy url: ", u)
		}
		proxy = http.ProxyURL(u)
	}
	logger.Warning("final proxy", proxy)
	client := &http.Client{
		Transport: &http.Transport{
			TLSClientConfig: &tls.Config{InsecureSkipVerify : true},
			Proxy: proxy,
			Dial: func(netw, addr string) (net.Conn, error) {
				conn, err := net.DialTimeout(netw, addr, time.Second * time.Duration(seconds))
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

func http_post()  {
	parameters["apiKey"] = configParameters["apiKey"]

	var logPrefix = "[TriggerId: " + parameters["triggerId"] + ", HostName: " + parameters["hostName"] + "]"

    	apiUrl := configParameters["opsgenie.api.url"] + "/v1/json/zabbix"
	viaMaridUrl := configParameters["viaMaridUrl"]
	target := ""

	if viaMaridUrl != ""{
		apiUrl = viaMaridUrl
		target = "Marid"
	}else{
		target = "OpsGenie"
	}

	if logger != nil {
		logger.Debug("URL: ", apiUrl)
		logger.Debug("Data to be posted:")
		logger.Debug(parameters)
	}

	var buf, _ = json.Marshal(parameters)
	body := bytes.NewBuffer(buf)
	request, _ := http.NewRequest("POST", apiUrl, body)

	for i := 1; i <= 3; i++ {
		client := getHttpClient(i)

		if logger != nil {
			logger.Debug(logPrefix + "Trying to send data to " + target + " with timeout: ", (TOTAL_TIME / 12) * 2 * i)
		}

		resp, error := client.Do(request)
		if error == nil {
			defer resp.Body.Close()
			body, err := ioutil.ReadAll(resp.Body)
			if err == nil{
				if resp.StatusCode == 200{
					if logger != nil {
						logger.Debug(logPrefix + " Response code: " + strconv.Itoa(resp.StatusCode))
						logger.Debug(logPrefix + "Response: " + string(body[:]))
						logger.Info(logPrefix +  "Data from Zabbix posted to " + target + " successfully")
					}
				}else {
					if logger != nil {
						logger.Error(logPrefix + "Couldn't post data from Zabbix to " + target + " successfully; Response code: " + strconv.Itoa(resp.StatusCode) + " Response Body: " + string(body[:]))
					}
				}
			}else{
				if logger != nil {
					logger.Error(logPrefix + "Couldn't read the response from " + target, err)
				}
			}
			break
		}else if i < 3 {
			if logger != nil {
				logger.Error(logPrefix + "Error occurred while sending data, will retry.", error)
			}
		}else {
			if logger != nil {
				logger.Error(logPrefix + "Failed to post data from Zabbix ", error)
			}
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
	triggerHostGroupName := flag.String("triggerHostGroupName", "", "TRIGGER.HOSTGROUP.NAME")
	hostName := flag.String("hostName","","HOSTNAME")
	ipAddress := flag.String("ipAddress", "", "IPADDRESS")
	date := flag.String("date", "", "DATE")
	time := flag.String("time","","TIME")
	itemKey := flag.String("itemKey","","ITEM.KEY")
	itemValue := flag.String("itemValue", "", "ITEM.VALUE")
	eventId := flag.String ("eventId","","EVENT.ID")
	recoveryEventStatus := flag.String ("recoveryEventStatus","","EVENT.RECOVERY.STATUS")
	tags := flag.String ("tags","","tags")
	responders := flag.String ("responders","","responders")
	logPath := flag.String("logPath", "", "LOGPATH")

	flag.Parse()

	parameters["triggerName"] = *triggerName
	parameters["triggerId"] = *triggerId
	parameters["triggerStatus"] = *triggerStatus
	parameters["triggerSeverity"] = *triggerSeverity
	parameters["triggerDescription"] = *triggerDescription
	parameters["triggerUrl"] = *triggerUrl
	parameters["triggerValue"] = *triggerValue
	parameters["triggerHostGroupName"] = *triggerHostGroupName
	parameters["hostName"] = *hostName
	parameters["ipAddress"] = *ipAddress
	parameters["date"] = *date
	parameters["time"] = *time
	parameters["itemKey"] = *itemKey
	parameters["itemValue"] = *itemValue
	parameters["eventId"] = *eventId
	parameters["recoveryEventStatus"] = *recoveryEventStatus
	parameters["mappedPriority"] = configParameters["zabbix2opsgenie.severity."+strings.ToLower(*triggerSeverity)]

	if *apiKey != ""{
		configParameters["apiKey"] = *apiKey
	}

	if *responders != ""{
		parameters["responders"] = *responders
	}else{
		parameters["responders"] = configParameters ["responders"]
	}

	if *tags != ""{
		parameters["tags"] = *tags
	}else{
		parameters["tags"] = configParameters ["tags"]
	}

	if *logPath != "" {
		parameters["logPath"] = *logPath
	} else {
		parameters["logPath"] = configParameters["logPath"]
	}
	args := flag.Args()
	for i := 0; i < len(args); i += 2 {
		if(len(args)%2 != 0 && i==len(args)-1){
			parameters[args[i]] = ""
		} else {
			parameters[args[i]] = args[i+1]
		}
	}

	return parameters
}
