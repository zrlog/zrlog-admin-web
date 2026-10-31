package com.zrlog.admin.util;

import com.hibegin.http.server.api.HttpResponse;
import com.hibegin.http.server.util.PathUtil;
import com.zrlog.business.plugin.type.StaticSiteType;
import com.zrlog.common.TokenService;
import com.zrlog.common.ZrLogConfig;
import com.zrlog.plugin.IPlugin;
import com.zrlog.plugin.Plugins;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.InputStream;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

public class AdminStaticSiteSsePublisherTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();
    private com.zrlog.common.ZrLogConfig applicationConfig;

    @Test
    public void shouldRunCacheStepsDirectlyWhenStaticSiteIsDisabled() throws Exception {
        CapturedResponse capturedResponse = new CapturedResponse();
        AtomicInteger cacheRuns = new AtomicInteger();
        {
            PathUtil.setRootPath(temporaryFolder.newFolder("zrlog-root").getAbsolutePath());
            applicationConfig = new DisabledStaticConfig();

            com.zrlog.admin.support.ApplicationServicesFixture.publisher(null, applicationConfig).write(
                    capturedResponse.response(),
                    "static-disabled-test",
                    "static-error",
                    List.of(StaticSiteType.BLOG),
                    emitter -> emitter.send("before-cache", Map.of("step", "before")),
                    cacheRuns::incrementAndGet,
                    emitter -> emitter.send("during-cache", Map.of("step", "during")),
                    emitter -> emitter.send("after-cache", Map.of("step", "after"))
            );

            String body = capturedResponse.body();
            assertEquals(1, cacheRuns.get());
            assertTrue(body.contains("event: before-cache"));
            assertTrue(body.contains("event: during-cache"));
            assertTrue(body.contains("event: after-cache"));
            assertTrue(body.contains("event: static-sync-skipped"));
            assertFalse(body.contains("event: static-sync-start"));
            assertFalse(body.contains("event: static-progress"));
        }
    }

    @Test
    public void shouldUseCacheErrorEventAfterBeforeStepSucceeds() throws Exception {
        CapturedResponse capturedResponse = new CapturedResponse();
        {
            PathUtil.setRootPath(temporaryFolder.newFolder("zrlog-root-separated-errors").getAbsolutePath());
            applicationConfig = new DisabledStaticConfig();

            com.zrlog.admin.support.ApplicationServicesFixture.publisher(null, applicationConfig).write(
                    capturedResponse.response(),
                    "static-separated-error-test",
                    "publish-error",
                    "static-error",
                    List.of(StaticSiteType.BLOG),
                    emitter -> emitter.send("article", Map.of("error", 0)),
                    () -> {
                        throw new IllegalStateException("static sync failed");
                    },
                    emitter -> emitter.send("during-cache", Map.of("step", "during")),
                    emitter -> emitter.send("publish-complete", Map.of("error", 0))
            );

            String body = capturedResponse.body();
            assertTrue(body.contains("event: article"));
            assertTrue(body.contains("event: static-error"));
            assertFalse(body.contains("static sync failed"));
            assertTrue(body.contains(com.zrlog.util.I18nUtil.getAdminBackendStringFromRes("admin.staticSite.sync.error")));
            assertFalse(body.contains("event: publish-error"));
            assertFalse(body.contains("event: static-sync-skipped"));
            assertFalse(body.contains("event: publish-complete"));
        }
    }

    @Test
    public void shouldSendStaticErrorAndSkipCompletionWhenCacheRefreshFails() throws Exception {
        CapturedResponse capturedResponse = new CapturedResponse();
        AtomicInteger cacheRuns = new AtomicInteger();
        {
            PathUtil.setRootPath(temporaryFolder.newFolder("zrlog-root-failed-refresh").getAbsolutePath());
            applicationConfig = new DisabledStaticConfig();

            com.zrlog.admin.support.ApplicationServicesFixture.publisher(null, applicationConfig).write(
                    capturedResponse.response(),
                    "static-refresh-failure-test",
                    "static-error",
                    List.of(StaticSiteType.ADMIN),
                    emitter -> emitter.send("response", Map.of("error", 0)),
                    () -> {
                        cacheRuns.incrementAndGet();
                        throw new IllegalStateException("refresh failed");
                    },
                    emitter -> emitter.send("refresh-complete", Map.of("error", 0))
            );

            String body = capturedResponse.body();
            assertEquals(1, cacheRuns.get());
            assertTrue(body.contains("event: response"));
            assertTrue(body.contains("event: static-error"));
            assertFalse(body.contains("refresh failed"));
            assertTrue(body.contains(com.zrlog.util.I18nUtil.getAdminBackendStringFromRes("admin.staticSite.sync.error")));
            assertFalse(body.contains("event: refresh-complete"));
        }
    }

    @Test
    public void asyncCacheWorkAndErrorsUseTheInitiatingLanguage() throws Exception {
        CapturedResponse response = new CapturedResponse();
        var observed = new java.util.concurrent.atomic.AtomicReference<String>();
        var publisher = new AdminStaticSiteSsePublisher(() -> false, new AdminStaticSiteProgress(List::of));
        String previous = com.zrlog.util.I18nUtil.getCurrentLocale();
        try (var ignored = AdminLanguageContext.open("en_US")) {
            publisher.write(response.response(), "english-cache", List.of(StaticSiteType.ADMIN), emitter -> { }, () -> {
                observed.set(com.zrlog.util.I18nUtil.getCurrentLocale());
                throw new IllegalStateException("private provider diagnostic");
            }, emitter -> emitter.send("unexpected-success", true));
        }
        String body = response.body();
        assertEquals("en_US", observed.get());
        assertTrue(body.contains("Static resource sync failed"));
        assertFalse(body.contains("private provider diagnostic"));
        assertFalse(body.contains("unexpected-success"));
        assertEquals(previous, com.zrlog.util.I18nUtil.getCurrentLocale());
    }

    private static class DisabledStaticConfig extends ZrLogConfig {
        DisabledStaticConfig() {
            super(18080, null, "");
        }

        @Override
        protected TokenService initTokenService() {
            return null;
        }

        @Override
        public List<IPlugin> getBasePluginList() {
            return new Plugins();
        }
    }

    private static class CapturedResponse {
        private final Map<String, String> headers = new HashMap<>();
        private final Map<String, String> addedHeaders = new HashMap<>();
        private InputStream written;

        private String body() throws Exception {
            return new String(written.readAllBytes(), StandardCharsets.UTF_8);
        }

        private HttpResponse response() {
            return (HttpResponse) Proxy.newProxyInstance(
                    AdminStaticSiteSsePublisherTest.class.getClassLoader(),
                    new Class[]{HttpResponse.class},
                    (proxy, method, args) -> {
                        if ("getHeader".equals(method.getName())) {
                            return headers;
                        }
                        if ("addHeader".equals(method.getName())) {
                            addedHeaders.put(args[0].toString(), args[1].toString());
                            return null;
                        }
                        if ("write".equals(method.getName())) {
                            written = (InputStream) args[0];
                            return null;
                        }
                        if ("toString".equals(method.getName())) {
                            return "HttpResponseProxy";
                        }
                        return null;
                    });
        }
    }
}
