package com.zrlog.admin.business.rest.request;

import com.zrlog.admin.business.rest.base.AIWebSiteInfo;
import com.zrlog.common.Validator;

public class AIGlobalSettingsRequest implements Validator {
    public String ai_prompt;
    public Boolean ai_reasoning_enabled;
    public Integer ai_max_completion_tokens;

    @Override
    public void doValid() {
        AIWebSiteInfo.validateCompletionTokens(ai_max_completion_tokens);
    }
}
