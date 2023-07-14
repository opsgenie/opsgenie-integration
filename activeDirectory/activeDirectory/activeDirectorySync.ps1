# OpsGenie - Microsoft Active Directory Group and User Synchronization Script

param([string] $command = "listGroups", [string] $groupName = "", [string] $usernames = "")

Import-Module activedirectory

function List-ADGroupMembers {
    $adGroupMembers = Get-ADGroupMember -Identity $groupName | foreach {
        if ($_.objectClass.Equals("user")){
            $_.SamAccountName
        }
    }

    if ($adGroupMembers -ne $null) {
        return ConvertTo-Json @($adGroupMembers) -Compress
    } else {
        return ConvertTo-Json @() -Compress
    }
}

function Get-ADUserDetails {
    $usernamesSplit = $usernames.Split(" ")
    $usersDetailsList = New-Object System.Collections.ArrayList

    $usernamesSplit | foreach {
        $trimmedUserName = $_.Trim()

        $adUser = Get-ADUser -Filter {SamAccountName -eq $trimmedUserName} -Properties EmailAddress, GivenName, Surname, UserPrincipalName
        
        if (!$aduser.EmailAddress){
            $adUserMap = @{
                "fullName" = $adUser.GivenName + " " + $adUser.Surname
                "email" = $adUser.UserPrincipalName
            }
        } else {
            $adUserMap = @{
                "fullName" = $adUser.GivenName + " " + $adUser.Surname
                "email" = $adUser.EmailAddress
            }
        }

        [void] $usersDetailsList.Add($adUserMap)
    }

    return ConvertTo-Json @($usersDetailsList) -Compress
}

if ($command -eq "listGroups") {
    List-ADGroups | Out-Host
} elseif ($command -eq "listGroupMembers") {
    List-ADGroupMembers | Out-Host
} elseif ($command -eq "getUsersDetails") {
    Get-ADUserDetails | Out-Host
} else {
    "Unsupported command [" + $command + "]." | Out-Host
}
