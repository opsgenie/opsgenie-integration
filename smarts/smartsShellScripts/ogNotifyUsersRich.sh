#!/bin/sh

# Specify the location of the lamp utility
LAMP_HOME=/opt/lamp

export CLASSPATH=$CLASSPATH:$SM_HOME/classes/*

$LAMP_HOME/lamp executeScript --name ogNotifyUsers.groovy
