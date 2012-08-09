rem @echo off
@echo on

set LAMP_HOME=C:\lamp
set OC_CLASSPATH=%SM_HOME%/classes/*

rem To use the groovy script (to attach object details, etc.) uncomment the following line and comment out the rest of the script.
rem cmd /c %LAMP_HOME%\lamp.bat executeScript --name ogNotifyUsers.groovy

rem Comment out rest of the lines if the groovy script above is being used.
set MESSAGE=Smarts notification: %SM_OBJ_ClassName% %SM_OBJ_InstanceDisplayName% %SM_OBJ_EventName%
set RECIPIENTS=%SM_POBJ_Recipients%
SET DESCRIPTION=%SM_OBJ_EventText%

cmd /c %LAMP_HOME%\lamp.bat createAlert --message %MESSAGE% --recipients %RECIPIENTS% --description %DESCRIPTION% ^
 --source Smarts -DElementName=%SM_OBJ_ElementName% -DDomain=%SM_OBJ_SourceDomainName% -DCount=%SM_OBJ_OccurrenceCount%



