# OpsGenie Integration

The project includes OpsGenie's specific integration packages:

* icinga
* nagios
* nagioxxi
* netcool
* opennms
* redmine
* smarts
* splunk
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
* **package<Integration_name>:** Packages the integration specific rpm, deb or zip files.
* **package<Integration_name>Zip:** Packages the integration's zip archive only if available
* **package<Integration_name>OS:** Packages the integration's rpm and deb archives only if available
* **packageAll:** Packages all zip, rpm and rpm archives for all integrations.

You can run the tasks:

**Unix:** ``./gradlew packageRedmine packageSdk packageNagios``

**Windows:** ``gradlew.bat packageRedmine packageSdk packageNagios``

Or if you want to package all

**Unix:** ``./gradlew packageAll``

**Windows:** ``gradlew.bat packageAll``
