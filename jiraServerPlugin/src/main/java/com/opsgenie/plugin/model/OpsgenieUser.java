package com.opsgenie.plugin.model;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class OpsgenieUser {

    private String username;

    private String password;

    public OpsgenieUser() {
    }

    public OpsgenieUser(String username, String password) {
        this.username = username;
        this.password = password;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public OpsgenieUser setUsername(String username) {
        this.username = username;
        return this;
    }

    public OpsgenieUser setPassword(String password) {
        this.password = password;
        return this;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("username", this.username);
        map.put("password", this.password);
        return map;
    }

    public static OpsgenieUser fromMap(Map<String, Object> userMap) {
        return new OpsgenieUser(((String) userMap.get("username")), ((String) userMap.get("password")));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof OpsgenieUser)) return false;
        OpsgenieUser that = (OpsgenieUser) o;
        return Objects.equals(getUsername(), that.getUsername()) &&
                Objects.equals(getPassword(), that.getPassword());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getUsername(), getPassword());
    }

    @Override
    public String toString() {
        return "OpsgenieUser{" +
                "username='" + username + '\'' +
                ", password='" + password + '\'' +
                '}';
    }
}
