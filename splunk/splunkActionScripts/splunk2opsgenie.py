#!/opt/splunk/bin/python
import sys
import urllib2
import json
# This py file is a stand-alone integration, it directly works with Splunk & OpsGenie
# store params passed Splunk as optional alert properties
details = {
   "numberOfEvents":sys.argv[1],
   "terms":sys.argv[2],
   "query":sys.argv[3],
   "url":sys.argv[6],
   "reason":sys.argv[5],
   "searchName":sys.argv[4]
}
# populate the map that contains alert properties
alertProps = {
   "apiKey":"your-opsgenie-api-key",
   "message":sys.argv[5],
   "recipients":"web_operations",
   "source":"Splunk",
   "details":details
}
jdata = json.dumps(alertProps)
response = urllib2.urlopen("https://api.opsgenie.com/v1/json/alert", jdata)