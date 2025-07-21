package com.zrlog.admin.business.rest.base;

import com.hibegin.common.util.StringUtils;
import com.zrlog.admin.business.type.AIProviderType;
import com.zrlog.common.Validator;
import com.zrlog.common.exception.ArgsException;

import java.util.Objects;

/**
 * 与 AI 的 api 服务交互的配置信息
 */
public class AIWebSiteInfo implements Validator {

    private AIProviderType ai_provider;
    private String ai_model;
    private String ai_api_key;
    private String ai_prompt;

    public AIProviderType getAi_provider() {
        return ai_provider;
    }

    public void setAi_provider(AIProviderType ai_provider) {
        this.ai_provider = ai_provider;
    }

    public String getAi_model() {
        return ai_model;
    }

    public void setAi_model(String ai_model) {
        this.ai_model = ai_model;
    }

    public String getAi_api_key() {
        return ai_api_key;
    }

    public void setAi_api_key(String ai_api_key) {
        this.ai_api_key = ai_api_key;
    }

    public String getAi_prompt() {
        return ai_prompt;
    }

    public void setAi_prompt(String ai_prompt) {
        this.ai_prompt = ai_prompt;
    }

    @Override
    public void doValid() {
        if (Objects.isNull(ai_provider)) {
            throw new ArgsException("ai_provider");
        }
        if (StringUtils.isEmpty(ai_model)) {
            throw new ArgsException("ai_model");
        }
        if (StringUtils.isEmpty(ai_api_key)) {
            throw new ArgsException("ai_api_key");
        }
    }
}
