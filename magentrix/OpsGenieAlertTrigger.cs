public class OpsGenieAlertTrigger : ActiveTrigger<Force__Case>
{
	public override void Execute(TransactionContext<Force__Case> trigger)
	{
	    const string OG_API_KEY = "<YOUR OPSGENIE MAGENTRIX INTEGRATION API KEY>";
        const string OG_URL = "https://api.opsgenie.com/v1/json/magentrix?apiKey=" + OG_API_KEY;

	    if (trigger.IsAfter)
        {
            foreach (var rec in trigger.Records)
            {
                var caseId = rec.New.Id;
                var caseDescription = rec.New.Case_Description__c;
                var severity = rec.New.Case_Severity__c;
                var caseNumber = rec.New.CaseNumber;
                var description = rec.New.Description;
                var priority = rec.New.Priority;
                var priorityValue = "";
                var escalatedBy = rec.New.Escalated_By__c;
                var isEscalated = "FALSE";
                
                if(rec.New.IsEscalated == true){
                    isEscalated = "TRUE";
                }
                
                if(escalatedBy == null){
                    escalatedBy = "";
                }

                if (priority != null)
                {
                    priorityValue = priority.ToString();
                }

                var subject = rec.New.Subject;
                var caseType = rec.New.Type;
                var ownerEmail = "";

                if (!string.IsNullOrEmpty(rec.New.Magentrix_Owner__c)) {
                    var ownerIds = trigger.Records.Where(a => a.New != null).Select(a => a.New.Magentrix_Owner__c).ToList();
                    var owners = Database.Query<Force__User>(a => ownerIds.Contains(a.Id)).Select(a => new { a.Id, a.Name, a.Email }).ToListAsAdmin();
                    var owner = owners.FirstOrDefault(a => a.Id == rec.New.Magentrix_Owner__c);
    
                    if (owner != null)
                    {
                        ownerEmail = owner.Email;
                    }
                }
                
                var caseStatus = rec.New.Status;
                var caseStatusValue = "";
                
                if (caseStatus != null)
                {
                    caseStatusValue = caseStatus.ToString();
                }

                var accountName = "";

                if ((rec.New.Account != null) && (!string.IsNullOrEmpty(rec.New.Account.Name))) {
                    accountName = rec.New.Account.Name;
                }

                var customField1 = "";
                var customField2 = "";
                var customField3 = "";
                var customField4 = "";
                var customField5 = "";
                var customField6 = "";
                var customField7 = "";
                var customField8 = "";
                var customField9 = "";
                var customField10 = "";
                
                var assetName = "";
                
                if((rec.New.Asset != null) && (!string.IsNullOrEmpty(rec.New.Asset.Name))) {
                    assetName = rec.New.Asset.Name;
                }

                System.Net.WebClient client = new System.Net.WebClient();
                client.Headers.Add("Content-Type","application/json;charset=UTF-8");

                var map = new Dictionary<string, object>();
                map.Add("caseId", caseId);
                map.Add("caseDescription", caseDescription);
                map.Add("severity", severity);
                map.Add("caseNumber", caseNumber);
                map.Add("description", description);
                map.Add("priority", priority);
                map.Add("subject", subject);
                map.Add("caseType", caseType);
                map.Add("caseStatus", caseStatusValue);
                map.Add("accountName", accountName);
                map.Add("assetName", assetName);
                map.Add("isEscalated", isEscalated);
                map.Add("escalatedBy", escalatedBy);
                map.Add("customField1", customField1);
                map.Add("customField2", customField2);
                map.Add("customField3", customField3);
                map.Add("customField4", customField4);
                map.Add("customField5", customField5);
                map.Add("customField6", customField6);
                map.Add("customField7", customField7);
                map.Add("customField8", customField8);
                map.Add("customField9", customField9);
                map.Add("customField10", customField10);

                var postData = JsonHelper.ToJson(map);
                client.UploadString(OG_URL, postData);
            }
        }
	}	
}
