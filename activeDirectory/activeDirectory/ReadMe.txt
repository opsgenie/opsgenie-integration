OpsGenie - Active Directory Synchronization Utility
--------------------------------------------------

What is this utility for?
-------------------------
    - This utility synchronizes your selected Active Directory groups and users with your OpsGenie account.
        It keeps your chosen Active Directory groups synchronized with your OpsGenie teams. It creates OpsGenie
        users from your Active Directory group members which are of type user.

Requirements
------------
    - Active Directory Domain Services

Installation
------------
    - Unzip the ZIP file provided by OpsGenie to a directory.
    - This utility executes a Powershell script to function. So you should allow running unsigned Powershell
        scripts by running the command "Set-ExecutionPolicy -ExecutionPolicy Unrestricted" in a Powershell
        session which ran as Administrator.
    - Windows automatically blocks downloaded executable files. You should unblock the activeDirectorySync.ps1,
        activeDirectorySync.exe and activeDirectorySync_64.exe, if blocked. Otherwise, you might see
        AuthorizationManager check failed messages in the log file.
    - You can unblock the files by right-clicking the file, then selecting "Properties", then checking
        the Unblock option on the right below, and then clicking OK or Apply buttons.

Configuration
-------------
    - Configuration of the utility is managed via the file called
        "activeDirectorySync.conf" in the same directory that you unzipped.

    - You can manage the following properties within this file.

        + ogUrl (OpsGenie URL)
            * You can change the default value of this property, if
                you are in an OpsGenie environment different than US.
                For example you can set https://api.eu.opsgenie.com
                for our EU environment.

        + ogApiKey (OpsGenie API Key)
            * This utility requires an API integration in OpsGenie to work.
                The integration should have the config access and write
                rights. You can also use the API key of your Default API
                integration, instead of creating a new API integration.
            * You should change the [YOUR OPSGENIE API KEY] placeholder
                value to your API integration's API key.

        + logLevel (Log Level)
            * Determines the verbosity of the logs that the utility
                writes.
            * The default value of the property is warning. Other
                supported levels are info, debug, and error.
            * The most verbose level is debug. It will print
                all the logs that are produced by the utility.

        + logPath (Log Path)
            * Determines which path will the log file be created.
            * The default value is empty. Which means it will be placed
                in the same path with the executable. And it will have the name
                "activeDirectorySync.log"

        + groupsToSync (Active Directory Groups to Synchronize with OpsGenie)
            * The names of the Active Directory groups that will be keep
                synchronized with your OpsGenie teams.
            * It's a comma-separated property. You should replace the
                [YOUR ACTIVE DIRECTORY GROUPS] placeholder value with the names of the groups
                that you want to keep synchronized. For example,
                groupsToSync = Domain users, Domain guests, Administrators

        + sendInvitationEmails (Send Invitation Emails to the Users Created by the Utility)
            * It's a true/false value to determine if the utility should send invitation emails
                to the users created by the utility. Sometimes especially if you're in the trial
                process, you might not want to send invitation emails to your actual employees.
            * The default value of the property is true.

        + applyDeletions (Apply the user/team removals in Active Directory to OpsGenie)
            * The utility also supports removing the users/teams that are removed from
                Active Directory. But, because removing is a risky operation to apply
                automatically, the utility has an option not to do it automatically.
                If you do not activate, the utility will only create the users/teams
                in OpsGenie.
            * It's a true/false value.
            * The default value of the property is false.

    - The utility also support using a proxy server for the HTTP requests made by the utility.
        You can do the proxy server configuration within the configuration file. The following
        properties are related to proxy server configuration.

    - The proxy configuration is only enabled if the http.proxy.enabled property is set to true.
        You do not have to change the values of the other properties, if you're not using this feature.
        
        + http.proxy.enabled (Enable/Disable Proxy Server Feature)
            * This property is for determining if the proxy server feature is enabled or not.
            * It's a true/false value.
            * The default value is false.
        
        + http.proxy.port (The Port of Your Proxy Server)
            * This property is for determining which port your proxy server is running.
        
        + http.proxy.host (The Domain of Your Proxy Server)
            * This property is for determining the domain address of your proxy server.
            * The default value is localhost.
        
        + http.proxy.protocol (The Protocol of Your Proxy Server)
            * This property is for determining which protocol your proxy server uses.
            * Supported values are http or https.
            * The default value is http.
        
        + http.proxy.username (The Username for Your Proxy Server)
            * If you're using authentication for your proxy server, you should fill in
                the username of your proxy server user into this property.
            * The default value is admin.
        
        + http.proxy.password (The Password for Your Proxy Server)
            * If you're using authentication for your proxy server, you should fill in
                the password of your proxy server user into this property.
            * If your proxy server user has no password set, you should leave this property
                blank. I.e. http.proxy.password =
            * The default value is [YOUR PROXY SERVER USER PASSWORD] placeholder.

    - For more information please refer to refer to the support document at
        https://support.atlassian.com/opsgenie/docs/integrate-opsgenie-with-microsoft-active-directory/.
