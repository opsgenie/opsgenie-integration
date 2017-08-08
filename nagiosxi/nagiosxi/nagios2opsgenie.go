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

var NAGIOS_SERVER = "default"
var API_KEY = ""
var TOTAL_TIME = 60
var configParameters = map[string]string{"apiKey": API_KEY,
	"nagios_server": NAGIOS_SERVER,
	"nagios2opsgenie.logger":"warning",
	"opsgenie.api.url":"https://api.opsgenie.com" ,
	"nagios2opsgenie.http.proxy.enabled" : "false",
	"nagios2opsgenie.http.proxy.port" : "1111",
	"nagios2opsgenie.http.proxy.host": "localhost",
	"nagios2opsgenie.http.proxy.protocol":"http",
	"nagios2opsgenie.http.proxy.username": "",
	"nagios2opsgenie.http.proxy.password": ""}
var parameters = make(map[string]string)
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

	if parameters["notification_type"] == "" {
		if logger != nil {
			logger.Warning("Stopping, Nagios NOTIFICATIONTYPE param has no value, please make sure your Nagios and OpsGenie files pass necessary parameters")
		}

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
			configParameters[l[0]]=l[1]
			if l[0] == "nagios2opsgenie.timeout"{
				TOTAL_TIME,_ = strconv.Atoi(l[1])
			}
		}
	}
	if err := scanner.Err(); err != nil {
		panic(err)
	}
}

func configureLogger ()log.Logger{
	level := configParameters["nagios2opsgenie.logger"]
	var logFilePath = parameters["logPath"]

	if len(logFilePath) == 0 {
		logFilePath = "/var/log/opsgenie/nagios2opsgenie.log"
	}

	var tmpLogger log.Logger

	file, err := os.OpenFile(logFilePath, os.O_CREATE|os.O_WRONLY|os.O_APPEND, 0666)

	if err != nil {
		fmt.Println("Could not create log file \"" + logFilePath + "\", will log to \"/tmp/nagios2opsgenie.log\" file. Error: ", err)

		fileTmp, errTmp := os.OpenFile("/tmp/nagios2opsgenie.log", os.O_CREATE | os.O_WRONLY | os.O_APPEND, 0666)

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
	var proxyEnabled = configParameters["nagios2opsgenie.http.proxy.enabled"]
	var proxyHost = configParameters["nagios2opsgenie.http.proxy.host"]
	var proxyPort = configParameters["nagios2opsgenie.http.proxy.port"]
	var scheme = configParameters["nagios2opsgenie.http.proxy.protocol"]
	var proxyUsername = configParameters["nagios2opsgenie.http.proxy.username"]
	var proxyPassword = configParameters["nagios2opsgenie.http.proxy.password"]
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
	var logPrefix = ""

	if parameters["entity_type"] == "host"{
		logPrefix = "[HostName: "+ parameters["host_name"] + ", HostState: "+ parameters["host_state"] +"]"
	}else{
		logPrefix = "[HostName: "+ parameters["host_name"] + ", ServiceDesc: "+ parameters["service_desc"] + ", ServiceState: " + parameters["service_state"] +"]"
	}

	apiUrl := configParameters["opsgenie.api.url"] + "/v1/json/nagiosxi"
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
	for i := 1; i <= 3; i++ {
		body := bytes.NewBuffer(buf)
		request, _ := http.NewRequest("POST", apiUrl, body)
		client := getHttpClient(i)

		if logger != nil {
			logger.Debug(logPrefix + "Trying to send data to OpsGenie with timeout: ", (TOTAL_TIME / 12) * 2 * i)
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
						logger.Info(logPrefix +  "Data from Nagios posted to " + target + " successfully")					}
				}else{
					if logger != nil {
						logger.Error(logPrefix + "Couldn't post data from Nagios to " + target + " successfully; Response code: " + strconv.Itoa(resp.StatusCode) + " Response Body: " + string(body[:]))
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
				logger.Error(logPrefix + "Failed to post data from Nagios. ", error)
			}
		}
		if resp != nil{
			defer resp.Body.Close()
		}
	}
}

