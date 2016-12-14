This custom script is written for automatically adding an IP (taken from alert.details) to blacklist via Signal Sciences API, when a new alert is created.

Configuration must be filled before running the file.
   * opsGenieAPIKey can be set as one's DefaultAPIKey or one can add a new API and set its apiKey.
   * SIGSCI_EMAIL and SIGSCI_PASSWORD are needed for providing authentication to Signal Sciences. One should enter their Signal Science credentials there.
   * One can find the corpName and siteName from the url of Signal Sciences.
        * Ex: Home Page's URL = "https://dashboard.signalsciences.net/opsgenie/opsgenie.com/?from=-6h" --> corpName : opsgenie, siteName: opsgenie.com
    
    
