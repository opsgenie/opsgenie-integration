@echo off

SET LAMP_HOME=C:\lamp

CMD /c %LAMP_HOME%\lamp.bat executeScript --name ogNotifyUsers.groovy
