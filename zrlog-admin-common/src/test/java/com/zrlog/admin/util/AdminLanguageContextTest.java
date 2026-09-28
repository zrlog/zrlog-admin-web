package com.zrlog.admin.util;

import com.zrlog.util.I18nUtil;
import org.junit.Test;
import java.util.concurrent.Executors;
import static org.junit.Assert.*;

public class AdminLanguageContextTest {
    @Test public void restoresNestedAndFailedScopesWithoutMutatingTheirParent() {
        var previous = I18nUtil.threadLocal.get();
        try (var english = AdminLanguageContext.open("en_US")) {
            var parent = I18nUtil.threadLocal.get();
            assertThrows(IllegalStateException.class, () -> {
                try (var chinese = AdminLanguageContext.open("zh_CN")) {
                    assertEquals("zh_CN", I18nUtil.getCurrentLocale());
                    assertEquals("en_US", parent.getLocale());
                    assertEquals("没有权限执行该操作", I18nUtil.getAdminBackendStringFromRes("admin.permission.error"));
                    throw new IllegalStateException("test failure");
                }
            });
            assertSame(parent, I18nUtil.threadLocal.get());
        }
        assertSame(previous, I18nUtil.threadLocal.get());
    }

    @Test public void doesNotLeakLanguageBetweenTasksOnTheSameWorker() throws Exception {
        var executor = Executors.newSingleThreadExecutor();
        try {
            for (String language : new String[]{"en_US", "zh_CN", "en_US"}) {
                assertEquals(language, executor.submit(() -> {
                    assertNull(I18nUtil.threadLocal.get());
                    try (var ignored = AdminLanguageContext.open(language)) {
                        return I18nUtil.getCurrentLocale();
                    }
                }).get());
                assertNull(executor.submit(I18nUtil.threadLocal::get).get());
            }
        } finally { executor.shutdownNow(); }
    }
}
