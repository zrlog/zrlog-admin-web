package com.zrlog.admin.business.dto;

import java.io.InputStream;

public class AIStreamResponse {
    private final int statusCode;
    private final String errorBody;
    private final InputStream inputStream;

    public AIStreamResponse(int statusCode, String errorBody, InputStream inputStream) {
        this.statusCode = statusCode;
        this.errorBody = errorBody;
        this.inputStream = inputStream;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public String getErrorBody() {
        return errorBody;
    }

    public InputStream getInputStream() {
        return inputStream;
    }
}