package com.opsgenie.plugin.model;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement
@XmlAccessorType(XmlAccessType.FIELD)
public class ApiKey {

    @XmlElement
    private String key;

    public String getKey() {
        return key;
    }

    public ApiKey setKey(String key) {
        this.key = key;
        return this;
    }
}
