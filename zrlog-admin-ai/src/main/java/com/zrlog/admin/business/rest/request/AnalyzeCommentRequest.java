package com.zrlog.admin.business.rest.request;

import com.zrlog.common.Validator;
import com.zrlog.common.exception.ArgsException;

public class AnalyzeCommentRequest implements Validator {
    private String content;
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    @Override public void doValid() {
        if (content == null || content.trim().isEmpty() || content.length() > 5000) {
            throw new ArgsException("content");
        }
    }
}
