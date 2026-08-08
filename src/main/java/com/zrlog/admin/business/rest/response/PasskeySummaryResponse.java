package com.zrlog.admin.business.rest.response;

import java.util.Collections;
import java.util.List;

public class PasskeySummaryResponse {

    private long id;
    private String name;
    private long createdAt;
    private Long lastUsedAt;
    private List<String> transports = Collections.emptyList();

    public PasskeySummaryResponse(long id, String name, long createdAt, Long lastUsedAt, List<String> transports) {
        this.id = id;
        this.name = name;
        this.createdAt = createdAt;
        this.lastUsedAt = lastUsedAt;
        this.transports = transports;
    }

    public long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public Long getLastUsedAt() {
        return lastUsedAt;
    }

    public List<String> getTransports() {
        return transports;
    }
}
