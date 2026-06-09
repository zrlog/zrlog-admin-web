package com.zrlog.admin.business.rest.request;

import com.zrlog.common.Validator;

public class OptimizeAiPromptRequest implements Validator {

    private String prompt;

    public String getPrompt() {
        return prompt;
    }

    public void setPrompt(String prompt) {
        this.prompt = prompt;
    }

    @Override
    public void doValid() {
    }
}
