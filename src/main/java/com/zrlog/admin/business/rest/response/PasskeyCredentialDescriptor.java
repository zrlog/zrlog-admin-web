package com.zrlog.admin.business.rest.response;

import java.util.Collections;
import java.util.List;

public class PasskeyCredentialDescriptor {

    private String id;
    private String type = "public-key";
    private List<String> transports = Collections.emptyList();

    public PasskeyCredentialDescriptor(String id, List<String> transports) {
        this.id = id;
        this.transports = transports;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public List<String> getTransports() {
        return transports;
    }

    public void setTransports(List<String> transports) {
        this.transports = transports;
    }
}
