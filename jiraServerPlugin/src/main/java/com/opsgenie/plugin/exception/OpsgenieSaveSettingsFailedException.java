package com.opsgenie.plugin.exception;

public class OpsgenieSaveSettingsFailedException extends Exception {

    public OpsgenieSaveSettingsFailedException(String message) {
        super(message);
    }

    public OpsgenieSaveSettingsFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}
