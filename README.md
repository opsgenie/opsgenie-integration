# OpsGenie Integration

The project includes OpsGenie's specific integration packages:

* icinga
* icinga2
* nagios
* nagiosxi
* netcool
* redmine
* zabbix
* zendesk
* zenoss

## Build

**Requirements:** 

* JDK 1.7 (to compile only)
* A clone of [OpsgenieClient](https://github.com/opsgenie/opsgenieclient) project into same parent directory.

### Tasks

**Available tasks**

* **packageSdk:** Packages sdk as zip file that includes jars, javadoc and third_party under build/distributions
* **package\<Integration_name\>:** Packages the integration specific rpm, deb or zip files.
* **package\<Integration_name\>Zip:** Packages the integration's zip archive only if available
* **package\<Integration_name\>OS:** Packages the integration's rpm and deb archives only if available
* **packageAll:** Packages all zip, rpm and rpm archives for all integrations. Also includes Go based Lamp client tool zip package(if you checked out go based lamp locally).

* **packageLamp:** If you checked out Go Based Lamp to your local computer, you can generate a zip package of it. You can find the Go Based Lamp source code from [here](https://github.com/opsgenie/opsgenie-lamp)

You can run the tasks:

**Unix:** ``./gradlew packageRedmine packageSdk packageNagios``

**Windows:** ``gradlew.bat packageRedmine packageSdk packageNagios``

Or if you want to package all

**Unix:** ``./gradlew packageAll``

**Windows:** ``gradlew.bat packageAll``
