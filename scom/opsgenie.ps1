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

[String]$TimeRaised

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
}


$json = ConvertTo-Json -InputObject $params

$postFile = "C:\scripts\opsgenie\postResult.txt"

$urlWithoutApiKey = "https://api.opsgenie.com/v1/json/scom?apiKey="

$endpoint = $urlWithoutApiKey + $ApiKey

try{

Invoke-RestMethod -Method Post -ContentType "application/json" -Body $json -Uri $endpoint | Out-File $postFile

}

catch{

out-file -InputObject "Exception Type: $($_.Exception.GetType().FullName) Exception Message: $($_.Exception.Message)" -FilePath $postFile

}