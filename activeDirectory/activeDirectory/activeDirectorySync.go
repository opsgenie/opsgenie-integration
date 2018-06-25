package main

import (
	"bufio"
	"crypto/tls"
	"flag"
	"fmt"
	"io"
	"net"
	"net/http"
	"net/url"
	"os"
	"strings"
	"time"

	"bytes"
	"encoding/json"
	"errors"
	"io/ioutil"
	"os/exec"
	"strconv"

	"github.com/alexcesaro/log"
	"github.com/alexcesaro/log/golog"
)

var configParameters = map[string]string{
	"ogUrl":                "https://api.opsgenie.com",
	"logLevel":             "warning",
	"sendInvitationEmails": "true",
	"applyDeletions":       "false",
	"http.proxy.enabled":   "false",
	"http.proxy.host":      "localhost",
	"http.proxy.protocol":  "http",
	"http.proxy.username":  "admin",
}

var logLevels = map[string]log.Level{"info": log.Info, "debug": log.Debug, "warning": log.Warning, "error": log.Error}
var logger log.Logger
var totalTime = 60
var configPath = "." + string(os.PathSeparator) + "activeDirectorySync.conf"

const powershellScriptPath = "." + string(os.PathSeparator) + "activeDirectorySync.ps1"

func main() {
	configFile, err := os.Open(configPath)

	if err == nil {
		readConfigFile(configFile)
	} else {
		panic(err)
	}

	version := flag.String("v", "", "")

	if *version != "" {
		fmt.Println("Version: 1.0")
		return
	}

	logger = configureLogger()
	printConfigToLog()

	groupsToSync := getGroupsToSync()

	if len(groupsToSync) > 0 {
		var usersExistsInADGroups = make([]string, 0)
		var usersExistsInOGTeams = make([]string, 0)

		for _, group := range groupsToSync {
			adGroupMembers, errADGroupMember := listGroupMembers(group)

			if errADGroupMember != nil {
				logger.Error("Error occurred when retrieving group members from Active Directory. Error: " + errADGroupMember.Error())
				continue
			}

			ogTeamExists, errOgTeamExists := checkOpsGenieTeam(group)

			if errOgTeamExists != nil {
				logger.Error("Error occurred while checking if the team ["+group+
					"] exists in OpsGenie. Error: ", errOgTeamExists.Error())
			}

			if ogTeamExists {
				if len(adGroupMembers) > 0 {
					adUserDetails, errADUserDetails := getUsersDetails(adGroupMembers)
					var adUserEmailsNamesMap = make(map[string]string)
					ogUsers, errOGUsers := getOpsGenieTeamMembers(group)

					if errADUserDetails != nil {
						logger.Error("Error occurred when retrieving user details from Active Directory. Error: " +
							errADUserDetails.Error() + ", quitting.")
						os.Exit(0)
					}

					if errOGUsers != nil {
						logger.Error("Error occurred when retrieving user details from OpsGenie. Error: " +
							errOGUsers.Error() + ", quitting.")
						os.Exit(0)
					}

					for _, ogUser := range ogUsers {
						if !elementExistsInList(usersExistsInOGTeams, ogUser) {
							usersExistsInOGTeams = append(usersExistsInOGTeams, ogUser)
						}
					}

					for _, userDetails := range adUserDetails {
						adUserEmailsNamesMap[userDetails["email"]] = userDetails["fullName"]
					}

					var adUserEmailsList = make([]string, 0)

					for k := range adUserEmailsNamesMap {
						adUserEmailsList = append(adUserEmailsList, k)

						if !elementExistsInList(usersExistsInADGroups, k) {
							usersExistsInADGroups = append(usersExistsInADGroups, k)
						}
					}

					var usersToCreate = subtractList(adUserEmailsList, ogUsers)
					var usersToRemoveFromTeam = subtractList(ogUsers, adUserEmailsList)

					for _, user := range usersToCreate {
						err := createOpsGenieUser(adUserEmailsNamesMap[user], user)

						if err != nil {
							logger.Error("Error occurred while creating OpsGenie user with email [\"" + user + "\"], skipping user creation. Error: " + err.Error())
						} else {
							logger.Info("Created the user [" + user + "] in OpsGenie successfully.")
						}

						addMembersToOpsGenieTeam(group, user)
					}

					if configParameters["applyDeletions"] == "true" {
						var deletedTeamMembers = make([]string, 0)

						for _, user := range usersToRemoveFromTeam {
							userHasADUserTag, errUserHasADUserTag := opsgenieUserHasADUserTag(user)

							if errUserHasADUserTag != nil {
								logger.Error("Error occurred while checking if the user with username [" + user + "] has Active Directory User tag.")
								continue
							}

							if userHasADUserTag {
								errRemoveFromTeam := deleteMemberFromOpsGenieTeam(group, user)

								if errRemoveFromTeam != nil {
									logger.Error("Error occurred while removing the user with username [" + user + "] from the team [" + group + "] in OpsGenie. Error: " + errRemoveFromTeam.Error())
								} else {
									logger.Info("Removed the user with username [" + user + "] from the team [" + group + "] in OpsGenie successfully.")
									deletedTeamMembers = append(deletedTeamMembers, user)
								}
							}
						}

						if len(adUserDetails) == 0 && allMembersOfTeamAreDeleted(ogUsers, deletedTeamMembers) {
							logger.Warning("No member exists with type user which has an email address set for group [\"" + group + "\"], and the team has no members in OpsGenie, the team will be deleted in OpsGenie, if exists.")
							errDeleteTeam := deleteOpsGenieTeam(group)

							if errDeleteTeam != nil {
								logger.Error("Error occurred while deleting OpsGenie team with name [" + group + "]. Error: " + errDeleteTeam.Error())
							} else {
								logger.Info("Deleted the team with name [" + group + "] in OpsGenie successfully.")
							}
						}
					}
				} else {
					if configParameters["applyDeletions"] == "true" {
						var ogTeamMembers, _ = getOpsGenieTeamMembers(group)

						if len(ogTeamMembers) == 0 {
							logger.Warning("No member exists with type user for group [\"" + group + "\"], the team will be deleted in OpsGenie, if exists.")
							errDeleteTeam := deleteOpsGenieTeam(group)

							if errDeleteTeam != nil {
								logger.Error("Error occurred while deleting OpsGenie team with name [" + group + "]. Error: " + errDeleteTeam.Error())
							} else {
								logger.Info("Deleted the team with name [" + group + "] in OpsGenie successfully.")
							}
						} else {
							var deletedTeamMembers = make([]string, 0)

							for _, ogTeamMember := range ogTeamMembers {
								userHasADUserTag, errUserHasADUserTag := opsgenieUserHasADUserTag(ogTeamMember)

								if errUserHasADUserTag != nil {
									logger.Error("Error occurred while checking if the user with username [" + ogTeamMember + "] has Active Directory User tag.")
									continue
								}

								if userHasADUserTag {
									errRemoveFromTeam := deleteMemberFromOpsGenieTeam(group, ogTeamMember)

									if errRemoveFromTeam != nil {
										logger.Error("Error occurred while removing the user with username [" + ogTeamMember + "] from the team [" + group + "] in OpsGenie. Error: " + errRemoveFromTeam.Error())
									} else {
										logger.Info("Removed the user with username [" + ogTeamMember + "] from the team [" + group + "] in OpsGenie successfully.")
										deletedTeamMembers = append(deletedTeamMembers, ogTeamMember)
									}
								}
							}

							if allMembersOfTeamAreDeleted(ogTeamMembers, deletedTeamMembers) {
								logger.Warning("No member exists with type user which has an email address set for group [\"" + group + "\"], and the team has no members in OpsGenie, the team will be deleted in OpsGenie, if exists.")
								errDeleteTeam := deleteOpsGenieTeam(group)

								if errDeleteTeam != nil {
									logger.Error("Error occurred while deleting OpsGenie team with name [" + group + "]. Error: " + errDeleteTeam.Error())
								} else {
									logger.Info("Deleted the team with name [" + group + "] in OpsGenie successfully.")
								}
							}
						}
					} else {
						logger.Warning("No member exists with type user for group [\"" + group + "\"], skipping.")
					}
				}
			} else {
				if len(adGroupMembers) > 0 {
					adUserDetails, errADUserDetails := getUsersDetails(adGroupMembers)

					if errADUserDetails != nil {
						logger.Error("Error occurred when retrieving user details from Active Directory. Error: " + errADUserDetails.Error() + ", quitting.")
						os.Exit(0)
					}

					if len(adUserDetails) > 0 {
						errCreateTeam := createOpsGenieTeam(group)

						if errCreateTeam != nil {
							logger.Error("Error occurred while creating team [" + group + "] in OpsGenie. Error: " + errCreateTeam.Error())
							continue
						} else {
							logger.Info("Created the team [" + group + "] in OpsGenie, successfully.")
						}

						for _, userDetails := range adUserDetails {
							fullName := userDetails["fullName"]
							email := userDetails["email"]

							err := createOpsGenieUser(fullName, email)

							if err != nil {
								logger.Error("Error occurred while creating OpsGenie user with email [\"" + email + "\"], skipping user creation. Error: " + err.Error())
							} else {
								logger.Info("Created the user [" + email + "] in OpsGenie, successfully.")
							}

							err = addMembersToOpsGenieTeam(group, email)

							if err != nil {
								logger.Error("Error occurred while adding the user [" + email + "] to the team [" + group + "], skipping adding member to team. Error: " + err.Error())
							} else {
								logger.Info("Added the user [" + email + "] to the team [" + group + "] in OpsGenie, successfully.")
							}
						}
					} else {
						logger.Warning("No member exists with email address set for group [" + group + "], skipping creating the team in OpsGenie.")
					}
				} else {
					logger.Warning("No member exists with type user for group [\"" + group + "\"], skipping creating the team in OpsGenie.")
				}
			}
		}

		if configParameters["applyDeletions"] == "true" {
			var usersToDelete = subtractList(usersExistsInOGTeams, usersExistsInADGroups)

			for _, user := range usersToDelete {

				userHasADUserTag, errUserHasADUserTag := opsgenieUserHasADUserTag(user)

				if errUserHasADUserTag != nil {
					logger.Error("Error occurred while checking if the user with username [" + user + "] has Active Directory User tag.")
					continue
				}

				if userHasADUserTag {
					errDeleteOpsGenieUser := deleteOpsGenieUser(user)

					if errDeleteOpsGenieUser != nil {
						logger.Error("Error occurred while deleting the user in OpsGenie. Error: " + errDeleteOpsGenieUser.Error())
					} else {
						logger.Info("Deleted the user with username [" + user + "] in OpsGenie successfully.")
					}
				}
			}
		}
	} else {
		logger.Error("No configured Active Directory groups found in the configuration, quitting.")
	}
}

