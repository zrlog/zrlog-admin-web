package com.zrlog.admin.business.rest.request;

import com.hibegin.common.util.StringUtils;
import com.zrlog.common.Validator;
import com.zrlog.common.exception.ArgsException;

public class UpdateAIMessageRequest implements Validator {

    private String messageId;
    private String tool;
    private Object payload;

    public String getMessageId() {
        return messageId;
    }

    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }

    public String getTool() {
        return tool;
    }

    public void setTool(String tool) {
        this.tool = tool;
    }

    public Object getPayload() {
        return payload;
    }

    public void setPayload(Object payload) {
        this.payload = payload;
    }

    @Override
    public void doValid() {
        if (StringUtils.isEmpty(messageId)) {
            throw new ArgsException("messageId");
        }
    }
}
