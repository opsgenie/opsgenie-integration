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
	"log"
	"strconv"
	"fmt"
)

//default configuration
var NAGIOS_SERVER = "default"
var API_KEY = ""
var TOTAL_TIME = 0
var parameters = map[string]string{"apiKey": API_KEY,"nagios_server": NAGIOS_SERVER}
var configPath = "/etc/opsgenie/nagios2opsgenie.conf"
var (
	Trace   *log.Logger
	Info    *log.Logger
	Warning *log.Logger
	Error   *log.Logger
)

func main() {
	fmt.Println("started")
	configureLogger()
	configFile, err := os.Open(configPath)
	fmt.Println("read config")
	if err == nil{
		readConfigFile(configFile)
	}
	fmt.Println("finish")
	parseFlags()
	if parameters["notification_type"] == "" {
		Warning.Println("Stopping, Nagios NOTIFICATIONTYPE param has no value, please make sure your Nagios and OpsGenie files pass necessary parameters")
		return
	}
	fmt.Println("before")
	http_post()
	fmt.Println("post")

}

func configureLogger (){
	file, err := os.OpenFile("nagios2opsgenieout.log", os.O_CREATE|os.O_WRONLY|os.O_APPEND, 0666)
	if err != nil {
		log.Fatalln("Failed to open log file", ":", err)
	}

	Trace = log.New(file, "TRACE: ", log.Ldate|log.Lmicroseconds|log.Lshortfile)
	Info = log.New(file, "INFO: ", log.Ldate|log.Lmicroseconds|log.Lshortfile)
	Warning = log.New(file, "WARN: ", log.Ldate|log.Lmicroseconds|log.Lshortfile)
	Error = log.New(file, "ERROR: ", log.Ldate|log.Lmicroseconds|log.Lshortfile)
}

func readConfigFile(file io.Reader){
	reader := bufio.NewReader(file)
	for {
		line, err := reader.ReadString('\n')
		if err !=nil {
			if err == io.EOF && len(line) == 0 {
				break
			}else{
				Error.Println("Error occured: ", err)
				panic(err)
			}
		}
		fmt.Println(line)
		line = strings.TrimSpace(line)
		if !strings.HasPrefix(line,"#") && line != "" {
			l := strings.Split(line,"=")
			parameters[l[0]] = l[1]
			if l[0] == "timeout"{
				TOTAL_TIME,_ = strconv.Atoi(l[1])
			}
		}
	}
}

func getHttpClient (timeout int) *http.Client{
	seconds := (TOTAL_TIME/12)*2*timeout
	client := &http.Client{
		Transport: &http.Transport{
			Dial: func(netw, addr string) (net.Conn, error) {
				conn, err := net.DialTimeout(netw, addr, time.Second * time.Duration(seconds))
				if err != nil {
					Error.Println("Error occured while connecting: ",err)
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
	url := parameters["opsgenie_url"]
	delete(parameters,"opsgenie_url")
	var buf, _ = json.Marshal(parameters)

	for i := 1; i <= 3; i++ {
		body := bytes.NewBuffer(buf)
		request, _ := http.NewRequest("POST", url, body)
		client := getHttpClient(i)
		Warning.Println("Trying to send data to OpsGenie with timeout: ",(TOTAL_TIME/12)*2*i)
		resp, error := client.Do(request)
		if error == nil  && resp.StatusCode == 200{
			Warning.Println("Data from Nagios posted to OpsGenie successfully.")
			break
		}else if i<3{
			Error.Println("Error occured while sending data, will retry." )
		}else {
			Error.Println("Failed to post data from Nagios to OpsGenie.")
		}
//		defer resp.Body.Close()
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
	servicePerfData := flag.String("spd", "", "SERVICEPERFDATA")

	flag.Parse()

	if *apiKey != ""{
		parameters["apiKey"] = *apiKey
	}
	if *nagiosServer != ""{
		parameters["nagios_server"] = *nagiosServer
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
	parameters["service_perf_data"] = *servicePerfData

	return parameters
}





