package com.zrlog.admin.business.rest.response;

import java.util.List;

public class HealthCheckIssueResponse {

    private final String key;
    private final String severity;
    private final long count;
    private final List<String> samples;
    private final String actionUri;

    public HealthCheckIssueResponse(String key, String severity, long count, List<String> samples, String actionUri) {
        this.key = key;
        this.severity = severity;
        this.count = count;
        this.samples = samples;
        this.actionUri = actionUri;
    }

    public String getKey() {
        return key;
    }

    public String getSeverity() {
        return severity;
    }

    public long getCount() {
        return count;
    }

    public List<String> getSamples() {
        return samples;
    }

    public String getActionUri() {
        return actionUri;
    }
}
