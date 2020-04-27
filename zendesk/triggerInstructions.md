## Zendesk to Opsgenie Trigger Setting
### Create Action:
- Click **Create**.  
- From the **Triggers** page, click **add trigger**.  
- Put **Send Create action to Opsgenie** into **Trigger name**, description is optional.  
- Under **Meet ALL of the following conditions:**, add two conditions as specified below:  
  - Status Is not Solved  
  - Ticket Is Created  
- Under **Actions:**, click **add action**, select **Notify target**, and pick the URL target you added earlier for the integration.  
- Paste the following into the **Message** field:  
>action: create ||  
id: {{ticket.id}} ||  
status: {{ticket.status}} ||  
title: {{ticket.title}} ||  
tags: {{ticket.tags}} ||  
link: {{ticket.link}} ||  
external_id: {{ticket.external_id}} ||  
via: {{ticket.via}} ||  
priority: {{ticket.priority}} ||  
ticket_type: {{ticket.ticket_type}} ||gi  
score: {{ticket.score}} ||  
groupname: {{ticket.group.name}} ||  
due_date: {{ticket.due_date}} ||  
account: {{ticket.account}} ||  
assigneename: {{ticket.assignee.name}} ||  
requestername: {{ticket.requester.name}} ||  
organizationname: {{ticket.organization.name}} ||  
in_business_hours: {{ticket.in_business_hours}} ||  
description: {{ticket.description}}  

### Open Action:  
- Click **Create**.  
- Now click **add trigger** to add the last one.  
- Put "Send Open action to Opsgenie" into **Trigger name**.  
- Under **Meet ANY of the following conditions:**, add two conditions as specified below:  
  - Ticket:Status Is Open  
- Under **Actions:**, select **Notify target** under **Notifications**, and pick the URL target you added earlier for the integration.  
- Paste the following into the **Message** field:  
>action: open ||  
id: {{ticket.id}} ||  
status: {{ticket.status}} ||  
latest_comment : {{ticket.latest_comment_formatted}} ||  
tags: {{ticket.tags}} ||  
external_id: {{ticket.external_id}} 

### Pending Action:  
- Click **Create**.  
- Now click **add trigger** to add the last one.  
- Put "Send Pending action to Opsgenie" into **Trigger name**.  
- Under **Meet ANY of the following conditions:**, add two conditions as specified below:  
  - Ticket:Status Is Pending  
- Under **Actions:**, select **Notify target** under **Notifications**, and pick the URL target you added earlier for the integration.  
- Paste the following into the **Message** field:  
>action: pending ||  
id: {{ticket.id}} ||  
status: {{ticket.status}} ||  
latest_comment : {{ticket.latest_comment_formatted}} ||  
tags: {{ticket.tags}} ||  
external_id: {{ticket.external_id}}  
  
### Resolved/Closed Action:  
- Click **Create**.  
- Now click **add trigger** to add the last one.  
- Put **Send Close/Resolved action to Opsgenie** into **Trigger name**.  
- Under **Meet ANY of the following conditions:**, add two conditions as specified below:  
  - Ticket:Status Is Solved  
  - Ticket:Status Is Closed  
- Under **Actions:**, select **Notify target** under **Notifications**, and pick the URL target you added earlier for the integration.  
- Paste the following into the "Message" field:  
>action: close ||  
id: {{ticket.id}} ||  
status: {{ticket.status}} ||  
latest_comment : {{ticket.latest_comment_formatted}} ||  
tags: {{ticket.tags}} ||  
external_id: {{ticket.external_id}}

### Add Note Action:
- Click **Create**.  
- Now click **add trigger** to add the last one.  
- Put **Send Add Note action to Opsgenie** into **Trigger name**.  
- Under **Meet ALL of the following conditions:**, add two conditions as specified below:  
    - Ticket: Is Updated
    - Ticket:Status Is Not Solved
- Under **Actions:**, select **Notify target** under **Notifications**, and pick the URL target you added earlier for the integration.  
- Paste the following into the "Message" field:  
>action: addnote ||  
id: {{ticket.id}} ||  
status: {{ticket.status}} ||  
latest_comment : {{ticket.latest_comment_formatted}} ||  
tags: {{ticket.tags}} ||  
external_id: {{ticket.external_id}}
