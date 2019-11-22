package com.opsgenie.plugin.listener;

import java.util.Arrays;

public enum SendMethod {

    HTTP("http");

    private String method;

    SendMethod(String method) {
        this.method = method;
    }

    public String getMethod() {
        return method;
    }

    SendMethod fromValue(String method) {
        return Arrays.stream(SendMethod.values()).filter(sendMethod -> sendMethod.getMethod().equalsIgnoreCase(method))
                .findFirst()
                .orElse(HTTP);
    }
}
