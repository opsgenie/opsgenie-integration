#######################################
##### Author: Allen Craig Barnard #####
##### Date published: 29-July-2020 ####
##### Company:          Atlassian #####
#######################################

##### What is this? This is a PRTG to Opsgenie integration script #####
##### What does this do? This scritps sends PRTG alerts to Opsgenie ###

##### set the parameters we expect to recieved from PRTG #####
param(
[String]$apiURL,
[String]$deviceString,
[String]$linkdeviceString,
[String]$sitenameString,
[String]$serviceurlString,
[String]$settingsString,
[String]$datetimeString,
[String]$historyString,
[String]$AlertinghostString,
[String]$downString,
[String]$downtimeString,
[String]$lastdownString,
[String]$nodenameString,
[String]$locationString,
[String]$groupString,
[String]$linkgroupString,
[String]$lastmessageString,
[String]$lastupString,
[String]$uptimeString,
[String]$statusString,
[String]$statesinceString,
[String]$sensorString,
[String]$linksensorString,
[String]$probeString,
[String]$priorityString,
[String]$commentssensorString,
[String]$commentsdeviceString,
[String]$commentsgroupString,
[String]$commentsprobeString,
[String]$colorofstateString,
[String]$iconofstateString,
[String]$idString
       )

##### Allow Session to use TLS 1.2  #####
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12

##### Add System.Web and use UrlEncode method to URL encode the PRTG parameter values. #####
Add-Type -AssemblyName System.Web
$deviceString = [System.Web.HttpUtility]::UrlEncode($deviceString)
$linkdeviceString = [System.Web.HttpUtility]::UrlEncode($linkdeviceString)
$sitenameString = [System.Web.HttpUtility]::UrlEncode($sitenameString)
$serviceurlString = [System.Web.HttpUtility]::UrlEncode($serviceurlString)
$settingsString = [System.Web.HttpUtility]::UrlEncode($settingsString)
$datetimeString = [System.Web.HttpUtility]::UrlEncode($datetimeString)
$historyString = [System.Web.HttpUtility]::UrlEncode($historyString)
$AlertinghostString = [System.Web.HttpUtility]::UrlEncode($AlertinghostString)
$downString = [System.Web.HttpUtility]::UrlEncode($downString)
$downtimeString = [System.Web.HttpUtility]::UrlEncode($downtimeString)
$lastdownString = [System.Web.HttpUtility]::UrlEncode($lastdownString)
$nodenameString = [System.Web.HttpUtility]::UrlEncode($nodenameString)
$locationString = [System.Web.HttpUtility]::UrlEncode($locationString)
$groupString = [System.Web.HttpUtility]::UrlEncode($groupString)
$linkgroupString = [System.Web.HttpUtility]::UrlEncode($linkgroupString)
$lastmessageString = [System.Web.HttpUtility]::UrlEncode($lastmessageString)
$lastupString = [System.Web.HttpUtility]::UrlEncode($lastupString)
$uptimeString = [System.Web.HttpUtility]::UrlEncode($uptimeString)
$statusString = [System.Web.HttpUtility]::UrlEncode($statusString)
$statesinceString = [System.Web.HttpUtility]::UrlEncode($statesinceString)
$sensorString = [System.Web.HttpUtility]::UrlEncode($sensorString)
$linksensorString = [System.Web.HttpUtility]::UrlEncode($linksensorString)
$probeString = [System.Web.HttpUtility]::UrlEncode($probeString)
$priorityString = [System.Web.HttpUtility]::UrlEncode($priorityString)
$commentssensorString = [System.Web.HttpUtility]::UrlEncode($commentssensorString)
$commentsdeviceString = [System.Web.HttpUtility]::UrlEncode($commentsdeviceString)
$commentsgroupString = [System.Web.HttpUtility]::UrlEncode($commentsgroupString)
$commentsprobeString = [System.Web.HttpUtility]::UrlEncode($commentsprobeString)
$colorofstateString = [System.Web.HttpUtility]::UrlEncode($colorofstateString)
$iconofstateString = [System.Web.HttpUtility]::UrlEncode($iconofstateString)
$idString = [System.Web.HttpUtility]::UrlEncode($idString)

##### Construct payload using the URL encoded strings #####
$payload = ("device="+$deviceString+"&linkdevice="+$linkdeviceString+"&sitename="+$sitenameString+"&serviceurl="+$serviceurlString+"&settings="+$settingsString+"&datetime="+$datetimeString+"&history="+$historyString+"&host="+$AlertinghostString+"&down="+$downString+"&downtime="+$downtimeString+"&lastdown="+$lastdownString+"&nodename="+$nodenameString+"&location="+$locationString+"&group="+$groupString+"&linkgroup="+$linkgroupString+"&lastmessage="+$lastmessageString+"&lastup="+$lastupString+"&uptime="+$uptimeString+"&status="+$statusString+"&statesince="+$statesinceString+"&sensor="+$sensorString+"&linksensor="+$linksensorString+"&probe="+$probeString+"&priority="+$priorityString+"&commentssensor="+$commentssensorString+"&commentsdevice="+$commentsdeviceString+"&commentsgroup="+$commentsgroupString+"&commentsprobe="+$commentsprobeString+"&colorofstate="+$colorofstateString+"&iconofstate="+$iconofstateString+"&id="+$idString+"&")

##### Post to Opsgenie API URL webhook #####
Invoke-WebRequest -Uri $apiURL -Method Post -Body $payload