package main

import (
	"os"
	"strings"
	"encoding/json"
	"net/http"
	"bytes"
	"github.com/alexcesaro/log/golog"
	"github.com/alexcesaro/log"
	"flag"
	"io"
	"bufio"
	"strconv"
	"crypto/tls"
	"net"
	"time"
	"io/ioutil"
)

var API_KEY = ""
var TOTAL_TIME = 60
var parameters = map[string]string{}
var configParameters = map[string]string{"apiKey": API_KEY, "opsgenie.api.url": "https://api.opsgenie.com", "logger": "warning"}
var configPath = "/etc/opsgenie/conf/opsgenie-integration.conf"
var levels = map[string]log.Level{"info": log.Info, "debug": log.Debug, "warning": log.Warning, "error": log.Error}
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

	parseFlags()

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
	level := configParameters["logger"]
	var logFilePath = "/var/log/opsgenie/oem2opsgenie.log"
	file, _ := os.OpenFile(logFilePath, os.O_CREATE|os.O_WRONLY|os.O_APPEND, 0666)

	return golog.New(file, levels[strings.ToLower(level)])
}

func getHttpClient (timeout int) *http.Client{
	seconds := (TOTAL_TIME/12)*2*timeout
	client := &http.Client{
		Transport: &http.Transport{
			TLSClientConfig: &tls.Config{InsecureSkipVerify : true},
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
	parameters["apiKey"] = configParameters["apiKey"]

	var logPrefix = "[OEM2OpsGenie] "

	logger.Debug("Data to be posted:")
	logger.Debug(parameters)

	apiUrl := configParameters["opsgenie.api.url"] + "/v1/json/oem"
	target := "OpsGenie"

	var buf, _ = json.Marshal(parameters)
	body := bytes.NewBuffer(buf)
	request, _ := http.NewRequest("POST", apiUrl, body)
	for i := 1; i <= 3; i++ {
		client := getHttpClient(i)
		logger.Warning(logPrefix + "Trying to send data to " + target + " with timeout: ", (TOTAL_TIME/12)*2*i)
		resp, error := client.Do(request)
		if error == nil {
			defer resp.Body.Close()
			body, err := ioutil.ReadAll(resp.Body)
			if err == nil{
				if resp.StatusCode == 200{
					logger.Debug(logPrefix + "Data from OEM posted to " + target + " successfully; response:" + string(body[:]))
				}else{
					logger.Warning(logPrefix + "Couldn't post data from OEM to " + target + " successfully; Response code: " + strconv.Itoa(resp.StatusCode) + " Response Body: " + string(body[:]))
				}
			}else{
				logger.Warning(logPrefix + "Couldn't read the response from " + target, err)
			}
			break
		}else if i < 3 {
			logger.Warning(logPrefix + "Error occurred while sending data, will retry.", error)
		}else {
			logger.Error(logPrefix + "Failed to post data from OEM to " + target, error)
		}
		if resp != nil{
			defer resp.Body.Close()
		}
	}
}

func parseFlags()map[string]string{
	apiKey := flag.String("apiKey","","apiKey")

	tags := flag.String ("tags","","tags")

	teams := flag.String("teams", "", "teams")

	flag.Parse()

	if *apiKey != ""{
		configParameters["apiKey"] = *apiKey
	}

	if *tags != ""{
		parameters["tags"] = *tags
	}else{
		parameters["tags"] = configParameters ["tags"]
	}

	if *teams != "" {
		parameters["teams"] = *teams
	} else {
		parameters["teams"] = configParameters["teams"]
	}

	var envVars map[string]string = make(map[string]string)

	for _, envVar := range os.Environ() {
		pair := strings.Split(envVar, "=")

		if pair[0] == "" || pair[1] == "" {
			continue
		}

		envVars[pair[0]] = pair[1]
	}

	for key, value := range envVars {
		parameters[key] = value;
	}

	return parameters
}
