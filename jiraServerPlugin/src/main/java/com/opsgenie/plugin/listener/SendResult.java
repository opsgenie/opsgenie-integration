package com.opsgenie.plugin.listener;

public class SendResult {

    private int numberOfAttempts;

    private boolean success;

    private String failReason;

    public int getNumberOfAttempts() {
        return numberOfAttempts;
    }

    public SendResult setNumberOfAttempts(int numberOfAttempts) {
        this.numberOfAttempts = numberOfAttempts;
        return this;
    }

    public boolean isSuccess() {
        return success;
    }

    public SendResult setSuccess(boolean success) {
        this.success = success;
        return this;
    }

    public String getFailReason() {
        return failReason;
    }

    public SendResult setFailReason(String failReason) {
        this.failReason = failReason;
        return this;
    }

    @Override
    public String toString() {
        return "SendResult{" +
                "numberOfAttempts=" + numberOfAttempts +
                ", success=" + success +
                ", failReason='" + failReason + '\'' +
                '}';
    }
}
