package com.zrlog.admin.util;

import com.hibegin.http.server.util.NativeImageUtils;
import java.util.*;

public final class AccessNativeImageUtils {
    private AccessNativeImageUtils() { }
    public static void reg() {
        NativeImageUtils.gsonNativeAgentByClazz(Arrays.asList(
                com.zrlog.admin.business.rest.request.WebhookConfigRequest.class,
                com.zrlog.admin.business.rest.request.WebhookMessageNoticeRequest.class,
                com.zrlog.admin.business.rest.response.WebhookConfigEntry.class,
                com.zrlog.admin.business.rest.response.WebhookConfigResponse.class,
                com.zrlog.admin.business.rest.response.WebhookTokenResponse.class,
                com.zrlog.admin.business.rest.response.WebhookMessageNoticeEntry.class,
                com.zrlog.admin.business.rest.response.WebhookMessageNoticeCreateResponse.class));
        NativeImageUtils.gsonNativeAgentByClazz(Arrays.asList(com.zrlog.admin.business.knowledge.McpModels.class.getDeclaredClasses()));
        NativeImageUtils.gsonNativeAgentByClazz(Arrays.asList(com.zrlog.admin.business.security.OAuthModels.class.getDeclaredClasses()));
        NativeImageUtils.gsonNativeAgentByClazz(Arrays.asList(com.zrlog.admin.business.security.PersonalTokenModels.class.getDeclaredClasses()));
    }
}
