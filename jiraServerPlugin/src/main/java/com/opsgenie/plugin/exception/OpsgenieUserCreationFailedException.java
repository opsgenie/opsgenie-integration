package com.opsgenie.plugin.exception;

public class OpsgenieUserCreationFailedException extends Exception {

    public OpsgenieUserCreationFailedException(String message) {
        super(message);
    }

    public OpsgenieUserCreationFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}
