package com.zrlog.admin.util;

import com.hibegin.http.server.api.HttpResponse;
import com.hibegin.http.server.util.PathUtil;
import com.zrlog.business.plugin.type.StaticSiteType;
import com.zrlog.common.Constants;
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AdminStaticSiteSsePublisherTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void shouldRunCacheStepsDirectlyWhenStaticSiteIsDisabled() throws Exception {
        ZrLogConfig previousConfig = Constants.zrLogConfig;
        CapturedResponse capturedResponse = new CapturedResponse();
        AtomicInteger cacheRuns = new AtomicInteger();
        try {
            PathUtil.setRootPath(temporaryFolder.newFolder("zrlog-root").getAbsolutePath());
            Constants.zrLogConfig = new DisabledStaticConfig();

            AdminStaticSiteSsePublisher.write(
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
            assertFalse(body.contains("event: static-sync-start"));
            assertFalse(body.contains("event: static-progress"));
        } finally {
            Constants.zrLogConfig = previousConfig;
        }
    }

    @Test
    public void shouldSendStaticErrorAndSkipCompletionWhenCacheRefreshFails() throws Exception {
        ZrLogConfig previousConfig = Constants.zrLogConfig;
        CapturedResponse capturedResponse = new CapturedResponse();
        AtomicInteger cacheRuns = new AtomicInteger();
        try {
            PathUtil.setRootPath(temporaryFolder.newFolder("zrlog-root-failed-refresh").getAbsolutePath());
            Constants.zrLogConfig = new DisabledStaticConfig();

            AdminStaticSiteSsePublisher.write(
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
            assertTrue(body.contains("refresh failed"));
            assertFalse(body.contains("event: refresh-complete"));
        } finally {
            Constants.zrLogConfig = previousConfig;
        }
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
