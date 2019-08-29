package main

import (
	"bytes"
	"crypto/tls"
	"encoding/json"
	"flag"
	"fmt"
	"io/ioutil"
	"net"
	"net/http"
	"os"
	"strconv"
	"strings"
	"time"

	"github.com/alexcesaro/log"
	"github.com/alexcesaro/log/golog"
)

var TOTAL_TIME = 60
var configParameters = map[string]string{
	"logLevel": "warning",
	"logPath":  "/var/log/opsgenie/nagios2opsgenie.log",
	/*
		if you are using opsgenie from another domain e.g. eu, sandbox etc.
		you should update the line below
	*/
	"opsgenieApiUrl": "https://api.opsgenie.com",
}
var parameters = make(map[string]string)
var levels = map[string]log.Level{"info": log.Info, "debug": log.Debug, "warning": log.Warning, "error": log.Error}
var logger log.Logger

func main() {
	version := flag.String("v", "", "")
	parseFlags()
	if *version != "" {
		fmt.Println("OpsView: 1.1")
		return
	}

	logger = configureLogger()
	printConfigToLog()

	http_post()
}

func printConfigToLog() {
	if logger != nil {
		if logger.LogDebug() {
			logger.Debug("Config:")
			for k, v := range configParameters {
				logger.Debug(k + "=" + v)
			}
		}
	}
}

func configureLogger() log.Logger {
	level := configParameters["logLevel"]
	var logFilePath = configParameters["logPath"]

	var tmpLogger log.Logger

	file, err := os.OpenFile(logFilePath, os.O_CREATE|os.O_WRONLY|os.O_APPEND, 0666)

	if err != nil {
		fmt.Println("Could not create log file \""+logFilePath+"\", will log to \"/tmp/nagios2opsgenie.log\" file. Error: ", err)

		fileTmp, errTmp := os.OpenFile("/tmp/nagios2opsgenie.log", os.O_CREATE|os.O_WRONLY|os.O_APPEND, 0666)

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
	client := &http.Client{
		Transport: &http.Transport{
			TLSClientConfig: &tls.Config{InsecureSkipVerify: true},
			Proxy:           http.ProxyFromEnvironment,
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

func http_post() {
	apiUrl := configParameters["opsgenieApiUrl"] + "/v1/json/opsview"
	target := "OpsGenie"

	if logger != nil {
		logger.Debug("Data to be posted:")
		logger.Debug(parameters)
		logger.Debug("url: " + apiUrl)
	}
	var buf, _ = json.Marshal(parameters)
	logger.Debug(buf)
	for i := 1; i <= 3; i++ {
		body := bytes.NewBuffer(buf)
		request, _ := http.NewRequest("POST", apiUrl, body)
		client := getHttpClient(i)

		if logger != nil {
			logger.Debug("Trying to send data to " + target + " with timeout: " + strconv.Itoa((TOTAL_TIME/12)*2*i))
		}

		resp, error := client.Do(request)
		if error == nil {
			defer resp.Body.Close()
			body, err := ioutil.ReadAll(resp.Body)
			if err == nil {
				if resp.StatusCode == 200 {
					if logger != nil {
						logger.Debug(" Response code: " + strconv.Itoa(resp.StatusCode))
						logger.Debug("Response: " + string(body[:]))
						logger.Info("Data from Opsview posted to " + target + " successfully")
					}
				} else {
					if logger != nil {
						logger.Error("Couldn't post data from Opsview to " + target + " successfully; Response code: " + strconv.Itoa(resp.StatusCode) + " Response Body: " + string(body[:]))
					}
				}
			} else {
				if logger != nil {
					logger.Error("Couldn't read the response from "+target, err)
				}
			}
			break
		} else if i < 3 {
			if logger != nil {
				logger.Error("Error occurred while sending data, will retry.", error)
			}
		} else {
			if logger != nil {
				logger.Error("Failed to post data from Opsview to "+target, error)
			}
		}
		if resp != nil {
			defer resp.Body.Close()
		}
	}
}

func parseFlags() map[string]string {
	apiKey := flag.String("apiKey", "", "apiKey")
	tags := flag.String("tags", "", "Tags")
	responders := flag.String("responders", "", "Responders")
	logLevel := flag.String("logLevel", "", "logLevel")
	logPath := flag.String("logPath", "", "logPath")
	opsgenieApiUrl := flag.String("opsgenieApiUrl", "", "opsgenieApiUrl")

	flag.Parse()

	if *apiKey != "" {
		parameters["apiKey"] = *apiKey
	}
	if *responders != "" {
		parameters["responders"] = *responders
	}
	if *tags != "" {
		parameters["tags"] = *tags
	}
	if *logPath != "" {
		configParameters["logPath"] = *logPath
	}
	if *logLevel != "" {
		configParameters["logLevel"] = *logLevel
	}
	if *opsgenieApiUrl != "" {
		if strings.Contains(*opsgenieApiUrl, "https://") {
			configParameters["opsgenieApiUrl"] = *opsgenieApiUrl
		} else {
			configParameters["opsgenieApiUrl"] = "https://" + (*opsgenieApiUrl)
		}
	}

	for _, e := range os.Environ() {
		pair := strings.Split(e, "=")
		key := pair[0]
		if strings.Contains(key, "NAGIOS") {
			parameters[key] = pair[1]
		}
	}

	args := flag.Args()
	for i := 0; i < len(args); i += 2 {
		if len(args)%2 != 0 && i == len(args)-1 {
			parameters[args[i]] = ""
		} else {
			parameters[args[i]] = args[i+1]
		}
	}

	return parameters
}
