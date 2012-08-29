@echo off
REM @echo on

SET LAMP_HOME=C:\lamp
SET OC_CLASSPATH=%SM_HOME%/classes/*

CMD /c %LAMP_HOME%\lamp.bat executeScript --name ogNotifyUsers.groovy
