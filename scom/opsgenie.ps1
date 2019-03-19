Param (
    [String]$Url = "https://api.opsgenie.com/v1/json/scom?apiKey=YOUR_API_KEY",
    [String]$AlertID,
    [String]$ResolutionStateLastModified,
    [String]$CreatedByMonitor,
    [String]$ManagedEntitySource,
    [String]$WorkflowId,
    [String]$DataItemCreateTimeLocal,
    [String]$ManagedEntityPath,
    [String]$ManagedEntity,
    [String]$MPElement = "NotPresent"
)

[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12

Import-Module "C:\Program Files\Microsoft System Center 2012 R2\Operations Manager\Powershell\OperationsManager\OperationsManager.psm1"

$alert = Get-SCOMAlert -Id $AlertID
$resolutionStateRaw = Get-SCOMAlert -Id $AlertID | select -expand ResolutionState


$params = @{
    alertId                     = $AlertID
    alertName                   = if($alert.Name) {$alert.Name.ToString()} else {"Not Present"}
    alertDescription            = if($alert.Description) {$alert.Description.ToString()} else {"Not Present"}
    resolutionState             = if($resolutionStateRaw -eq "0") {"New"} elseif($resolutionStateRaw -eq "255") {"Closed"} else {"Not Present"}
    resolutionStateLastModified = if($alert.TimeResolutionStateLastModified) {$alert.TimeResolutionStateLastModified.ToString()} else {"Not Present"}
    priority                    = if($alert.Priority) {$alert.Priority.ToString()} else {"Not Present"}
    owner                       = if($alert.Owner) {$alert.Owner.ToString()} else {"Not Present"}
    repeatCount                 = if($alert.RepeatCount) {$alert.RepeatCount.ToString()} else {"Not Present"}
    severity                    = if($alert.Severity) {$alert.Severity.ToString()} else {"Not Present"}
    category                    = if($alert.Category) {$alert.Category.ToString()} else {"Not Present"}
    createdByMonitor            = $CreatedByMonitor
    managedEntitySource         = $ManagedEntitySource
    workflowId                  = $WorkflowId
    lastModified                = if($alert.LastModified) {$alert.LastModified.ToString()} else {"Not Present"}
    timeRaised                  = if($alert.TimeRaised) {$alert.TimeRaised.ToString()} else {"Not Present"}
    ticketId                    = if($alert.TicketId) {$alert.TicketId.ToString()} else {"Not Present"}
    dataItemCreateTime          = $DataItemCreateTimeLocal
    managedEntityPath           = $ManagedEntityPath
    managedEntityGUID           = $ManagedEntity
    timeAdded                   = if($alert.TimeAdded) {$alert.TimeAdded.ToString()} else {"Not Present"}
    mpElement                   = $MPElement
    customField1                = if($alert.CustomField1) {$alert.CustomField1.ToString()} else {"Not Present"}
    customField2                = if($alert.CustomField2) {$alert.CustomField2.ToString()} else {"Not Present"}
    customField3                = if($alert.CustomField3) {$alert.CustomField3.ToString()} else {"Not Present"}
    customField4                = if($alert.CustomField4) {$alert.CustomField4.ToString()} else {"Not Present"}
    customField5                = if($alert.CustomField5) {$alert.CustomField5.ToString()} else {"Not Present"}
    customField6                = if($alert.CustomField6) {$alert.CustomField6.ToString()} else {"Not Present"}
    customField7                = if($alert.CustomField7) {$alert.CustomField7.ToString()} else {"Not Present"}
    customField8                = if($alert.CustomField8) {$alert.CustomField8.ToString()} else {"Not Present"}
    customField9                = if($alert.CustomField9) {$alert.CustomField9.ToString()} else {"Not Present"}
    customField10               = if($alert.CustomField10) {$alert.CustomField10.ToString()} else {"Not Present"}
}

$json = ConvertTo-Json -InputObject $params

$postFile = "C:\scripts\opsgenie\postResult.txt"

try {
    write-output "Connection to OpsGenie Status" | Out-File $postFile -Append
    Invoke-RestMethod -Method Post -ContentType "application/json" -Body $json -Uri $Url | Out-File $postFile -Append

}

catch {

    out-file -InputObject "Exception Type: $($_.Exception.GetType().FullName) Exception Message: $($_.Exception.Message)" -FilePath $postFile -Append

}
