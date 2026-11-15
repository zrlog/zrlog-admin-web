package com.zrlog.admin.business.rest.request;

import com.zrlog.admin.business.rest.base.AIWebSiteInfo;

/** Connection selectors are optional so older settings clients remain supported. */
public class AISettingsRequest extends AIWebSiteInfo {
    public String ai_connection_id;
    public String ai_connection_name;
    public String ai_image_connection_id;
    public String ai_image_connection_name;
}
