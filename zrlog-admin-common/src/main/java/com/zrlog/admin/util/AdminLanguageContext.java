package com.zrlog.admin.util;

import com.zrlog.common.vo.I18nVO;
import com.zrlog.util.I18nUtil;

/** A language scope for authenticated requests and tasks running on reusable threads. */
public final class AdminLanguageContext implements AutoCloseable {
    private final I18nVO previous;

    private AdminLanguageContext(String language) {
        previous = I18nUtil.threadLocal.get();
        I18nVO source = previous == null ? I18nUtil.getI18nVOCache() : previous;
        I18nVO context = new I18nVO();
        context.setAdminBackend(source.getAdminBackend());
        context.setBackend(source.getBackend());
        context.setBlog(source.getBlog());
        context.setAdmin(source.getAdmin());
        context.setLocale("en_US".equals(language) ? "en_US" : "zh_CN");
        context.setLang("en_US".equals(language) ? "en" : "zh");
        I18nUtil.threadLocal.set(context);
    }

    public static AdminLanguageContext open(String language) {
        return new AdminLanguageContext(language);
    }

    @Override public void close() {
        if (previous == null) I18nUtil.removeI18n();
        else I18nUtil.threadLocal.set(previous);
    }
}
