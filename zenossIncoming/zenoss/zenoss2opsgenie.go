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
var configParameters = map[string]string{"apiKey": API_KEY,
	"zenoss2opsgenie.logger": "warning",
	"opsgenie.api.url": "https://api.opsgenie.com",
	"zenoss2opsgenie.http.proxy.enabled": "false",
	"zenoss2opsgenie.http.proxy.port": "1111",
	"zenoss2opsgenie.http.proxy.host": "localhost",
	"zenoss2opsgenie.http.proxy.protocol": "http",
	"zenoss2opsgenie.http.proxy.username": "",
	"zenoss2opsgenie.http.proxy.password": ""}
var parameters = make(map[string]interface{})
var configPath = "/etc/opsgenie/conf/opsgenie-integration.conf"
var levels = map[string]log.Level{"info": log.Info, "debug": log.Debug, "warning": log.Warning, "error": log.Error}
var logger log.Logger
var logPrefix string
var eventState string

func main() {
	version := flag.String("v", "", "")
	parseFlags()

	logger = configureLogger()
	printConfigToLog()

	if *version != "" {
		fmt.Println("Version: 1.1")
		return
	}
	logPrefix = "[EventId: " + parameters["evid"].(string) + "]"
	if parameters["test"] == true {
		logger.Warning("Sending test alert to OpsGenie.")
	} else {
		if (strings.ToLower(eventState) == "close") {
			if logger != nil {
				logger.Info("eventState flag is set to close. Will not try to retrieve event details from zenoss")
			}
		} else {
			getEventDetailsFromZenoss()
		}
	}
	postToOpsGenie()
}

func printConfigToLog() {
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
			if l[0] == "zenoss2opsgenie.timeout" {
				TOTAL_TIME, _ = strconv.Atoi(l[1])
			}
		}
	}
	if err := scanner.Err(); err != nil {
		panic(err)
	}
}