func printConfigToLog() {
	if logger != nil {
		if logger.LogDebug() {
			logger.Debug("-----Config-----")

			for k, v := range configParameters {
				if strings.Contains(k, "password") {
					logger.Debug(k + "=*******")
				} else {
					logger.Debug(k + "=" + v)
				}
			}

			logger.Debug("---End Config---")
		}
	}
}

func getGroupsToSync() []string {
	var groupsList []string
	groups := configParameters["groupsToSync"]
	groupsSplit := strings.Split(groups, ",")

	for _, group := range groupsSplit {
		groupsList = append(groupsList, strings.TrimSpace(group))
	}

	return groupsList
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
		}
	}

	if err := scanner.Err(); err != nil {
		panic(err)
	}
}

func configureLogger() log.Logger {
	level := configParameters["logLevel"]
	var logFilePath = configParameters["logPath"]

	if len(logFilePath) == 0 {
		logFilePath = "." + string(os.PathSeparator) + "activeDirectorySync.log"
	}

	var tmpLogger log.Logger

	file, err := os.OpenFile(logFilePath, os.O_CREATE|os.O_WRONLY|os.O_APPEND, 0666)

	if err != nil {
		tempLogFilePath := "C:" + string(os.PathSeparator) + "Windows" + string(os.PathSeparator) + "Temp" +
			string(os.PathSeparator) + "activeDirectorySync.log"
		fmt.Println("Could not create log file \""+logFilePath+"\", will log to \""+tempLogFilePath+
			"\" file. Error: ", err)
		fileTmp, errTmp := os.OpenFile(tempLogFilePath,
			os.O_CREATE|os.O_WRONLY|os.O_APPEND, 0666)

		if errTmp != nil {
			fmt.Println("Logging disabled. Reason: ", errTmp)
		} else {
			tmpLogger = golog.New(fileTmp, logLevels[strings.ToLower(level)])
		}
	} else {
		tmpLogger = golog.New(file, logLevels[strings.ToLower(level)])
	}

	return tmpLogger
}

