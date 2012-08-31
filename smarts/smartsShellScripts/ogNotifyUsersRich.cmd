@echo off

SET LAMP_HOME=C:\lamp
SET LAMP_CLASSPATH=%SM_HOME%/classes/*

CMD /c %LAMP_HOME%\lamp.bat executeScript --name ogNotifyUsers.groovy
