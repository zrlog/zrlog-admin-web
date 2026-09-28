package com.zrlog.admin.business.knowledge;

import com.zrlog.util.I18nUtil;
import java.util.Map;
import java.util.Objects;

/** Explicit request language; never changes the shared HTTP thread's locale. */
public final class KnowledgeMessages {
    private final Map<String, Object> messages;

    public KnowledgeMessages(String language) {
        String locale = "zh_CN".equals(language) ? "zh_CN" : "en_US";
        messages = I18nUtil.getI18nVOCache().getAdminBackend().get(locale);
    }

    public String get(String key) {
        return Objects.requireNonNull(messages.get(key), "Missing knowledge translation: " + key).toString();
    }
}
