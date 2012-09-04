#!/bin/sh

# Specify the location of the lamp utility
LAMP_HOME=/opt/lamp

$LAMP_HOME/lamp executeScript --name ogNotifyUsers.groovy