func getHttpClient(timeout int) *http.Client {
	seconds := (totalTime / 12) * 2 * timeout
	var proxyEnabled = configParameters["http.proxy.enabled"]
	var proxyHost = configParameters["http.proxy.host"]
	var proxyPort = configParameters["http.proxy.port"]
	var scheme = configParameters["http.proxy.protocol"]
	var proxyUsername = configParameters["http.proxy.username"]
	var proxyPassword = configParameters["http.proxy.password"]

	proxy := http.ProxyFromEnvironment

	if proxyEnabled == "true" {
		u := new(url.URL)
		u.Scheme = scheme
		u.Host = proxyHost + ":" + proxyPort

		if len(proxyUsername) > 0 {
			u.User = url.UserPassword(proxyUsername, proxyPassword)
		}

		if logger != nil {
			logger.Debug("[getHttpClient] Formed Proxy url: ", u)
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
						logger.Error("[getHttpClient] Error occurred while connecting: ", err)
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

func httpRequest(url string, method string, headers map[string]string, body map[string]interface{}) (*http.Response, string, error) {
	if logger != nil {
		logger.Debug("[httpRequest] URL: " + url)
		logger.Debug("[httpRequest] Method: " + method)

		headersToLog := getHeadersForLogging(headers)
		logger.Debug("[httpRequest] Headers:")
		logger.Debug(headersToLog)

		logger.Debug("[httpRequest] Body:")
		logger.Debug(body)
	}

	var request *http.Request

	if body != nil {
		var buf, _ = json.Marshal(body)
		bodyJson := bytes.NewBuffer(buf)
		request, _ = http.NewRequest(method, url, bodyJson)
	} else {
		request, _ = http.NewRequest(method, url, nil)
	}

	var response *http.Response
	var responseBody string
	var err error

	for key, value := range headers {
		request.Header.Add(key, value)
	}

	for i := 1; i <= 3; i++ {
		client := getHttpClient(i)

		if logger != nil {
			logger.Debug("[httpRequest] Trying to make an HTTP " + method + " request to URL [\"" + url + "\"] with timeout: " +
				strconv.Itoa((totalTime/12)*2*i) + " seconds.")
		}

		response, err = client.Do(request)

		if err == nil {
			defer response.Body.Close()
			responseBodyBytes, err := ioutil.ReadAll(response.Body)
			responseBody = string(responseBodyBytes[:])

			if err == nil {
				if 200 <= response.StatusCode && response.StatusCode < 400 {
					if logger != nil {
						logger.Debug("[httpRequest] Response code: " + strconv.Itoa(response.StatusCode))
						logger.Debug("[httpRequest] Response: " + string(responseBody[:]))
						logger.Info("[httpRequest] HTTP " + method + " request to URL [\"" + url + "\"] was successful.")
					}
				} else {
					if logger != nil {
						logger.Error("[httpRequest] Couldn't make HTTP " + method + " request to URL [\"" + url +
							"\"]; Response code: " + strconv.Itoa(response.StatusCode) + " Response Body: " +
							string(responseBody[:]))
					}
				}
			} else {
				if logger != nil {
					logger.Error("[httpRequest] Couldn't read the response from URL [\""+url+"\"].", err)
				}
			}

			break
		} else if i < 3 {
			if logger != nil {
				logger.Error("[httpRequest] Error occurred during the HTTP "+method+" request to URL [\""+
					url+"\"], will retry.", err)
			}
		} else {
			if logger != nil {
				logger.Error("[httpRequest] Failed to make HTTP "+method+" request to URL [\""+url+"\"].", err)
			}
		}

		if response != nil {
			defer response.Body.Close()
		}
	}

	return response, responseBody, err
}

func getHeadersForLogging(headers map[string]string) map[string]string {
	headersToLog := make(map[string]string)

	for key, value := range headers {
		if !strings.EqualFold(key, "authorization") {
			headersToLog[key] = value
		}
	}

	return headersToLog
}

func makePowershellRequest(arguments []string, objToUnmarshal interface{}) error {
	arguments = append([]string{powershellScriptPath}, arguments...)

	for index, argument := range arguments {
		if strings.Contains(argument, " ") {
			arguments[index] = "\"" + argument + "\""
		}
	}

	logger.Debug("Making Powershell request with arguments:")
	logger.Debug(arguments)

	command := exec.Command("powershell.exe", arguments...)
	var stdOut bytes.Buffer
	var stdErr bytes.Buffer
	command.Stdout = &stdOut
	command.Stderr = &stdErr
	err := command.Run()

	if err != nil {
		return errors.New("Cannot read response from Powershell. " + fmt.Sprint(err) + ": " + stdErr.String())
	}

	logger.Debug("Response from Powershell:")
	logger.Debug(stdOut.String())

	err = json.Unmarshal(stdOut.Bytes(), objToUnmarshal)

	if err != nil {
		return errors.New("Cannot unmarshal data from Powershell. Err: " + err.Error() + "\nData: " + stdOut.String())
	}

	return nil
}

func listGroupMembers(groupName string) ([]string, error) {
	var groupMembers = make([]string, 0)
	var err error

	if len(groupName) > 0 {
		err = makePowershellRequest([]string{"-command", "listGroupMembers", "-groupName", groupName}, &groupMembers)
	}

	return groupMembers, err
}

func getUsersDetails(usernames []string) ([]map[string]string, error) {
	usernamesStr := strings.Join(usernames, ",")

	var userDetails = make([]map[string]string, 0)
	var userDetailsToReturn = make([]map[string]string, 0)
	var err error

	if len(usernamesStr) > 0 {
		err = makePowershellRequest([]string{"-command", "getUsersDetails", "-username", usernamesStr}, &userDetails)

		if len(userDetails) > 0 {
			for _, userDetail := range userDetails {
				if len(userDetail["fullName"]) > 0 && len(userDetail["email"]) > 0 {
					userDetailsToReturn = append(userDetailsToReturn, userDetail)
				}
			}
		}
	}

	return userDetailsToReturn, err
}

func checkOpsGenieTeam(teamName string) (bool, error) {
	response, responseBody, err := httpRequest(configParameters["ogUrl"]+"/v2/teams/"+teamName+
		"?identifierType=name", "GET", map[string]string{"Authorization": "GenieKey " +
		configParameters["ogApiKey"]}, nil)

	if err != nil {
		return false, errors.New("Could not get team [" + teamName + "] from OpsGenie. Error: " + err.Error())
	}

	if 200 <= response.StatusCode && response.StatusCode < 400 {
		return true, nil
	} else if response.StatusCode == 404 {
		return false, nil
	} else {
		return false, errors.New("Error occurred while getting team from OpsGenie. Status code: " +
			strconv.Itoa(response.StatusCode) + ". Response: " + responseBody)
	}
}

func createOpsGenieTeam(teamName string) error {
	headers := map[string]string{
		"Authorization": "GenieKey " + configParameters["ogApiKey"],
		"Content-Type":  "application/json",
	}

	body := map[string]interface{}{
		"name": teamName,
	}

	response, _, err := httpRequest(configParameters["ogUrl"]+"/v2/teams", "POST", headers, body)

	if response.StatusCode == 409 {
		return errors.New("OpsGenie team with name [" + teamName + "] already exists.")
	}

	return err
}

func getOpsGenieTeamMembers(teamName string) ([]string, error) {
	var ogTeamMembers = make([]string, 0)

	headers := map[string]string{
		"Authorization": "GenieKey " + configParameters["ogApiKey"],
		"Content-Type":  "application/json",
	}

	_, responseBody, err := httpRequest(configParameters["ogUrl"]+"/v2/teams/"+teamName+"?identifierType=name", "GET",
		headers, nil)

	if err != nil {
		return nil, err
	}

	var responseMap = make(map[string]interface{})
	err = json.Unmarshal([]byte(responseBody), &responseMap)

	if err != nil {
		return nil, err
	}

	var dataMap = responseMap["data"].(map[string]interface{})

	if _, ok := dataMap["members"]; ok {
		var membersList = dataMap["members"].([]interface{})

		for _, memberMap := range membersList {
			var userMap = memberMap.(map[string]interface{})["user"].(map[string]interface{})
			var username = userMap["username"].(string)

			if len(username) > 0 {
				ogTeamMembers = append(ogTeamMembers, username)
			}
		}
	}

	return ogTeamMembers, nil
}

func addMembersToOpsGenieTeam(teamName string, email string) error {
	headers := map[string]string{
		"Authorization": "GenieKey " + configParameters["ogApiKey"],
		"Content-Type":  "application/json",
	}

	body := make(map[string]interface{})
	body["user"] = make(map[string]string)
	body["user"].(map[string]string)["username"] = email

	_, _, err := httpRequest(configParameters["ogUrl"]+"/v2/teams/"+teamName+
		"/members?teamIdentifierType=name", "POST", headers, body)

	if err != nil {
		return err
	}

	return nil
}

func deleteMemberFromOpsGenieTeam(teamName string, member string) error {
	headers := map[string]string{
		"Authorization": "GenieKey " + configParameters["ogApiKey"],
		"Content-Type":  "application/json",
	}

	_, _, err := httpRequest(configParameters["ogUrl"]+"/v2/teams/"+teamName+
		"/members/"+member+"?teamIdentifierType=name", "DELETE", headers, nil)

	return err
}

func deleteOpsGenieTeam(teamName string) error {
	headers := map[string]string{
		"Authorization": "GenieKey " + configParameters["ogApiKey"],
		"Content-Type":  "application/json",
	}

	response, _, err := httpRequest(configParameters["ogUrl"]+"/v2/teams/"+teamName+"?identifierType=name",
		"DELETE", headers, nil)

	if response.StatusCode == 404 {
		return errors.New("No team with name [" + teamName + "] exists in OpsGenie.")
	}

	return err
}

func opsgenieUserHasADUserTag(email string) (bool, error) {
	headers := map[string]string{
		"Authorization": "GenieKey " + configParameters["ogApiKey"],
		"Content-Type":  "application/json",
	}

	_, responseStr, err := httpRequest(configParameters["ogUrl"]+"/v2/users/"+email, "GET", headers, nil)

	if err != nil {
		return false, err
	}

	var responseMap map[string]*json.RawMessage
	err = json.Unmarshal([]byte(responseStr[:]), &responseMap)

	if err != nil {
		return false, err
	}

	if responseMap["data"] != nil {
		var dataMap map[string]*json.RawMessage
		err = json.Unmarshal(*responseMap["data"], &dataMap)

		if err != nil {
			return false, err
		}

		if dataMap["tags"] != nil {
			var tags []string
			err = json.Unmarshal(*dataMap["tags"], &tags)

			if err != nil {
				return false, err
			}

			var isADUser = false

			for _, tag := range tags {
				if tag == "Active Directory User" {
					isADUser = true
				}
			}

			return isADUser, nil
		} else {
			return false, nil
		}
	} else {
		return false, errors.New("data map in the get user response was nil.")
	}
}

func createOpsGenieUser(fullName string, email string) error {
	headers := map[string]string{
		"Authorization": "GenieKey " + configParameters["ogApiKey"],
		"Content-Type":  "application/json",
	}

	var invitationDisabled bool

	if configParameters["sendInvitationEmails"] == "false" {
		invitationDisabled = true
	} else {
		invitationDisabled = false
	}

	body := map[string]interface{}{
		"username":           email,
		"fullName":           fullName,
		"invitationDisabled": invitationDisabled,
		"tags":               []string{"Active Directory User"},
	}

	response, _, err := httpRequest(configParameters["ogUrl"]+"/v2/users", "POST", headers, body)

	if response.StatusCode == 409 {
		return errors.New("OpsGenie user with username [" + email + "] already exists.")
	}

	return err
}

func deleteOpsGenieUser(email string) error {
	headers := map[string]string{
		"Authorization": "GenieKey " + configParameters["ogApiKey"],
		"Content-Type":  "application/json",
	}

	_, _, err := httpRequest(configParameters["ogUrl"]+"/v2/users/"+email,
		"DELETE", headers, nil)

	return err
}

func subtractList(minuend []string, subtrahend []string) []string {
	var difference []string

	for _, elementM := range minuend {
		var found = false

		for _, elementS := range subtrahend {
			if elementS == elementM {
				found = true
				break
			}
		}

		if !found {
			difference = append(difference, elementM)
		}
	}

	return difference
}

func elementExistsInList(list []string, element string) bool {
	for _, i := range list {
		if i == element {
			return true
		}
	}

	return false
}

func allMembersOfTeamAreDeleted(teamMembersList []string, removedMembersList []string) bool {
	teamMembersMap := make(map[string]int)
	removedMembersMap := make(map[string]int)

	for _, teamMember := range teamMembersList {
		teamMembersMap[teamMember]++
	}
	for _, removedMember := range removedMembersList {
		removedMembersMap[removedMember]++
	}

	for teamMemberKey, teamMemberValue := range teamMembersMap {
		if removedMembersMap[teamMemberKey] != teamMemberValue {
			return false
		}
	}

	return true
}
