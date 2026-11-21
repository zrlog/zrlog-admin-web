package com.zrlog.admin.business.rest.request;

import com.google.gson.annotations.SerializedName;
import com.zrlog.admin.business.ai.model.AIProviderType;
import com.zrlog.common.Validator;
import com.zrlog.common.exception.ArgsException;

public class AIConnectionRequest implements Validator {
    public enum Action {
        @SerializedName("save")
        SAVE,
        @SerializedName("activate")
        ACTIVATE,
        @SerializedName("delete")
        DELETE,
        @SerializedName("disableImage")
        DISABLE_IMAGE
    }

    public Action action;
    public String ai_connection_id;
    public String ai_kind;
    public String ai_connection_name;
    public AIProviderType ai_provider;
    public String ai_model;
    public String ai_base_url;
    public String ai_api_key;

    @Override
    public void doValid() {
        if (action == null) throw new ArgsException("action");
    }
}
