package main

import (
	"bytes"
	"encoding/json"
	"flag"
	"net/http"
	"net"
	"time"
	"os"
	"fmt"
	"io/ioutil"
)

var TIMEOUT = 30
var parameters = make(map[string]string)
var configParameters = map[string]string{"apiUrl" : "https://api.opsgenie.com"}

func main() {
	parseFlags()
	http_post()
}

func parseFlags()map[string]string{
	apiKey := flag.String("apiKey","", "api key")
	name := flag.String("name","", "heartbeat name")
	apiUrl := flag.String("apiUrl","", "api url")

	flag.Parse()

	parameters["apiKey"] = *apiKey
	parameters["name"] = *name

	if *apiUrl != ""{
		configParameters["apiUrl"] = *apiUrl
	}

	return parameters
}

func http_post()  {
	var buf, _ = json.Marshal(parameters)
	body := bytes.NewBuffer(buf)

	request, _ := http.NewRequest("POST", configParameters["apiUrl"] + "/v1/json/heartbeat/send", body)
	client := getHttpClient(TIMEOUT)

	resp, error := client.Do(request)
	if error == nil {
		defer resp.Body.Close()
		body, err := ioutil.ReadAll(resp.Body)
		if err == nil{
			if(resp.StatusCode == 200){
				fmt.Fprintln(os.Stdout, "OK - successfully sent heartbeat to opsgenie")
				os.Exit(0) // OK
			}else{
				fmt.Fprintln(os.Stdout, "Opsgenie response:" + string(body[:]))
				os.Exit(1) // warning
			}
		}else{
			fmt.Fprintln(os.Stdout, "Couldn't read the response from opsgenie", err)
			os.Exit(1) // warning
		}
	}else{
		fmt.Fprintln(os.Stdout, "couldn't send heartbeat to opsgenie", error)
		os.Exit(2) // critical
	}
}

func getHttpClient (seconds int) *http.Client{
	client := &http.Client{
		Transport: &http.Transport{
			Dial: func(netw, addr string) (net.Conn, error) {
				conn, err := net.DialTimeout(netw, addr, time.Second * time.Duration(seconds))
				if err != nil {
					return nil, err
				}
				conn.SetDeadline(time.Now().Add(time.Second * time.Duration(seconds)))
				return conn, nil
			},
		},
	}
	return client
}