func configureLogger() log.Logger {
	level := configParameters["zenoss2opsgenie.logger"]
	var logFilePath = parameters["logPath"].(string)

	if len(logFilePath) == 0 {
		logFilePath = "/var/log/opsgenie/zenoss2opsgenie.log"
	}

	var tmpLogger log.Logger

	file, err := os.OpenFile(logFilePath, os.O_CREATE|os.O_WRONLY|os.O_APPEND, 0666)

	if err != nil {
		fmt.Println("Could not create log file \""+logFilePath+"\", will log to \"/tmp/zenoss2opsgenie.log\" file. Error: ", err)

		fileTmp, errTmp := os.OpenFile("/tmp/zenoss2opsgenie.log", os.O_CREATE|os.O_WRONLY|os.O_APPEND, 0666)

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
	var proxyEnabled = configParameters["zenoss2opsgenie.http.proxy.enabled"]
	var proxyHost = configParameters["zenoss2opsgenie.http.proxy.host"]
	var proxyPort = configParameters["zenoss2opsgenie.http.proxy.port"]
	var scheme = configParameters["zenoss2opsgenie.http.proxy.protocol"]
	var proxyUsername = configParameters["zenoss2opsgenie.http.proxy.username"]
	var proxyPassword = configParameters["zenoss2opsgenie.http.proxy.password"]
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

func getEventDetailsFromZenoss() {
	zenossApiUrl := configParameters["zenoss.command_url"]

	data := [1]interface{}{map[string]interface{}{"evid": parameters["evid"].(string)}}
	zenossParams := map[string]interface{}{"action": "EventsRouter", "method": "detail", "data": data, "type": "rpc", "tid": 1}

	if logger != nil {
		logger.Debug("Data to be posted to Zenoss:")
		logger.Debug(zenossParams)
	}

	var buf, _ = json.Marshal(zenossParams)
	body := bytes.NewBuffer(buf)

	if logger != nil {
		logger.Warning(logPrefix + "Trying to get event details from Zenoss")
	}

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
		if err == nil {
			if resp.StatusCode == 200 {
				if logger != nil {
					logger.Info(logPrefix + "Retrieved event data from Zenoss successfully;")
					logger.Debug(logPrefix + "Response body: " + string(body[:]))
				}

				var data map[string]interface{}

				if err := json.Unmarshal(body, &data); err != nil {
					logErrorAndExit("Error occurred while unmarshalling event data: ", err)
				}
				parameters["eventData"] = data
			} else {
				logErrorAndExit("Couldn't retrieve event data from Zenoss successfully; Response code: "+strconv.Itoa(resp.StatusCode)+" Response Body: "+string(body[:]), error)
			}
		} else {
			logErrorAndExit("Couldn't read the response from", err)
		}
	} else {
		logErrorAndExit("Failed to get data from Zenoss", error)
	}
	if resp != nil {
		defer resp.Body.Close()
	}
}

func logErrorAndExit(log string, err error) {
	if logger != nil {
		logger.Error(logPrefix+log, err)
	}
	os.Exit(1)
}

func postToOpsGenie() {
	apiUrl := configParameters["opsgenie.api.url"] + "/v1/json/zenoss"
	viaMaridUrl := configParameters["viaMaridUrl"]
	target := ""

	if viaMaridUrl != "" {
		apiUrl = viaMaridUrl
		target = "Marid"
	} else {
		target = "OpsGenie"
	}

	if logger != nil {
		logger.Debug("URL: ", apiUrl)
		logger.Debug("Data to be posted:")
		logger.Debug(parameters)
	}

	var buf, _ = json.Marshal(parameters)
	for i := 1; i <= 3; i++ {
		body := bytes.NewBuffer(buf)
		request, _ := http.NewRequest("POST", apiUrl, body)
		client := getHttpClient(i)

		if logger != nil {
			logger.Debug(logPrefix+"Trying to send data to OpsGenie with timeout: ", (TOTAL_TIME/12)*2*i)
		}

		resp, error := client.Do(request)
		if error == nil {
			defer resp.Body.Close()
			body, err := ioutil.ReadAll(resp.Body)
			if err == nil {
				if resp.StatusCode == 200 {
					if logger != nil {
						logger.Debug(logPrefix + " Response code: " + strconv.Itoa(resp.StatusCode))
						logger.Debug(logPrefix + "Response: " + string(body[:]))
						logger.Info(logPrefix + "Data from Zenoss posted to " + target + " successfully")
					}
				} else {
					if logger != nil {
						logger.Error(logPrefix + "Couldn't post data from Zenoss to " + target + " successfully; Response code: " + strconv.Itoa(resp.StatusCode) + " Response Body: " + string(body[:]))
					}
				}
			} else {
				if logger != nil {
					logger.Error(logPrefix+"Couldn't read the response from "+target, err)
				}
			}
			break
		} else if i < 3 {
			if logger != nil {
				logger.Error(logPrefix+"Error occurred while sending data, will retry.", error)
			}
		} else {
			if logger != nil {
				logger.Error(logPrefix+"Failed to post data from Zenoss.", error)
			}
		}
		if resp != nil {
			defer resp.Body.Close()
		}
	}
}

func parseFlags() {
	apiKey := flag.String("apiKey", "", "Api Key")
	evid := flag.String("evid", "", "Event Id")
	responders := flag.String("responders", "", "Responders")
	tags := flag.String("tags", "", "Tags")
	state := flag.String("eventState", "", "Event State")
	configloc := flag.String("config", "", "Config File Location")
	logPath := flag.String("logPath", "", "LOGPATH")
	test := flag.Bool("test", false, "Test (boolean)")

	flag.Parse()

	args := flag.Args()
	for i := 0; i < len(args); i += 2 {
		if (len(args)%2 != 0 && i == len(args)-1) {
			parameters[args[i]] = ""
		} else {
			parameters[args[i]] = args[i+1]
		}
		fmt.Printf("%s:%s\n ", args[i], parameters[args[i]])
	}

	eventState = *state

	if *configloc != "" {
		configPath = *configloc
	}

	configFile, err := os.Open(configPath)

	if err == nil {
		readConfigFile(configFile)
	} else {
		panic(err)
	}

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

	if *test != false {
		parameters["test"] = *test
	}

	parameters["evid"] = *evid
}
