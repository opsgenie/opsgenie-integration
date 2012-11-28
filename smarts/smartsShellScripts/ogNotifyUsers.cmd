@echo off

SET LAMP_HOME=C:\lamp

SET MESSAGE="Smarts notification: %SM_OBJ_ClassName% %SM_OBJ_InstanceDisplayName% %SM_OBJ_EventName%"
SET RECIPIENTS="%SM_POBJ_Recipients%"
SET DESCRIPTION="%SM_OBJ_EventText%"
SET ACTIONS="unacknowledge,take ownership,release ownership"

IF "%SM_POBJ_Recipients%"=="" SET RECIPIENTS="all"

echo Creating Alert
CMD /c %LAMP_HOME%\lamp.bat createAlert --message %MESSAGE% --recipients %RECIPIENTS% ^
--description %DESCRIPTION% --source Smarts --alias "%SM_OBJ_Name%" --actions %ACTIONS% ^
-DElementName="%SM_OBJ_ElementName%" -DDomain="%SM_OBJ_SourceDomainName%" -DCount="%SM_OBJ_OccurrenceCount%" ^
-DDomainName="%SM_SERVER_NAME%"



