package com.opsgenie.plugin.model;

import org.codehaus.jackson.annotate.JsonProperty;

import javax.validation.constraints.NotNull;
import java.util.Objects;

public class ConnectionCloseDto {
    @NotNull(message = "serverId cannot be null!")
    @JsonProperty("serverId")
    private String serverId;

    public String getServerId() {
        return serverId;
    }

    public ConnectionCloseDto setServerId(String serverId) {
        this.serverId = serverId;
        return this;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ConnectionCloseDto)) return false;
        ConnectionCloseDto that = (ConnectionCloseDto) o;
        return Objects.equals(getServerId(), that.getServerId());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getServerId());
    }

    @Override
    public String toString() {
        return "ConnectionCloseDto{" +
                "serverId='" + serverId + '\'' +
                '}';
    }
}
