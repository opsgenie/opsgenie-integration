package com.opsgenie.plugin.model;

import org.codehaus.jackson.annotate.JsonProperty;

import javax.annotation.Nullable;
import javax.validation.constraints.NotNull;
import java.util.Objects;

public class ConnectionSetupDto {

    @NotNull(message = "username cannot be null!")
    @JsonProperty("username")
    private String username;

    @NotNull(message = "password cannot be null!")
    @JsonProperty("password")
    private String password;

    @NotNull(message = "serverId cannot be null!")
    @JsonProperty("serverId")
    private String serverId;

    @Nullable
    @JsonProperty("serverUrl")
    private String serverUrl;

    public String getUsername() {
        return username;
    }

    public ConnectionSetupDto setUsername(String username) {
        this.username = username;
        return this;
    }

    public String getPassword() {
        return password;
    }

    public ConnectionSetupDto setPassword(String password) {
        this.password = password;
        return this;
    }

    public String getServerId() {
        return serverId;
    }

    public ConnectionSetupDto setServerId(String serverId) {
        this.serverId = serverId;
        return this;
    }

    public String getServerUrl() {
        return serverUrl;
    }

    public ConnectionSetupDto setServerUrl(String serverUrl) {
        this.serverUrl = serverUrl;
        return this;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ConnectionSetupDto)) return false;
        ConnectionSetupDto that = (ConnectionSetupDto) o;
        return Objects.equals(getUsername(), that.getUsername()) &&
                Objects.equals(getPassword(), that.getPassword()) &&
                Objects.equals(getServerId(), that.getServerId()) &&
                Objects.equals(getServerUrl(), that.getServerUrl());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getUsername(), getPassword(), getServerId(), getServerUrl());
    }

    @Override
    public String toString() {
        return "ConnectionSetupDto{" +
                "username='" + username + '\'' +
                ", password='" + password + '\'' +
                ", serverId='" + serverId + '\'' +
                ", serverUrl='" + serverUrl + '\'' +
                '}';
    }
}
