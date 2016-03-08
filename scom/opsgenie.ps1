Param (

[String]$ApiKey = "Your_API_Key",

[String]$AlertID,

[String]$AlertName,

[String]$AlertDesc,

[String]$ResolutionState,

[String]$ResolutionStateLastModified,

[String]$Priority,

[String]$Owner,

[String]$RepeatCount,

[String]$Severity,

[String]$Category,

[String]$CreatedByMonitor,

[String]$ManagedEntitySource,

[String]$WorkflowId,

[String]$LastModified,

[String]$TimeRaised,

[String]$TicketId,

[String]$DataItemCreateTimeLocal,

[String]$ManagedEntityPath,

[String]$ManagedEntity,

[String]$TimeAddedLocal,

[String]$MPElement = "NotPresent",

[String]$Custom1,

[String]$Custom2,

[String]$Custom3,

[String]$Custom4,

[String]$Custom5,

[String]$Custom6,

[String]$Custom7,

[String]$Custom8,

[String]$Custom9,

[String]$Custom10

)

$params = @{
alertId=$AlertID;
alertName=$AlertName;
alertDescription=$AlertDesc;
resolutionState=$ResolutionState;
resolutionStateLastModified=$ResolutionStateLastModified;
priority=$Priority;
owner=$Owner;
repeatCount=$RepeatCount;
severity=$Severity;
category=$Category;
createdByMonitor=$CreatedByMonitor;
managedEntitySource=$ManagedEntitySource;
workflowId=$WorkflowId;
lastModified=$LastModified;
timeRaised=$TimeRaised;
ticketId=$TicketId;
dataItemCreateTime=$DataItemCreateTimeLocal;
managedEntityPath=$ManagedEntityPath;
managedEntityGUID=$ManagedEntity;
timeAdded=$TimeAddedLocal;
mpElement=$MPElement;
customField1=$Custom1;
customField2=$Custom2;
customField3=$Custom3;
customField4=$Custom4;
customField5=$Custom5;
customField6=$Custom6;
customField7=$Custom7;
customField8=$Custom8;
customField9=$Custom9;
customField10=$Custom10;
}


$json = ConvertTo-Json -InputObject $params

$postFile = "C:\scripts\opsgenie\postResult.txt"

$urlWithoutApiKey = "http://qaapi.opsgeni.us/v1/json/scom?apiKey="

$endpoint = $urlWithoutApiKey + $ApiKey

try{

Invoke-RestMethod -Method Post -ContentType "application/json" -Body $json -Uri $endpoint | Out-File $postFile

}

catch{

out-file -InputObject "Exception Type: $($_.Exception.GetType().FullName) Exception Message: $($_.Exception.Message)" -FilePath $postFile

}