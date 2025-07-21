package com.zrlog.admin.business.exception;

import com.zrlog.common.exception.UnknownException;

public class AdminUnknownException extends UnknownException {

    private String message;

    public AdminUnknownException() {
    }

    public AdminUnknownException(String message) {
        this.message = message;
    }

    @Override
    public String getMessage() {
        return message;
    }
}
