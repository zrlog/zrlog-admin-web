package com.zrlog.admin.business.rest.response;

import com.zrlog.admin.business.ai.model.AIProviderType;

/** Public settings metadata deliberately has no credential field. */
public class AIConnectionResponse {
    public String ai_connection_id;
    public String ai_connection_name;
    public String ai_kind;
    public AIProviderType ai_provider;
    public String ai_model;
    public String ai_base_url;
    public boolean hasAiApiKey;
}
