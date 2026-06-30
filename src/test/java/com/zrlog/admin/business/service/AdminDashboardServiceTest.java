package com.zrlog.admin.business.service;

import com.hibegin.common.util.http.handle.CloseResponseHandle;
import com.hibegin.http.HttpMethod;
import com.zrlog.admin.business.rest.request.AdminDashboardCardRequest;
import com.zrlog.admin.business.rest.request.AdminDashboardConfigRequest;
import com.zrlog.admin.business.rest.response.AdminDashboardCardConfigResponse;
import com.zrlog.admin.business.rest.response.AdminDashboardCardResponse;
import com.zrlog.admin.business.rest.response.AdminDashboardConfigResponse;
import com.zrlog.admin.support.InMemoryZrLogDatabase;
import com.zrlog.business.plugin.PluginCorePlugin;
import com.zrlog.common.Constants;
import com.zrlog.common.vo.AdminTokenVO;
import com.zrlog.plugin.IPlugin;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.net.ssl.SSLSession;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class AdminDashboardServiceTest {

    @Test
    public void shouldFillSurfacePathsFromSafePluginName() {
        AdminDashboardCardResponse panel = new AdminDashboardCardResponse();
        panel.setType("surface");
        panel.setPluginName(" reminder ");

        AdminDashboardCardResponse normalizedPanel = AdminDashboardService.normalizePanel(panel);

        assertSame(panel, normalizedPanel);
        assertEquals("reminder", normalizedPanel.getId());
        assertEquals("reminder", normalizedPanel.getPluginName());
        assertEquals("/admin/plugins/reminder/surface", normalizedPanel.getSurfaceUrl());
        assertEquals("/admin/plugins/reminder/surfaceAction", normalizedPanel.getActionUrl());
    }

    @Test
    public void shouldKeepAdvancedInternalSurfacePaths() {
        AdminDashboardCardResponse panel = new AdminDashboardCardResponse();
        panel.setType("surface");
        panel.setId("custom-panel");
        panel.setSurfaceUrl(" /admin/plugins/reminder/surface?scope=dashboard ");
        panel.setActionUrl("/admin/plugins/reminder/surfaceAction");

        AdminDashboardCardResponse normalizedPanel = AdminDashboardService.normalizePanel(panel);

        assertSame(panel, normalizedPanel);
        assertEquals("/admin/plugins/reminder/surface?scope=dashboard", normalizedPanel.getSurfaceUrl());
        assertEquals("/admin/plugins/reminder/surfaceAction", normalizedPanel.getActionUrl());
    }

    @Test
    public void shouldDropExternalSurfaceUrls() {
        AdminDashboardCardResponse panel = new AdminDashboardCardResponse();
        panel.setType("surface");
        panel.setId("external-panel");
        panel.setSurfaceUrl("https://example.com/surface");
        panel.setActionUrl("//example.com/surfaceAction");

        assertNull(AdminDashboardService.normalizePanel(panel));
    }

    @Test
    public void shouldDropPluginNameContainingPathSeparators() {
        AdminDashboardCardResponse panel = new AdminDashboardCardResponse();
        panel.setType("surface");
        panel.setPluginName("../reminder");

        assertNull(AdminDashboardService.normalizePanel(panel));
    }

    @Test
    public void shouldNormalizeInternalPluginViewPath() {
        AdminDashboardCardResponse panel = new AdminDashboardCardResponse();
        panel.setType("view");
        panel.setPluginName("reminder");
        panel.setViewUrl(" /settings/index?tab=base ");

        AdminDashboardCardResponse normalizedPanel = AdminDashboardService.normalizePanel(panel);

        assertSame(panel, normalizedPanel);
        assertEquals("settings/index?tab=base", normalizedPanel.getViewUrl());
    }

    @Test
    public void shouldKeepExternalHttpPluginViewUrl() {
        AdminDashboardCardResponse panel = new AdminDashboardCardResponse();
        panel.setType("view");
        panel.setViewUrl("https://example.com/dashboard");

        AdminDashboardCardResponse normalizedPanel = AdminDashboardService.normalizePanel(panel);

        assertSame(panel, normalizedPanel);
        assertEquals("https://example.com/dashboard", normalizedPanel.getViewUrl());
    }

    @Test
    public void shouldDefaultUnsafePluginViewPath() {
        AdminDashboardCardResponse panel = new AdminDashboardCardResponse();
        panel.setType("view");
        panel.setPluginName("reminder");
        panel.setViewUrl("../admin");

        AdminDashboardCardResponse normalizedPanel = AdminDashboardService.normalizePanel(panel);

        assertSame(panel, normalizedPanel);
        assertEquals("index", normalizedPanel.getViewUrl());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void shouldConvertDashboardCardRequestsToResponses() throws Exception {
        AdminDashboardService service = new AdminDashboardService();
        AdminDashboardCardRequest request = new AdminDashboardCardRequest();
        request.setKind("plugin");
        request.setId("reminder");
        request.setEnabled(false);
        request.setSort(12);
        request.setType("surface");
        request.setPluginName("reminder");
        request.setSurfaceUrl("/admin/plugins/reminder/surface");
        request.setActionUrl("/admin/plugins/reminder/surfaceAction");
        request.setViewUrl("index");
        request.setMaxItems(3);
        request.setHeight(260);
        request.setOrder(7);

        List<AdminDashboardCardResponse> cards = (List<AdminDashboardCardResponse>) invoke(service,
                "toCardResponses", List.of(request));

        assertEquals(1, cards.size());
        AdminDashboardCardResponse card = cards.get(0);
        assertEquals("plugin", card.getKind());
        assertEquals("reminder", card.getId());
        assertEquals(false, card.getEnabled());
        assertEquals(Integer.valueOf(12), card.getSort());
        assertEquals("surface", card.getType());
        assertEquals("reminder", card.getPluginName());
        assertEquals("/admin/plugins/reminder/surface", card.getSurfaceUrl());
        assertEquals("/admin/plugins/reminder/surfaceAction", card.getActionUrl());
        assertEquals("index", card.getViewUrl());
        assertEquals(Integer.valueOf(3), card.getMaxItems());
        assertEquals(Integer.valueOf(260), card.getHeight());
        assertEquals(Integer.valueOf(7), card.getOrder());
        assertEquals(List.of(), invoke(service, "toCardResponses", new Object[]{null}));
    }

    @Test
    public void shouldFillDashboardRefreshConfigWithDefaults() throws Exception {
        AdminDashboardService service = new AdminDashboardService();
        AdminDashboardConfigResponse target = new AdminDashboardConfigResponse();
        AdminDashboardConfigResponse source = new AdminDashboardConfigResponse();
        source.setAutoRefreshEnabled(true);
        source.setAutoRefreshIntervalSeconds(5);
        Method responseMethod = method("fillRefreshConfig",
                AdminDashboardConfigResponse.class, AdminDashboardConfigResponse.class);
        responseMethod.invoke(service, target, source);

        AdminDashboardConfigRequest request = new AdminDashboardConfigRequest();
        request.setAutoRefreshEnabled(true);
        request.setAutoRefreshIntervalSeconds(15);
        AdminDashboardConfigResponse requestTarget = new AdminDashboardConfigResponse();
        Method requestMethod = method("fillRefreshConfig",
                AdminDashboardConfigResponse.class, AdminDashboardConfigRequest.class);
        requestMethod.invoke(service, requestTarget, request);

        assertEquals(true, target.getAutoRefreshEnabled());
        assertEquals(Integer.valueOf(60), target.getAutoRefreshIntervalSeconds());
        assertEquals(true, requestTarget.getAutoRefreshEnabled());
        assertEquals(Integer.valueOf(15), requestTarget.getAutoRefreshIntervalSeconds());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void shouldMergeDefaultDashboardCardsWithSavedOverrides() throws Exception {
        AdminDashboardService service = new AdminDashboardService();
        AdminDashboardCardConfigResponse welcome = cardConfig("welcome", false, 90);
        AdminDashboardCardConfigResponse localDraft = cardConfig("localDraft", false, 5);
        AdminDashboardCardConfigResponse unknown = cardConfig("unknown", false, 1);
        AdminDashboardCardConfigResponse missing = cardConfig(null, false, 1);

        List<AdminDashboardCardConfigResponse> cards = (List<AdminDashboardCardConfigResponse>) invoke(service,
                "mergeCards", List.of(welcome, localDraft, unknown, missing));

        assertEquals(7, cards.size());
        assertEquals("localDraft", cards.get(0).getId());
        assertEquals(false, cards.get(0).getEnabled());
        assertEquals(Integer.valueOf(5), cards.get(0).getSort());
        AdminDashboardCardConfigResponse mergedWelcome = findCard(cards, "welcome");
        assertEquals(true, mergedWelcome.getEnabled());
        assertEquals(Integer.valueOf(90), mergedWelcome.getSort());
        assertNull(findCard(cards, "unknown"));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void shouldMergePluginPanelsAndFillMissingStandardFields() throws Exception {
        AdminDashboardService service = new AdminDashboardService();
        AdminDashboardCardResponse standard = panel("reminder", "surface", 20);
        standard.setTitle("Reminder");
        standard.setSurfaceUrl("/admin/plugins/reminder/surface");
        standard.setActionUrl("/admin/plugins/reminder/surfaceAction");
        AdminDashboardCardResponse saved = new AdminDashboardCardResponse();
        saved.setId("reminder");
        saved.setPluginName("reminder");
        saved.setEnabled(false);
        saved.setSort(5);
        AdminDashboardCardResponse customView = new AdminDashboardCardResponse();
        customView.setType("view");
        customView.setPluginName("backup");
        customView.setViewUrl("settings");
        customView.setSort(15);

        List<AdminDashboardCardResponse> panels = (List<AdminDashboardCardResponse>) invoke(service,
                "mergePluginPanels", List.of(saved, customView), List.of(standard));

        assertEquals(2, panels.size());
        assertEquals("reminder", panels.get(0).getId());
        assertEquals(false, panels.get(0).getEnabled());
        assertEquals("Reminder", panels.get(0).getTitle());
        assertEquals("/admin/plugins/reminder/surface", panels.get(0).getSurfaceUrl());
        assertEquals("/admin/plugins/reminder/surfaceAction", panels.get(0).getActionUrl());
        assertEquals("backup", panels.get(1).getId());
        assertEquals("view", panels.get(1).getType());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void shouldExtractItemsClearRuntimeDataAndBuildDashboardItems() throws Exception {
        AdminDashboardService service = new AdminDashboardService();
        AdminDashboardCardResponse cardItem = new AdminDashboardCardResponse();
        cardItem.setKind("card");
        cardItem.setId("welcome");
        cardItem.setEnabled(true);
        cardItem.setSort(20);
        cardItem.setTitle("Welcome");
        cardItem.setData(Map.of("runtime", true));
        AdminDashboardCardResponse pluginItem = panel("reminder", "surface", 10);
        pluginItem.setKind("plugin");
        pluginItem.setData(Map.of("value", 1));
        pluginItem.setError("error");
        pluginItem.setSurfaceLoaded(true);

        List<AdminDashboardCardConfigResponse> cards = (List<AdminDashboardCardConfigResponse>) invoke(service,
                "extractCardItems", List.of(cardItem, pluginItem));
        List<AdminDashboardCardResponse> plugins = (List<AdminDashboardCardResponse>) invoke(service,
                "extractPluginItems", List.of(cardItem, pluginItem));
        invoke(service, "clearRuntimeData", List.of(pluginItem));
        List<AdminDashboardCardResponse> items = (List<AdminDashboardCardResponse>) invoke(service, "toItems",
                cards, plugins);

        assertEquals(1, cards.size());
        assertEquals("welcome", cards.get(0).getId());
        assertEquals(Map.of("runtime", true), cards.get(0).getData());
        assertEquals(1, plugins.size());
        assertEquals("reminder", plugins.get(0).getId());
        assertNull(pluginItem.getData());
        assertNull(pluginItem.getError());
        assertNull(pluginItem.getSurfaceLoaded());
        assertEquals("plugin", items.get(0).getKind());
        assertEquals("reminder", items.get(0).getId());
        assertEquals("card", items.get(1).getKind());
    }

    @Test
    public void shouldNormalizePanelDefaultsAndDropInvalidPanels() {
        assertNull(AdminDashboardService.normalizePanel(null));
        AdminDashboardCardResponse invalidType = new AdminDashboardCardResponse();
        invalidType.setId("bad");
        invalidType.setType("chart");
        assertNull(AdminDashboardService.normalizePanel(invalidType));

        AdminDashboardCardResponse view = new AdminDashboardCardResponse();
        view.setType("view");
        view.setPluginName("reminder");
        view.setOrder(22);

        AdminDashboardCardResponse normalized = AdminDashboardService.normalizePanel(view);

        assertSame(view, normalized);
        assertEquals("reminder", normalized.getId());
        assertEquals(true, normalized.getEnabled());
        assertEquals(Integer.valueOf(5), normalized.getMaxItems());
        assertEquals(Integer.valueOf(22), normalized.getSort());
        assertEquals(Integer.valueOf(360), normalized.getHeight());
        assertEquals("index", normalized.getViewUrl());
    }

    @Test
    public void shouldValidatePluginMetadataEntriesAndUrls() throws Exception {
        AdminDashboardService service = new AdminDashboardService();

        assertTrue((Boolean) invoke(service, "hasEntry", List.of("/surface", "other"), "surface"));
        assertTrue((Boolean) invoke(service, "hasEntry", List.of("surfaceAction?scope=dashboard"), "surfaceAction"));
        assertFalse((Boolean) invoke(service, "hasEntry", List.of("other"), "surface"));
        assertFalse((Boolean) invoke(service, "hasEntry", "surface", "surface"));
        assertTrue((Boolean) invoke(service, "hasSurfaceMetadata",
                Map.of("paths", List.of("surface"), "actions", List.of())));
        assertFalse((Boolean) invoke(service, "hasSurfaceMetadata",
                Map.of("paths", List.of("index"), "actions", List.of("other"))));
        assertEquals("/admin/plugins/reminder/surface", invokeStatic("pluginUrl", "reminder", "surface"));
        assertEquals("reminder", invokeStatic("normalizePluginName", " reminder "));
        assertNull(invokeStatic("normalizePluginName", "../reminder"));
        assertEquals("/admin/plugins/reminder/surface?x=1",
                invokeStatic("normalizePluginContextPath", "/admin/plugins/reminder/surface?x=1"));
        assertNull(invokeStatic("normalizePluginContextPath", "//example.com/surface"));
        assertEquals("settings/index?tab=base", invokeStatic("normalizePluginViewUrl",
                "/settings/index?tab=base"));
        assertNull(invokeStatic("normalizePluginViewUrl", "ftp://example.com/file"));
        assertTrue((Boolean) invokeStatic("hasParentPathSegment", "a/../b"));
        assertFalse((Boolean) invokeStatic("hasParentPathSegment", "a/b"));
    }

    @Test
    public void shouldExposeSurfaceLoadResultValues() throws Exception {
        Class<?> type = Class.forName("com.zrlog.admin.business.service.AdminDashboardService$SurfaceLoadResult");
        Method dataMethod = type.getDeclaredMethod("data", Object.class);
        Method errorMethod = type.getDeclaredMethod("error", String.class);
        dataMethod.setAccessible(true);
        errorMethod.setAccessible(true);
        Object data = dataMethod.invoke(null, Map.of("title", "Plugin"));
        Object error = errorMethod.invoke(null, "failed");

        assertEquals(Map.of("title", "Plugin"), field(data, "data"));
        assertNull(field(data, "error"));
        assertEquals(true, field(data, "loaded"));
        assertNull(field(error, "data"));
        assertEquals("failed", field(error, "error"));
        assertEquals(false, field(error, "loaded"));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void shouldSaveConfigLoadPluginPanelsAndPreloadSurfaceThroughRealWebsiteKv() throws Exception {
        try (InMemoryZrLogDatabase db = InMemoryZrLogDatabase.open()) {
            FakePluginCorePlugin plugin = new FakePluginCorePlugin();
            Constants.zrLogConfig.getAllPlugins().add(plugin);
            AdminDashboardService service = new AdminDashboardService();
            AdminDashboardConfigRequest request = new AdminDashboardConfigRequest();
            request.setAutoRefreshEnabled(true);
            request.setAutoRefreshIntervalSeconds(20);
            AdminDashboardCardRequest pluginCard = new AdminDashboardCardRequest();
            pluginCard.setKind("plugin");
            pluginCard.setPluginName("reminder");
            pluginCard.setType("surface");
            pluginCard.setEnabled(true);
            pluginCard.setSort(3);
            request.setCards(List.of(pluginCard));

            AdminDashboardConfigResponse config = service.saveConfig(request, null, token());
            AdminDashboardCardResponse pluginItem = findDashboardItem(config.getCards(), "reminder");
            Map<String, Object> saved = db.queryOne("select value from website where name=?",
                    AdminDashboardService.DASHBOARD_CONFIG_KEY);

            assertNotNull(pluginItem);
            assertEquals("plugin", pluginItem.getKind());
            assertEquals("surface", pluginItem.getType());
            assertEquals(Boolean.TRUE, pluginItem.getSurfaceLoaded());
            assertEquals("Reminder Surface", pluginItem.getTitle());
            assertEquals(List.of("/api/plugins", "/api/plugins", "/admin/plugins/reminder/surface"),
                    plugin.requestedUris);
            assertEquals("Reminder Surface", ((Map<String, Object>) pluginItem.getData()).get("title"));
            assertNotNull(saved);
            assertTrue(String.valueOf(saved.get("value")).contains("\"pluginName\":\"reminder\""));
            assertFalse(String.valueOf(saved.get("value")).contains("Reminder Surface"));
        }
    }

    @Test
    public void shouldFallbackToDefaultsAndSurfaceErrorWhenSavedConfigCannotLoadPlugin() throws Exception {
        try (InMemoryZrLogDatabase db = InMemoryZrLogDatabase.open()) {
            db.putWebsite(AdminDashboardService.DASHBOARD_CONFIG_KEY,
                    "{\"cards\":[{\"kind\":\"plugin\",\"id\":\"missing\",\"enabled\":true,\"sort\":1,"
                            + "\"type\":\"surface\",\"surfaceUrl\":\"/admin/plugins/missing/surface\","
                            + "\"actionUrl\":\"/admin/plugins/missing/surfaceAction\"}],"
                            + "\"autoRefreshEnabled\":true,\"autoRefreshIntervalSeconds\":5}");
            AdminDashboardConfigResponse config = new AdminDashboardService().getConfig(null, token(), true);
            AdminDashboardCardResponse pluginItem = findDashboardItem(config.getCards(), "missing");

            assertEquals(Boolean.TRUE, config.getAutoRefreshEnabled());
            assertEquals(Integer.valueOf(60), config.getAutoRefreshIntervalSeconds());
            assertNotNull(findDashboardItem(config.getCards(), "welcome"));
            assertNotNull(pluginItem);
            assertEquals(Boolean.FALSE, pluginItem.getSurfaceLoaded());
            assertNotNull(pluginItem.getError());

            db.putWebsite(AdminDashboardService.DASHBOARD_CONFIG_KEY, "{bad json");
            AdminDashboardConfigResponse fallback = new AdminDashboardService().getConfig(null, token());

            assertEquals(7, fallback.getCards().size());
            assertNotNull(findDashboardItem(fallback.getCards(), "welcome"));
        }
    }

    private static AdminDashboardCardConfigResponse cardConfig(String id, Boolean enabled, Integer sort) {
        AdminDashboardCardConfigResponse card = new AdminDashboardCardConfigResponse();
        card.setId(id);
        card.setEnabled(enabled);
        card.setSort(sort);
        return card;
    }

    private static AdminDashboardCardConfigResponse findCard(List<AdminDashboardCardConfigResponse> cards, String id) {
        for (AdminDashboardCardConfigResponse card : cards) {
            if (id.equals(card.getId())) {
                return card;
            }
        }
        return null;
    }

    private static AdminDashboardCardResponse panel(String id, String type, Integer sort) {
        AdminDashboardCardResponse panel = new AdminDashboardCardResponse();
        panel.setKind("plugin");
        panel.setId(id);
        panel.setType(type);
        panel.setPluginName(id);
        panel.setSort(sort);
        panel.setEnabled(true);
        return panel;
    }

    private static AdminDashboardCardResponse findDashboardItem(List<AdminDashboardCardResponse> cards, String id) {
        for (AdminDashboardCardResponse card : cards) {
            if (id.equals(card.getId())) {
                return card;
            }
        }
        return null;
    }

    private static AdminTokenVO token() {
        AdminTokenVO token = new AdminTokenVO();
        token.setUserId(1);
        token.setProtocol("http");
        token.setSessionId("dashboard-session");
        return token;
    }

    private static CloseResponseHandle closeResponse(String json) {
        CloseResponseHandle handle = new CloseResponseHandle();
        handle.setT(response(json));
        return handle;
    }

    private static HttpResponse<InputStream> response(String body) {
        return new HttpResponse<>() {
            @Override
            public int statusCode() {
                return 200;
            }

            @Override
            public java.net.http.HttpRequest request() {
                return java.net.http.HttpRequest.newBuilder(URI.create("https://example.com")).build();
            }

            @Override
            public Optional<HttpResponse<InputStream>> previousResponse() {
                return Optional.empty();
            }

            @Override
            public HttpHeaders headers() {
                return HttpHeaders.of(Map.of("content-type", List.of("application/json")), (name, value) -> true);
            }

            @Override
            public InputStream body() {
                return new ByteArrayInputStream(body.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }

            @Override
            public Optional<SSLSession> sslSession() {
                return Optional.empty();
            }

            @Override
            public URI uri() {
                return URI.create("https://example.com");
            }

            @Override
            public HttpClient.Version version() {
                return HttpClient.Version.HTTP_1_1;
            }
        };
    }

    private static Object invoke(AdminDashboardService service, String name, Object... args) throws Exception {
        Method method = findMethod(name, args.length);
        method.setAccessible(true);
        return method.invoke(service, args);
    }

    private static Object invokeStatic(String name, Object... args) throws Exception {
        Method method = findMethod(name, args.length);
        method.setAccessible(true);
        return method.invoke(null, args);
    }

    private static Method method(String name, Class<?>... parameterTypes) throws Exception {
        Method method = AdminDashboardService.class.getDeclaredMethod(name, parameterTypes);
        method.setAccessible(true);
        return method;
    }

    private static Method findMethod(String name, int parameterCount) {
        for (Method method : AdminDashboardService.class.getDeclaredMethods()) {
            if (method.getName().equals(name) && method.getParameterCount() == parameterCount) {
                return method;
            }
        }
        throw new IllegalArgumentException("No method " + name);
    }

    private static Object field(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static class FakePluginCorePlugin implements PluginCorePlugin {

        private final List<String> requestedUris = new ArrayList<>();

        @Override
        public boolean refreshCache(String cacheVersion, com.hibegin.http.server.api.HttpRequest request) {
            return true;
        }

        @Override
        public CloseResponseHandle getContext(String uri, HttpMethod method,
                                              com.hibegin.http.server.api.HttpRequest request,
                                              AdminTokenVO adminTokenVO) {
            requestedUris.add(uri);
            if ("/api/plugins".equals(uri)) {
                return closeResponse("{\"plugins\":[{\"shortName\":\"reminder\",\"paths\":[\"surface\"],"
                        + "\"actions\":[\"surfaceAction\"],\"indexPage\":\"settings\"}]}");
            }
            if ("/admin/plugins/reminder/surface".equals(uri)) {
                return closeResponse("{\"success\":true,\"data\":{\"title\":\"Reminder Surface\",\"count\":2}}");
            }
            return closeResponse("{\"success\":false,\"message\":\"missing\"}");
        }

        @Override
        public <T> T requestService(com.hibegin.http.server.api.HttpRequest inputRequest,
                                    Map<String, String[]> params, AdminTokenVO adminTokenVO, Class<T> clazz) {
            return null;
        }

        @Override
        public boolean accessPlugin(String uri, com.hibegin.http.server.api.HttpRequest request,
                                    com.hibegin.http.server.api.HttpResponse response,
                                    AdminTokenVO adminTokenVO) {
            return false;
        }

        @Override
        public String getToken() {
            return "dashboard-token";
        }

        @Override
        public boolean start() {
            return true;
        }

        @Override
        public boolean isStarted() {
            return true;
        }

        @Override
        public boolean stop() {
            return true;
        }
    }
}
