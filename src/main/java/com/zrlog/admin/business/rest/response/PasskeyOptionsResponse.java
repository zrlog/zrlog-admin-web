package com.zrlog.admin.business.rest.response;

public class PasskeyOptionsResponse<T> {

    private String requestId;
    private T options;

    public PasskeyOptionsResponse(String requestId, T options) {
        this.requestId = requestId;
        this.options = options;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public T getOptions() {
        return options;
    }

    public void setOptions(T options) {
        this.options = options;
    }
}