func parseFlags()map[string]string{
	apiKey := flag.String("apiKey","","api key")
	nagiosServer := flag.String("ns","","nagios server")

	entityType := flag.String("entityType","","")

	notificationType := flag.String("t", "","NOTIFICATIONTYPE")
	longDateTime := flag.String("ldt", "", "LONGDATETIME")

	hostName := flag.String("hn", "", "HOSTNAME")
	hostDisplayName := flag.String("hdn", "", "HOSTDISPLAYNAME")
	hostAlias := flag.String("hal", "", "HOSTALIAS")
	hostAddress := flag.String("haddr", "", "HOSTADDRESS")
	hostState := flag.String("hs", "", "HOSTSTATE")
	hostStateId := flag.String("hsi", "", "HOSTSTATEID")
	lastHostState := flag.String("lhs","","LASTHOSTSTATE")
	lastHostStateId := flag.String("lhsi","","LASTHOSTSTATEID")
	hostStateType := flag.String("hst","","HOSTSTATETYPE")
	hostAttempt := flag.String("ha", "", "HOSTATTEMPT")
	maxHostAttempts := flag.String("mha", "", "MAXHOSTATTEMPTS")
	hostEventId := flag.String("hei","","HOSTEVENTID")
	lastHostEventId := flag.String("lhei","","LASTHOSTEVENTID")
	hostProblemId := flag.String("hpi","","HOSTPROBLEMID")
	lastHostProblemId := flag.String("lhpi","","LASTHOSTPROBLEMID")
	hostLatency := flag.String("hl", "", "HOSTLATENCY")
	hostExecutionTime := flag.String ("het","","HOSTEXECUTIONTIME")
	hostDuration := flag.String("hd", "", "HOSTDURATION")
	hostDurationSec := flag.String("hds", "", "HOSTDURATIONSEC")
	hostDownTime := flag.String("hdt","","HOSTDOWNTIME")
	hostPercentChange := flag.String("hpc","","HOSTPERCENTCHANGE")
	hostGroupName := flag.String("hgn", "", "HOSTGROUPNAME")
	hostGroupNames := flag.String("hgns", "", "HOSTGROUPNAMES")
	lastHostCheck := flag.String("lhc", "", "LASTHOSTCHECK")
	lastHostStateChange := flag.String("lhsc", "", "LASTHOSTSTATECHANGE")
	lastHostUp := flag.String("lhu", "", "LASTHOSTUP")
	lastHostDown := flag.String("lhd", "", "LASTHOSTDOWN")
	lastHostUnreachable := flag.String("lhur", "", "LASTHOSTUNREACHABLE")
	hostOutput := flag.String("ho", "", "HOSTOUTPUT")
	longHostOutput := flag.String("lho", "", "LONGHOSTOUTPUT")
	hostNotesUrl := flag.String("hnu", "", "HOSTNOTESURL")
	hostPerfData := flag.String("hpd", "", "HOSTPERFDATA")

	serviceDesc := flag.String("s", "", "SERVICEDESC")
	serviceDisplayName := flag.String("sdn","","SERVICEDISPLAYNAME")
	serviceState := flag.String("ss", "", "SERVICESTATE")
	serviceStateId := flag.String("ssi", "", "SERVICESTATEID")
	lastServiceState := flag.String("lss", "", "LASTSERVICESTATE")
	lastServiceStateId := flag.String("lssi", "", "LASTSERVICESTATEID")
	serviceStateType := flag.String("sst", "", "SERVICESTATETYPE")
	serviceAttempt := flag.String("sa", "", "SERVICEATTEMPT")
	maxServiceAttempts := flag.String("msa", "", "MAXSERVICEATTEMPTS")
	serviceIsVolatile := flag.String("siv","","SERVICEISVOLATILE")
	serviceEventId := flag.String("sei","","SERVICEEVENTID")
	lastServiceEventId := flag.String("lsei","","LASTSERVICEEVENTID")
	serviceProblemId := flag.String("spi","","SERVICEPROBLEMID")
	lastServiceProblemId := flag.String("lspi","","LASTSERVICEPROBLEMID")
	serviceLatency := flag.String("sl", "", "SERVICELATENCY")
	serviceExecutionTime := flag.String("set","","SERVICEEXECUTIONTIME")
	serviceDuration := flag.String("sd", "", "SERVICEDURATION")
	serviceDurationSec := flag.String("sds", "", "SERVICEDURATIONSEC")
	serviceDownTime := flag.String("sdt","","SERVICEDOWNTIME")
	servicePercentChange := flag.String("spc","","SERVICEPERCENTCHANGE")
	serviceGroupName := flag.String("sgn", "", "SERVICEGROUPNAME")
	serviceGroupNames := flag.String("sgns", "", "SERVICEGROUPNAMES")
	lastServiceCheck := flag.String("lsch", "", "LASTSERVICECHECK")
	lastServiceStateChange := flag.String("lssc", "", "LASTSERVICESTATECHANGE")
	lastServiceOk := flag.String("lsok","","LASTSERVICEOK")
	lastServiceWarning := flag.String("lsw","","LASTSERVICEWARNING")
	lastServiceUnknown := flag.String("lsu","","LASTSERVICEUNKNOWN")
	lastServiceCritical := flag.String("lsc","","LASTSERVICECRITICAL")
	serviceOutput := flag.String("so", "", "SERVICEOUTPUT")
	longServiceOutput := flag.String("lso", "", "LONGSERVICEOUTPUT")
	serviceNotesUrl := flag.String("snu", "", "SERVICENOTESURL")
	servicePerfData := flag.String("spd", "", "SERVICEPERFDATA")
	logPath := flag.String("logPath", "", "LOGPATH")

	recipients := flag.String("recipients","","Recipients")
	tags := flag.String("tags","","Tags")
	teams := flag.String("teams","","Teams")

	flag.Parse()

	if *apiKey != ""{
		parameters["apiKey"] = *apiKey
	}else{
		parameters["apiKey"] = configParameters ["apiKey"]
	}
	if *nagiosServer != ""{
		parameters["nagios_server"] = *nagiosServer
	}else{
		parameters["nagios_server"] = configParameters["nagios_server"]
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

	if *logPath != "" {
		parameters["logPath"] = *logPath
	} else {
		parameters["logPath"] = configParameters["logPath"]
	}

	parameters["entity_type"] = *entityType

	parameters["notification_type"] = *notificationType
	parameters["long_date_time"] = *longDateTime

	parameters["host_name"] = *hostName
	parameters["host_display_name"] = *hostDisplayName
	parameters["host_alias"] = *hostAlias
	parameters["host_address"] = *hostAddress
	parameters["host_state"] = *hostState
	parameters["host_state_id"] = *hostStateId
	parameters["last_host_state"] = *lastHostState
	parameters["last_host_state_id"] = *lastHostStateId
	parameters["host_state_type"] = *hostStateType
	parameters["host_attempt"] = *hostAttempt
	parameters["max_host_attempts"] = *maxHostAttempts
	parameters["host_event_id"] = *hostEventId
	parameters["last_host_event_id"] = *lastHostEventId
	parameters["host_problem_id"] = *hostProblemId
	parameters["last_host_problem_id"] = *lastHostProblemId
	parameters["host_latency"] = *hostLatency
	parameters["host_execution_time"] = *hostExecutionTime
	parameters["host_duration"] = *hostDuration
	parameters["host_duration_sec"] = *hostDurationSec
	parameters["host_down_time"] = *hostDownTime
	parameters["host_percent_change"] = *hostPercentChange
	parameters["host_group_name"] = *hostGroupName
	parameters["host_group_names"] = *hostGroupNames
	parameters["last_host_check"] = *lastHostCheck
	parameters["last_host_state_change"] = *lastHostStateChange
	parameters["last_host_up"] = *lastHostUp
	parameters["last_host_down"] = *lastHostDown
	parameters["last_host_unreachable"] = *lastHostUnreachable
	parameters["host_output"] = *hostOutput
	parameters["long_host_output"] = *longHostOutput
	parameters["host_notes_url"] = *hostNotesUrl
	parameters["host_perf_data"] = *hostPerfData

	parameters["service_desc"] = *serviceDesc
	parameters["service_display_name"] = *serviceDisplayName
	parameters["service_state"] = *serviceState
	parameters["service_state_id"] = *serviceStateId
	parameters["last_service_state"] = *lastServiceState
	parameters["last_service_state_id"] = *lastServiceStateId
	parameters["service_state_type"] = *serviceStateType
	parameters["service_attempt"] = *serviceAttempt
	parameters["max_service_attempts"] = *maxServiceAttempts
	parameters["service_is_volatile"] = *serviceIsVolatile
	parameters["service_event_id"] = *serviceEventId
	parameters["last_service_event_id"] = *lastServiceEventId
	parameters["service_problem_id"] = *serviceProblemId
	parameters["last_service_problem_id"] = *lastServiceProblemId
	parameters["service_latency"] = *serviceLatency
	parameters["service_execution_time"] = *serviceExecutionTime
	parameters["service_duration"] = *serviceDuration
	parameters["service_duration_sec"] = *serviceDurationSec
	parameters["service_down_time"] = *serviceDownTime
	parameters["service_percent_change"] = *servicePercentChange
	parameters["service_group_name"] = *serviceGroupName
	parameters["service_group_names"] = *serviceGroupNames
	parameters["last_service_check"] = *lastServiceCheck
	parameters["last_service_state_change"] = *lastServiceStateChange
	parameters["last_service_ok"] = *lastServiceOk
	parameters["last_service_warning"] = *lastServiceWarning
	parameters["last_service_unknown"] = *lastServiceUnknown
	parameters["last_service_critical"] = *lastServiceCritical
	parameters["service_output"] = *serviceOutput
	parameters["long_service_output"] = *longServiceOutput
	parameters["service_notes_url"] = *serviceNotesUrl
	parameters["service_perf_data"] = *servicePerfData

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





