package com.opsgenie.plugin.model;

import org.codehaus.jackson.annotate.JsonProperty;

import javax.annotation.Nullable;
import javax.validation.constraints.NotNull;
import java.util.Objects;

public class ConnectionUpdateDto {

    @NotNull(message = "serverId cannot be null!")
    @JsonProperty("serverId")
    private String serverId;

    @Nullable
    @JsonProperty("serverUrl")
    private String serverUrl;

    @Nullable
    @JsonProperty("username")
    private String username;

    @Nullable
    @JsonProperty("password")
    private String password;

    public String getServerId() {
        return serverId;
    }

    public ConnectionUpdateDto setServerId(String serverId) {
        this.serverId = serverId;
        return this;
    }

    public String getServerUrl() {
        return serverUrl;
    }

    public ConnectionUpdateDto setServerUrl(String serverUrl) {
        this.serverUrl = serverUrl;
        return this;
    }

    public String getUsername() {
        return username;
    }

    public ConnectionUpdateDto setUsername(String username) {
        this.username = username;
        return this;
    }

    public String getPassword() {
        return password;
    }

    public ConnectionUpdateDto setPassword(String password) {
        this.password = password;
        return this;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ConnectionUpdateDto)) return false;
        ConnectionUpdateDto that = (ConnectionUpdateDto) o;
        return Objects.equals(getServerId(), that.getServerId()) &&
                Objects.equals(getUsername(), that.getUsername()) &&
                Objects.equals(getPassword(), that.getPassword()) &&
                Objects.equals(getServerUrl(), that.getServerUrl());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getServerId(), getServerUrl());
    }

    @Override
    public String toString() {
        return "ConnectionUpdateDto{" +
                "serverId='" + serverId + '\'' +
                ", serverUrl='" + serverUrl + '\'' +
                ", username='" + username + '\'' +
                ", password='" + password + '\'' +
                '}';
    }
}
