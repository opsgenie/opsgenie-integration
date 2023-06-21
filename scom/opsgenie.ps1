Param (
    [parameter(Mandatory=$true, HelpMessage="You must provide the Url contained in your SCOM Integration's settings in OpsGenie UI.")][String]$Url,
    [String]$AlertID,
    [String]$ResolutionStateLastModified,
    [String]$CreatedByMonitor,
    [String]$ManagedEntitySource,
    [String]$WorkflowId,
    [String]$DataItemCreateTimeLocal,
    [String]$ManagedEntityPath,
    [String]$ManagedEntity,
    [String]$MPElement = "NotPresent",
    [String]$scomModulePath,
    [Switch]$install,
    [Switch]$enableLogging
)

[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12

$logfile = "C:\scripts\opsgenie\log.csv"
$timeStampFormat = "MM-dd-yyyy HH:mm:ss"
$IntegrationLogEntry = ""


function Add-OpsgenieIntegrationLogEntry {    
    Param (
        [parameter(Mandatory=$true, HelpMessage="This function is expecting log level as a string.")][String]$logLevel,
        [parameter(Mandatory=$true, HelpMessage="This function is expecting a log message as a string.")][String]$logMessage
    ) 
    $logMessage=$logMessage.Replace('"','""')
    $timestamp = get-date -Format $timeStampFormat
    $script:IntegrationLogEntry = "$($IntegrationLogEntry)`"$($timestamp)`",`"$($logLevel)`",`"$($AlertID)`",`"$($logMessage)`"`n"
}



if ($enableLogging){if (-not (Test-Path -LiteralPath  $logfile -PathType Leaf)){Write-Output "`"TimeStamp`",`"LogLevel`",`"SCOMAlertId`",`"LogMessage`"" | Out-File $logfile -Append};Add-OpsgenieIntegrationLogEntry  -logLevel "INFO" -logMessage "Opsgenie intergration script has started  for alertId $($alertID)"}
 


Import-Module OperationsManager
if ($?){write-host -ForegroundColor Green "Module has been successfully imported.";echo ""; echo ""} 
else {
    if (!$scomModulePath){
       Write-Host "Attempting to locate SCOM installation path."; echo ""; echo ""
       if (Test-Path "C:\Program Files\Microsoft System Center 2012"){$scomModulePath = "C:\Program Files\Microsoft System Center 2012\Operations Manager\Powershell\OperationsManager\OperationsManager.psm1"; Write-Host -NoNewline "Trying to locate 'C:\Program Files\Microsoft System Center 2012'     :  "; Write-Host -ForegroundColor Green "SCOM 2012 Install path found"}
       if (!$scomModulePath){Write-Host -NoNewline "Trying to locate 'C:\Program Files\Microsoft System Center 2012'     :  "; Write-Host -ForegroundColor Yellow "SCOM 2012 Install path not found"}
       if (Test-Path "C:\Program Files\Microsoft System Center 2012 R2"){$scomModulePath = "C:\Program Files\Microsoft System Center 2012 R2\Operations Manager\Powershell\OperationsManager\OperationsManager.psm1"; Write-Host -NoNewline "Trying to locate 'C:\Program Files\Microsoft System Center 2012 R2'  :  "; Write-Host -ForegroundColor Green "SCOM 2012 R2 Install path found"}
       if (!$scomModulePath){Write-Host -NoNewline "Trying to locate 'C:\Program Files\Microsoft System Center 2012 R2'  :  "; Write-Host -ForegroundColor Yellow "SCOM 2012 R2 Install path not found"}
       if (Test-Path "C:\Program Files\Microsoft System Center 2016"){$scomModulePath = "C:\Program Files\Microsoft System Center 2016\Operations Manager\Powershell\OperationsManager\OperationsManager.psm1"; Write-Host -NoNewline "Trying to locate 'C:\Program Files\Microsoft System Center 2016'     :  "; Write-Host -ForegroundColor Green "SCOM 2016 Install path found"}
       if (!$scomModulePath){Write-Host -NoNewline "Trying to locate 'C:\Program Files\Microsoft System Center 2016'     :  "; Write-Host -ForegroundColor Yellow "SCOM 2016 Install path not found"}
       if (Test-Path "C:\Program Files\Microsoft System Center 2019"){$scomModulePath = "C:\Program Files\Microsoft System Center 2019\Operations Manager\Powershell\OperationsManager\OperationsManager.psm1"; Write-Host -NoNewline "Trying to locate 'C:\Program Files\Microsoft System Center 2019'     :  "; Write-Host -ForegroundColor Green "SCOM 2019 Install path found"}
       if (!$scomModulePath){Write-Host -NoNewline "Trying to locate 'C:\Program Files\Microsoft System Center 2019'     :  "; Write-Host -ForegroundColor Yellow "SCOM 2019 Install path not found"}
       if (Test-Path "C:\Program Files\Microsoft System Center"){$scomModulePath = "C:\Program Files\Microsoft System Center\Operations Manager\Powershell\OperationsManager\OperationsManager.psm1"; Write-Host -NoNewline "Trying to locate 'C:\Program Files\Microsoft System Center'          :  "; Write-Host -ForegroundColor Green "SCOM Install path found"}
       if (!$scomModulePath){Write-Host -NoNewline "Trying to locate 'C:\Program Files\Microsoft System Center'          :  "; Write-Host -ForegroundColor Yellow "SCOM Install path not found"}
    }
    else {Import-Module $scomModulePath}
    echo ""; echo ""; Write-host -NoNewline "Attempting to import Operations Manager Powershell Module            :  "
    Import-Module $scomModulePath
    if ($?){write-host -ForegroundColor Green "Module has been successfully imported.";echo ""; echo ""} else {echo ""; echo ""; write-host -NoNewline -ForegroundColor Yellow "Could not improt module try specifying it using ";write-host -NoNewline -ForegroundColor Cyan "-scomModulePath <path>";write-host -ForegroundColor yellow " when starting this script.";exit}
}


function Install-ogIntegration{
    $PsCommandArgs = "-executionpolicy bypass -File `"C:\scripts\opsgenie\opsgenie.ps1`" -Url `""+$Url+"`" -AlertID `"`$Data[Default='NotPresent']/Context/DataItem/AlertId$`" -CreatedByMonitor `"`$Data[Default='NotPresent']/Context/DataItem/CreatedByMonitor$`" -ManagedEntitySource `"`$Data[Default='NotPresent']/Context/DataItem/ManagedEntityDisplayName$`" -WorkflowId `"`$Data[Default='NotPresent']/Context/DataItem/WorkflowId$`" -DataItemCreateTimeLocal `"`$Data[Default='NotPresent']/Context/DataItem/DataItemCreateTimeLocal$`" -ManagedEntityPath `"`$Data[Default='NotPresent']/Context/DataItem/ManagedEntityPath$`" -ManagedEntity `"`$Data[Default='NotPresent']/Context/DataItem/ManagedEntity$`" -MPElement `"`$MPElement$`""
    Write-host -ForegroundColor Cyan "Creating OpsGenie Notification Channel."
    Add-SCOMNotificationChannel -ApplicationPath ($PSHOME+"\Powershell.exe") -Name "OpsGenie" -Argument $PsCommandArgs -WorkingDirectory $PSHOME
    if ($?){write-host -ForegroundColor Green "OpsGenie Notification Channel has been successfully created.";echo ""; echo ""} else {echo ""; write-host -ForegroundColor Yellow "Failed to create OpsGenie Notification Channel!!!";exit}

    Write-host -ForegroundColor Cyan "Creating OpsGenie Notification Subscriber.";echo ""
    Add-SCOMNotificationSubscriber -name "OpsGenie" -DeviceList "OpsGenie"
    if ($?){write-host -ForegroundColor Green "OpsGenie Notification Subscriber has been successfully created.";echo ""; echo ""} else {echo ""; write-host -ForegroundColor Yellow "Failed to create OpsGenie Notification Subscriber!!!";exit}

    Write-host -ForegroundColor Cyan "Creating OpsGenie Notification Subscription.";echo ""
    $subscriber = Get-SCOMNotificationSubscriber "OpsGenie"
    $channel = Get-SCOMNotificationChannel "OpsGenie"
    Add-SCOMNotificationSubscription -Name ("OpsGenie"+"_Subscription") -Subscriber $subscriber -Channel $channel
    if ($?){write-host -ForegroundColor Green "OpsGenie Notification Subscription has been successfully created.";echo ""} else {echo ""; write-host -ForegroundColor Yellow "Failed to create OpsGenie Notification Subscription!!!";exit}

    Write-Host -ForegroundColor Green "OpsGenie Integration setup has completed successfully"
    exit
}


if ($install){Install-ogIntegration}


try {
    $alert = Get-SCOMAlert -Id $alertID
    if ($enableLogging) {    
    $linebreak ="
"
    if ($enableLogging){Add-OpsgenieIntegrationLogEntry -logLevel "INFO" -logMessage "The following SCOM alert details has been retrieved: alertName='$($alert.name)',alertDescription='$($alert.Description.replace($linebreak," "))',resolutionStateLastModified='$($alert.TimeResolutionStateLastModified)',priority='$($alert.Priority)',owner='$($alert.Owner)',repeatCount='$($alert.RepeatCount)',severity='$($alert.Severity)',category'$($alert.Category)'"}
    }
} 
catch {
    if ($enableLogging){Add-OpsgenieIntegrationLogEntry -logLevel "ERROR" -logMessage "Opsgenie intergration script has failed to retrieved SCOM alert: Exception Type='$($_.Exception.GetType().FullName)',Exception Message='$($_.Exception.Message)'"}
    write-output $IntegrationLogEntry | Out-File $logfile  -Append
    exit
}


$params = @{
    alertId                     = $AlertID
    alertName                   = if($alert.Name) {$alert.Name} else {"Not Present"}
    alertDescription            = if($alert.Description) {$alert.Description} else {"Not Present"}
    resolutionState             = if($alert.ResolutionState -eq 0) {"New"} elseif($alert.ResolutionState -eq 255) {"Closed"} else {"Not Present"}
    resolutionStateLastModified = if($alert.TimeResolutionStateLastModified) {$alert.TimeResolutionStateLastModified.ToString()} else {"Not Present"}
    priority                    = if($alert.Priority) {$alert.Priority.ToString()} else {"Not Present"}
    owner                       = if($alert.Owner) {$alert.Owner} else {"Not Present"}
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
    customField1                = if($alert.CustomField1) {$alert.CustomField1} else {"Not Present"}
    customField2                = if($alert.CustomField2) {$alert.CustomField2} else {"Not Present"}
    customField3                = if($alert.CustomField3) {$alert.CustomField3} else {"Not Present"}
    customField4                = if($alert.CustomField4) {$alert.CustomField4} else {"Not Present"}
    customField5                = if($alert.CustomField5) {$alert.CustomField5} else {"Not Present"}
    customField6                = if($alert.CustomField6) {$alert.CustomField6} else {"Not Present"}
    customField7                = if($alert.CustomField7) {$alert.CustomField7} else {"Not Present"}
    customField8                = if($alert.CustomField8) {$alert.CustomField8} else {"Not Present"}
    customField9                = if($alert.CustomField9) {$alert.CustomField9} else {"Not Present"}
    customField10               = if($alert.CustomField10) {$alert.CustomField10} else {"Not Present"}
}


if ($params.resolutionstate -match "Not Present"){
    if ($enableLogging){Add-OpsgenieIntegrationLogEntry -logLevel "WARNING" -logMessage "The following SCOM alert $($alert.id) will not be sent to Opsgenie as it does not contain resolutionState `"New`" or `"Closed`"."}
}
else {
    $json = ConvertTo-Json -InputObject $params


    try {
        $opsgenie_API_Request = Invoke-RestMethod -Method Post -ContentType "application/json" -Body $json -Uri $Url
        if ($enableLogging){Add-OpsgenieIntegrationLogEntry  -logLevel "INFO" -logMessage "Successfully posted SCOM alert to Opsgenie API the requestId for this call is $($opsgenie_API_Request.requestId)."}
    }

    catch {
         if ($enableLogging){Add-OpsgenieIntegrationLogEntry -logLevel "ERROR" -logMessage "Opsgenie integration script has failed to POST SCOM alert details to the Opsgenie API: Exception Type='$($_.Exception.GetType().FullName)',Exception Message='$($_.Exception.Message)'"}
    }
}
if ($enableLogging){write-output $IntegrationLogEntry | Out-File $logfile -Append}
