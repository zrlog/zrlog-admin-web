package com.zrlog.admin.util;

import com.hibegin.http.server.api.HttpRequest;
import com.hibegin.http.server.util.NativeImageUtils;
import com.zrlog.admin.business.AdminConstants;
import com.zrlog.admin.business.rest.response.AdminResourceInfoResponse;
import com.zrlog.admin.business.service.AdminResource;
import com.zrlog.admin.support.InMemoryZrLogDatabase;
import com.zrlog.common.Constants;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class AdminNativeImageUtilsTest {

    private final Logger nativeImageLogger = Logger.getLogger(NativeImageUtils.class.getName());
    private Level previousLevel;
    private boolean previousUseParentHandlers;

    @Before
    public void setUp() {
        previousLevel = nativeImageLogger.getLevel();
        previousUseParentHandlers = nativeImageLogger.getUseParentHandlers();
        nativeImageLogger.setUseParentHandlers(false);
        nativeImageLogger.setLevel(Level.OFF);
    }

    @After
    public void tearDown() {
        nativeImageLogger.setLevel(previousLevel);
        nativeImageLogger.setUseParentHandlers(previousUseParentHandlers);
    }

    @Test
    public void shouldBuildNativeImageResourceListFromAdminResource() throws Exception {
        List<String> resources = new java.util.ArrayList<>(AdminNativeImageUtils.getResources());
        resources.addAll(AdminUiNativeImageUtils.getResources(new TestAdminResource(Set.of("/admin/static/app.js"))));

        assertTrue(resources.contains("/admin/static/app.js"));
        assertTrue(resources.contains(AdminConstants.ADMIN_HTML_PAGE));
        assertTrue(resources.contains(AdminConstants.ADMIN_PWA_MANIFEST_JSON));
        assertTrue(resources.contains(AdminResource.ADMIN_ASSET_MANIFEST_JSON));
        assertTrue(resources.contains(AdminConstants.BUILD_SYSTEM_INFO_MD));
        assertTrue(resources.contains(com.zrlog.admin.business.ai.model.AIModelCatalog.RESOURCE));
        assertTrue(resources.stream().anyMatch(resource -> resource.endsWith("zh_CN.md")));
        assertTrue(resources.stream().anyMatch(resource -> resource.endsWith("en_US.md")));
    }

    @Test
    public void shouldRunNativeImageRegistrationSmoke() {
        AdminNativeImageUtils.reg();
        AdminUiNativeImageUtils.reg(new TestAdminResource(Set.of("/admin/static/app.js")));
    }

    @Test
    public void shouldRegisterEveryControllerRouteWithoutInvokingDatabaseDependentConstructors() throws Exception {
        Set<String> registered = new HashSet<>();
        for (String module : List.of("account", "content", "assets", "ai", "access", "web-api", "web-ui")) {
            String path = "/META-INF/native-image/com.hibegin/zrlog-admin-" + module + "/reflect-config.json";
            try (InputStream input = getClass().getResourceAsStream(path)) {
                assertNotNull(path, input);
                for (JsonElement element : JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8))
                        .getAsJsonArray()) {
                    JsonObject entry = element.getAsJsonObject();
                    Class<?> controller = Class.forName(entry.get("name").getAsString());
                    for (JsonElement member : entry.getAsJsonArray("methods")) {
                        JsonObject method = member.getAsJsonObject();
                        Class<?>[] parameters = new Class<?>[method.getAsJsonArray("parameterTypes").size()];
                        for (int i = 0; i < parameters.length; i++) {
                            parameters[i] = Class.forName(method.getAsJsonArray("parameterTypes").get(i).getAsString());
                        }
                        String name = method.get("name").getAsString();
                        if (name.equals("<init>")) controller.getConstructor(parameters);
                        else {
                            assertEquals(0, parameters.length);
                            controller.getDeclaredMethod(name);
                        }
                        registered.add(controller.getName() + "#" + name);
                    }
                }
            }
        }
        try (InMemoryZrLogDatabase db = InMemoryZrLogDatabase.open()) {
            db.loadAdminWebModules();
            Set<Method> routes = new HashSet<>(Constants.zrLogConfig.getServerConfig().getRouter().getRouterMap().values());
            assertTrue("Expected the real admin routes", routes.size() > 100);
            for (Method method : routes) {
                if (!method.getDeclaringClass().getName().startsWith("com.zrlog.admin.")) continue;
                String controller = method.getDeclaringClass().getName();
                assertTrue(method.toString(), registered.contains(controller + "#" + method.getName()));
                assertTrue(controller, registered.contains(controller + "#<init>"));
            }
        }
    }

    private static class TestAdminResource implements AdminResource {

        private final Set<String> staticResourceUris;

        private TestAdminResource(Set<String> staticResourceUris) {
            this.staticResourceUris = staticResourceUris;
        }

        @Override
        public Set<String> getAdminStaticResourceUris() {
            return staticResourceUris;
        }

        @Override
        public Set<String> getAdminPageUris() {
            return Set.of();
        }

        @Override
        public Set<String> getAdminStaticCacheUris() {
            return Set.of();
        }

        @Override
        public Set<String> getAdminCacheableApiUris() {
            return Set.of();
        }

        @Override
        public InputStream renderServiceWorker(HttpRequest request) {
            return null;
        }

        @Override
        public String getStaticResourceBuildId() {
            return "test-build";
        }

        @Override
        public AdminResourceInfoResponse adminResourceInfo(HttpRequest request) {
            return null;
        }
    }
}
