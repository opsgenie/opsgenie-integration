# OpsGenie Integration

Identify important conversion workflows in your app, so that when a issue is reported in that workflow via
Crashlytics, this issue will be sent to OpsGenie. This will allow you to react quicker to crashes that impact 
important conversion workflows of your app.

Crashlytics is a crash reporter for Firebase. 

Note: This assumes that you have Crashlytics in Firebase. [Learn more about Crashlytics](https://firebase.google.com/docs/crashlytics/)


## Setting up the sample

 Create and setup the Firebase project:
  1. Create a Firebase project using the [Firebase Developer Console](https://console.firebase.google.com).
  1. Enable Billing on your Firebase the project by switching to the **Blaze** plan, this is currently needed to be able to perform HTTP requests to external services from a Cloud Function.
  1.Include [Crashlytics in your project](https://firebase.google.com/docs/crashlytics/get-started).

 Configuring the sample
  1. Clone or download this repo and open the `firebaseCrashlytics/og-integration` directory.
  1. You must have the Firebase CLI installed. If you don't have it, install it with `npm install -g firebase-tools` and then configure it with `firebase login`.
  1. Configure the CLI locally by using `firebase use --add` and select your project in the list.
  1. Install `npm` dependencies in the functions directory locally, by running: `cd functions; npm install;`
  
 Integrating with OpsGenie
  1. Configure the required environment variables for OpsGenie: `firebase functions:config:set og.webhookurl="<webhook url copied from OG firebaseCrashlytics integration>"`

   
## Deploy and test

 1. Deploy your project using `firebase deploy`
 1. Simulate a test crash. [Instructions](https://firebase.google.com/docs/crashlytics/force-a-crash)
