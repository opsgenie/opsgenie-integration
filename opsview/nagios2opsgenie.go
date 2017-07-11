package main

import (
	"bytes"
	"encoding/json"
	"flag"
	"net/http"
	"net"
	"time"
	"os"
	"strings"
	"strconv"
	"fmt"
	"io/ioutil"
	"crypto/tls"
    "log"
    "log/syslog"
)

var TOTAL_TIME = 60
var configParameters = map[string]string {
    "opsgenieApiUrl" : "https://api.opsgenie.com"}
var parameters = make(map[string]string)
var logger *syslog.Writer

func main() {
	version := flag.String("v","","")
	parseFlags()
    if *version != ""{
        fmt.Println("OpsView: 1.0")
        return
    }

    logger, e := syslog.New(syslog.LOG_NOTICE, "opsgenie")
    if e == nil {
    log.SetOutput(logger)
    }
	printConfigToLog()

	http_post()
}

func printConfigToLog(){
	if logger != nil {
        logger.Debug("Config:")
        for k, v := range configParameters {
            logger.Debug(k + "=" + v)
        }
	}
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
					if logger != nil {
						logger.Err("Error occurred while connecting: " + err.Error())
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
	apiUrl := configParameters["opsgenieApiUrl"] + "/v1/json/opsview"
	target := "OpsGenie"

	var buf, _ = json.Marshal(parameters)
	for i := 1; i <= 3; i++ {
		body := bytes.NewBuffer(buf)

		if logger != nil {
            logger.Debug("Data to be posted:")
            logger.Debug(string(buf))
            logger.Debug("url: " + apiUrl)
        }

		request, _ := http.NewRequest("POST", apiUrl, body)
		client := getHttpClient(i)

		if logger != nil {
			logger.Debug("Trying to send data to " + target + " with timeout: " + strconv.Itoa((TOTAL_TIME / 12) * 2 * i))
		}

		resp, error := client.Do(request)
		if error == nil {
			defer resp.Body.Close()
			body, err := ioutil.ReadAll(resp.Body)
			if err == nil{
				if resp.StatusCode == 200{
					if logger != nil {
						logger.Debug(" Response code: " + strconv.Itoa(resp.StatusCode))
						logger.Debug("Response: " + string(body[:]))
						logger.Info("Data from Nagios posted to " + target + " successfully")
					}
				}else{
					if logger != nil {
						logger.Err("Couldn't post data from Nagios to " + target + " successfully; Response code: " + strconv.Itoa(resp.StatusCode) + " Response Body: " + string(body[:]))
					}
				}
			}else{
				if logger != nil {
					logger.Err("Couldn't read the response from " + target + " : " + err.Error())
				}
			}
			break
		}else if i < 3 {
			if logger != nil {
				logger.Err("Error occurred while sending data, will retry : " + error.Error())
			}
		}else {
			if logger != nil {
				logger.Err("Failed to post data from Nagios to " + target + " : " + error.Error())
			}
		}
		if resp != nil{
			defer resp.Body.Close()
		}
	}
}

func parseFlags()map[string]string{
	apiKey := flag.String("apiKey","","apiKey")
	tags := flag.String("tags","","Tags")
	teams := flag.String("teams","","Teams")
	logLevel := flag.String("logLevel", "", "logLevel")
	logPath := flag.String("logPath", "", "logPath")
	opsgenieApiUrl := flag.String("opsgenieApiUrl", "", "opsgenieApiUrl")

	flag.Parse()

	if *apiKey != ""{
		parameters["apiKey"] = *apiKey
	}
	if *teams != ""{
		parameters["teams"] = *teams
	}
	if *tags != ""{
		parameters["tags"] = *tags
	}
	if *logPath != "" {
        configParameters["logPath"] = *logPath
    }
	if *logLevel != "" {
		configParameters["logLevel"] = *logLevel
	}
	if *opsgenieApiUrl != "" {
        configParameters["opsgenieApiUrl"] = *opsgenieApiUrl
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
		if(len(args)%2 != 0 && i==len(args)-1){
			parameters[args[i]] = ""
		} else {
			parameters[args[i]] = args[i+1]
		}
	}

	return parameters
}