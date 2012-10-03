@echo off

SET LAMP_HOME=C:\lamp

SET SUBJECT="%1"
SET TEXT_MESSAGE="%2"
SET NODEID="%3"

CMD /c %LAMP_HOME%\lamp.bat executeScript --name ogCreateAlert.groovy -Dsubject="%SUBJECT%" -DtextMessage="%TEXT_MESSAGE%" -DnodeId="%NODEID%"