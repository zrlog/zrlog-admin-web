package com.zrlog.admin.web.controller.api;

import com.hibegin.common.dao.DataSourceWrapper;
import com.hibegin.http.HttpMethod;
import com.hibegin.http.server.api.HttpRequest;
import com.hibegin.http.server.api.HttpResponse;
import com.hibegin.http.server.web.Controller;
import com.zrlog.admin.business.ai.model.AIProviderType;
import com.zrlog.admin.business.ai.service.AIServiceDependencies;
import com.zrlog.admin.business.exception.PermissionErrorException;
import com.zrlog.admin.business.rest.base.*;
import com.zrlog.admin.business.rest.request.CreateArticleRequest;
import com.zrlog.admin.business.rest.request.UpdateArticleRequest;
import com.zrlog.admin.business.rest.response.AIWebSiteInfoResponse;
import com.zrlog.admin.business.rest.response.AdminPageDataResponse;
import com.zrlog.admin.business.rest.response.AdminResourceInfoResponse;
import com.zrlog.admin.business.rest.response.VersionResponse;
import com.zrlog.admin.business.service.*;
import com.zrlog.admin.support.AccountServicesFixture;
import com.zrlog.admin.support.ApplicationServicesFixture;
import com.zrlog.admin.support.InMemoryZrLogDatabase;
import com.zrlog.admin.web.McpWebSetup;
import com.zrlog.admin.web.controller.ai.AIWebSiteController;
import com.zrlog.admin.web.token.AdminTokenThreadLocal;
import com.zrlog.business.updater.UpdateVersionInfoPlugin;
import com.zrlog.common.ZrLogConfig;
import com.zrlog.common.adapter.UpdaterHosts;
import com.zrlog.common.exception.ArgsException;
import com.zrlog.model.UserPasskey;
import com.zrlog.model.WebSite;
import com.zrlog.plugin.BaseStaticSitePlugin;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.Assert.*;

public class WebSiteControllerDatabaseTest {

    private ZrLogConfig applicationConfig;

    private static Object value(InMemoryZrLogDatabase db, String name) throws Exception {
        Map<String, Object> row = db.queryOne("select value from website where name=?", name);
        return row == null ? null : row.get("value");
    }

    private static void setControllerField(Controller controller, String name, Object value) throws Exception {
        Field field = Controller.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(controller, value);
    }

    private static HttpRequest request(HttpMethod method, String uri, String body) {
        return request(method, uri, body, "JUnit");
    }

    private static HttpRequest request(HttpMethod method, String uri, String body, String userAgent) {
        return (HttpRequest) Proxy.newProxyInstance(
                WebSiteControllerDatabaseTest.class.getClassLoader(),
                new Class[]{HttpRequest.class},
                (proxy, invokedMethod, args) -> {
                    switch (invokedMethod.getName()) {
                        case "getMethod":
                            return method;
                        case "getUri":
                            return uri;
                        case "getInputStream":
                            return body == null ? null : new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8));
                        case "getHeader":
                            return "User-Agent".equals(args[0]) ? userAgent : null;
                        case "getHeaderMap":
                            return Map.of("X-Real-IP", "127.0.0.1");
                        case "getRemoteHost":
                            return "127.0.0.1";
                        case "getContextPath":
                            return "/";
                        case "toString":
                            return "HttpRequestProxy";
                        default:
                            if (invokedMethod.getReturnType().isPrimitive()) {
                                return 0;
                            }
                            return null;
                    }
                });
    }

    private static HttpResponse response() {
        return (HttpResponse) Proxy.newProxyInstance(
                WebSiteControllerDatabaseTest.class.getClassLoader(),
                new Class[]{HttpResponse.class},
                (proxy, method, args) -> null);
    }

    @Test
    public void arrangementVisibilityIsPersistedWithoutChangingArticleDataOrBlockingWrites() throws Exception {
        try (InMemoryZrLogDatabase db = InMemoryZrLogDatabase.open()) {
            applicationConfig = db.config();
            String labUri = "/api/admin/website/lab";
            HttpRequest editorRequest = request(HttpMethod.GET, "/admin/article-edit", null);
            AdminResourceService resources =
                    new AdminResourceService(
                            AdminResourceInfoResponse.Capabilities::new,
                            new FeatureLabService(
                                    () -> true,
                                    new WebhookService(
                                            ApplicationServicesFixture.messages(db.dataSource()),
                                            ApplicationServicesFixture.websiteCache(db.dataSource())),
                                    () -> new WebSite(db.dataSource())),
                            ApplicationServicesFixture.site(db.config()),
                            () ->
                                    new WebSite(db.dataSource())
                                            .getStringValueByName("admin_static_resource_base_url"),
                            () -> new UserPasskey(db.dataSource()));
            assertFalse(controller(HttpMethod.GET, labUri, null).lab().getData().getFeature_article_arrangement_enabled());
            assertFalse(resources.adminResourceInfo(editorRequest).getFeature_article_arrangement_enabled());

            db.loadAdminWebModules();
            var content = db.component(com.zrlog.admin.web.di.content.ContentComponent.class);
            AdminArticleService articles = content.articles();
            CreateArticleRequest create = new CreateArticleRequest();
            create.setTitle("Layout visibility");
            create.setAlias("layout-visibility");
            create.setContent("<p>Content</p>");
            create.setMarkdown("Content");
            create.setTypeId(1L);
            create.setRubbish(true);
            create.setArrange_plugin("retro-runtime");
            int id = articles.create(AccountServicesFixture.component(db.dataSource()).requestIdentity().account(AdminTokenThreadLocal.getUser()), create).getLogId().intValue();
            Map<String, Object> original = db.queryOne("select * from log where logId=?", id);

            for (boolean enabled : new boolean[]{true, false}) {
                var saved = controller(HttpMethod.POST, labUri,
                        "{\"feature_article_arrangement_enabled\":" + enabled + "}").lab();
                assertEquals(enabled, saved.getData().getFeature_article_arrangement_enabled());
                assertEquals(String.valueOf(enabled), String.valueOf(value(db, "feature_article_arrangement_enabled")));
                assertEquals(enabled, controller(HttpMethod.GET, labUri, null).lab().getData().getFeature_article_arrangement_enabled());
                assertEquals(enabled, resources.adminResourceInfo(editorRequest).getFeature_article_arrangement_enabled());
                assertEquals(original, db.queryOne("select * from log where logId=?", id));
                assertEquals(
                        "retro-runtime",
                        content.queries()
                                .loadDetail(AccountServicesFixture.component(db.dataSource()).requestIdentity().current(), "layout-visibility", editorRequest)
                                .getArrange_plugin());
            }

            UpdateArticleRequest update =
                    content.queries().loadDetail(AccountServicesFixture.component(db.dataSource()).requestIdentity().current(), "layout-visibility", editorRequest);
            update.setArrange_plugin("another-arranger");
            update.setRubbish(false);
            articles.update(AccountServicesFixture.component(db.dataSource()).requestIdentity().account(AdminTokenThreadLocal.getUser()), update);
            assertEquals("another-arranger", db.scalar("select arrange_plugin from log where logId=?", id));
            assertEquals(Boolean.FALSE, db.scalar("select rubbish from log where logId=?", id));
        }
    }

    @Test
    public void backendServiceAddressIsSavedPrivatelyAndUsedByOAuthImmediately() throws Exception {
        try (InMemoryZrLogDatabase db = InMemoryZrLogDatabase.open()) {
            applicationConfig = db.config();
            String address = "https://gateway.example/sub";
            Object blogHost = value(db, "host");
            var saved = controller(HttpMethod.POST, "/api/admin/website/admin",
                    "{\"session_timeout\":60,\"backend_server_url\":\" HTTPS://Gateway.Example/sub/ \"}").admin();
            assertEquals(address, saved.getData().getBackend_server_url());
            assertEquals(address, value(db, "backend_server_url"));
            assertEquals(blogHost, value(db, "host"));
            assertEquals(address, controller(HttpMethod.GET, "/api/admin/website/admin", null).admin().getData().getBackend_server_url());
            var oauth =
                    new OAuthService(
                            ApplicationServicesFixture.backend(db.config())::configured,
                            ApplicationServicesFixture.available(db.config(), McpWebSetup.class),
                            AccountServicesFixture.component(db.dataSource()).permissions(), AccountServicesFixture.component(db.dataSource()).identities(),
                            AccountServicesFixture.component(db.dataSource()).security(),
                            tokenResource ->
                                    new PersonalAccessTokenService(
                                            tokenResource,
                                            AccountServicesFixture.component(db.dataSource()).identities(), AccountServicesFixture.component(db.dataSource()).permissions(),
                                            AccountServicesFixture.component(db.dataSource()).security()),
                            AccountServicesFixture.component(db.dataSource()).backend());
            assertEquals(address, oauth.metadata().issuer);
            assertEquals(address + "/mcp", oauth.mcpResourceMetadata().resource);
            assertEquals(address + "/api/webhook/message-center/notice", oauth.page(AccountServicesFixture.component(db.dataSource()).requestIdentity().current()).notificationEndpoint);
            String publicInfo = new com.google.gson.Gson().toJson(new WebSite(db.dataSource()).getPublicWebSite());
            assertFalse(publicInfo.contains("backend_server_url"));
            assertFalse(publicInfo.contains("gateway.example"));
            // Old settings clients must not clear a value they do not know about.
            controller(HttpMethod.POST, "/api/admin/website/admin", "{\"session_timeout\":90}").admin();
            assertEquals(address, value(db, "backend_server_url"));
            controller(HttpMethod.POST, "/api/admin/website/admin", "{\"session_timeout\":90,\"backend_server_url\":\"\"}").admin();
            assertEquals("", value(db, "backend_server_url"));
            assertFalse(
                    new OAuthService(
                            ApplicationServicesFixture.backend(db.config())::configured,
                            ApplicationServicesFixture.available(
                                    db.config(), McpWebSetup.class),
                            AccountServicesFixture.component(db.dataSource()).permissions(), AccountServicesFixture.component(db.dataSource()).identities(),
                            AccountServicesFixture.component(db.dataSource()).security(),
                            tokenResource ->
                                    new PersonalAccessTokenService(
                                            tokenResource,
                                            AccountServicesFixture.component(db.dataSource())
                                                    .identities(), AccountServicesFixture.component(db.dataSource())
                                            .permissions(),
                                            AccountServicesFixture.component(db.dataSource()).security()),
                            AccountServicesFixture.component(db.dataSource()).backend())
                            .issuer()
                            .contains("gateway.example"));
        }
    }

    @Test
    public void invalidBackendAddressesCannotOverwriteSavedAddress() throws Exception {
        try (InMemoryZrLogDatabase db = InMemoryZrLogDatabase.open()) {
            applicationConfig = db.config();
            db.putWebsite("backend_server_url", "https://gateway.example");
            for (String invalid : java.util.List.of("/sub", "//gateway.example", "gateway.example", "http://gateway.example",
                    "ftp://gateway.example", "https://user:password@gateway.example", "https://gateway.example?x=1",
                    "https://gateway.example#x", "https://gateway.example:0", "https://gateway.example:65536",
                    "https://gateway.example/../sub", "https://gateway.example/%2e%2e/sub", "https://gateway.example/sub%2fother",
                    "https://gateway.example/%252e%252e", "https://gateway.example/sub%0aother")) {
                String body = new com.google.gson.Gson().toJson(Map.of("backend_server_url", invalid, "session_timeout", 60));
                assertThrows(invalid, ArgsException.class, () -> controller(HttpMethod.POST, "/api/admin/website/admin", body).admin());
                assertEquals("https://gateway.example", value(db, "backend_server_url"));
            }
            var local = controller(HttpMethod.POST, "/api/admin/website/admin",
                    "{\"session_timeout\":60,\"backend_server_url\":\"http://127.0.0.1:18080/sub/\"}").admin();
            assertEquals("http://127.0.0.1:18080/sub", local.getData().getBackend_server_url());
        }
    }

    @Test
    public void staticSettingsExportAndNonAdministratorsCannotReadBackendAddress() throws Exception {
        try (InMemoryZrLogDatabase db = InMemoryZrLogDatabase.open()) {
            applicationConfig = db.config();
            db.putWebsite("backend_server_url", "https://gateway.example/sub");
            WebSiteController staticController = controller(HttpMethod.GET, "/api/admin/website/admin", null);
            setControllerField(
                    staticController,
                    "request",
                    request(
                            HttpMethod.GET,
                            "/api/admin/website/admin",
                            null,
                            BaseStaticSitePlugin.STATIC_USER_AGENT));
            String json = new com.google.gson.Gson().toJson(staticController.admin());
            assertFalse(json.contains("backend_server_url"));
            assertFalse(json.contains("gateway.example"));
            var method = WebSiteController.class.getMethod("admin");
            db.execute("update user set role=? where userId=?", "author", 1);
            assertThrows(
                    PermissionErrorException.class,
                    () ->
                            AccountServicesFixture.component(db.dataSource()).routeAuthorizer().checkRoute(AccountServicesFixture.component(db.dataSource()).requestIdentity().current(),
                                    method,
                                    request(
                                            HttpMethod.GET,
                                            "/api/admin/website/admin",
                                            null)));
            AdminTokenThreadLocal.remove();
            assertThrows(
                    PermissionErrorException.class,
                    () ->
                            AccountServicesFixture.component(db.dataSource()).routeAuthorizer().checkRoute(AccountServicesFixture.component(db.dataSource()).requestIdentity().current(),
                                    method,
                                    request(
                                            HttpMethod.GET,
                                            "/api/admin/website/admin",
                                            null)));
        }
    }

    @Test
    public void shouldUpdateBasicWebsiteSettingsThroughRealWebsiteTable() throws Exception {
        try (InMemoryZrLogDatabase db = InMemoryZrLogDatabase.open()) {
            applicationConfig = db.config();
            WebSiteController controller = controller(HttpMethod.POST, "/api/admin/website",
                    "{\"title\":\"<b>Demo</b>\",\"second_title\":\"Second\","
                            + "\"description\":\"<script>x</script>Desc\",\"keywords\":\"zrlog,java\","
                            + "\"author\":\"Admin\"}");

            AdminPageDataResponse<BasicWebSiteInfo> response = controller.basic();

            assertEquals("Demo", response.getData().getTitle());
            assertEquals("Second", response.getData().getSecond_title());
            assertEquals("Desc", response.getData().getDescription());
            assertEquals("zrlog,java", response.getData().getKeywords());
            assertEquals("Admin", response.getData().getAuthor());
            assertEquals("Demo", value(db, "title"));
            assertTrue(String.valueOf(value(db, "admin_audit_log")).contains("UPDATE_SETTING"));
        }
    }

    @Test
    public void shouldUpdateBlogArticleEditAndFeatureLabSettingsThroughRealWebsiteTable() throws Exception {
        try (InMemoryZrLogDatabase db = InMemoryZrLogDatabase.open()) {
            applicationConfig = db.config();
            AdminPageDataResponse<BlogWebSiteInfo> blog = controller(HttpMethod.POST, "/api/admin/website/blog",
                    "{\"generator_html_status\":true,\"host\":\"https://example.com\","
                            + "\"disable_comment_status\":true,\"article_thumbnail_status\":false,"
                            + "\"system_notification\":\"<b>Notice</b>\"}").blog();
            AdminPageDataResponse<ArticleEditWebSiteInfo> articleEdit =
                    controller(HttpMethod.POST, "/api/admin/website/article-edit",
                            "{\"article_auto_digest_length\":120,\"article_edit_auto_save_interval\":10,"
                                    + "\"article_editor_link_preview_enabled\":true,"
                                    + "\"article_publish_check_enabled\":false,"
                                    + "\"article_cover_aspect_ratio\":\"4:3\"}").articleEdit();
            AdminPageDataResponse<FeatureLabWebSiteInfo> lab =
                    controller(HttpMethod.POST, "/api/admin/website/lab",
                            "{\"feature_resource_reference_enabled\":true,"
                                    + "\"feature_article_extension_filter_enabled\":true,"
                                    + "\"feature_webhook_enabled\":true,"
                                    + "\"feature_personal_data_enabled\":false}").lab();

            assertEquals(Boolean.TRUE, blog.getData().getGenerator_html_status());
            assertEquals("https://example.com", blog.getData().getHost());
            assertEquals(Boolean.TRUE, blog.getData().getDisable_comment_status());
            assertEquals(Boolean.FALSE, blog.getData().getArticle_thumbnail_status());
            assertEquals("Notice", blog.getData().getSystem_notification());
            assertEquals(Long.valueOf(120L), articleEdit.getData().getArticle_auto_digest_length());
            assertEquals(Long.valueOf(10L), articleEdit.getData().getArticle_edit_auto_save_interval());
            assertEquals(Boolean.TRUE, articleEdit.getData().getArticle_editor_link_preview_enabled());
            assertEquals(Boolean.FALSE, articleEdit.getData().getArticle_publish_check_enabled());
            assertEquals("4:3", articleEdit.getData().getArticle_cover_aspect_ratio());
            assertEquals(Boolean.TRUE, lab.getData().getFeature_resource_reference_enabled());
            assertEquals(Boolean.TRUE, lab.getData().getFeature_article_extension_filter_enabled());
            assertEquals(Boolean.TRUE, lab.getData().getFeature_webhook_enabled());
            assertEquals(Boolean.FALSE, lab.getData().getFeature_personal_data_enabled());
            assertTrue(String.valueOf(value(db, "generator_html_status")).equalsIgnoreCase("true"));
            assertEquals("120", String.valueOf(value(db, "article_auto_digest_length")));
            assertTrue(String.valueOf(value(db, "feature_resource_reference_enabled")).equalsIgnoreCase("true"));
        }
    }

    @Test
    public void shouldReadAndUpdateOtherAdminContentProtectorAndUpgradeSettingsThroughRealWebsiteTable()
            throws Exception {
        try (InMemoryZrLogDatabase db = InMemoryZrLogDatabase.open()) {
            applicationConfig = db.config();
            AdminPageDataResponse<BasicWebSiteInfo> index =
                    controller(HttpMethod.GET, "/api/admin/website", null).index();
            AdminPageDataResponse<OtherWebSiteInfo> other =
                    controller(HttpMethod.POST, "/api/admin/website/other",
                            "{\"icp\":\"<b>ICP</b><script>x</script>\",\"webCm\":\"公安备案\","
                                    + "\"robotRuleContent\":\"User-agent: *\\nDisallow: /admin\"}").other();
            AdminPageDataResponse<AdminWebSiteInfo> admin =
                    controller(HttpMethod.GET, "/api/admin/website/admin", null).admin();
            AdminPageDataResponse<ContentProtectorWebSiteInfo> contentProtector =
                    controller(HttpMethod.POST, "/api/admin/website/content-protector",
                            "{\"content_protector_enabled\":true,"
                                    + "\"content_protector_license_type\":\"CC_BY_4_0\","
                                    + "\"content_protector_template\":\"Content {{title}}\"}").contentProtector();
            WebSiteController upgrade = controller(HttpMethod.POST, "/api/admin/website/upgrade",
                    "{\"autoUpgradeVersion\":1,\"upgradePreview\":true}");

            assertEquals("ZrLog Test", index.getData().getTitle());
            assertEquals("<b>ICP</b>", other.getData().getIcp());
            assertEquals("公安备案", other.getData().getWebCm());
            assertEquals("User-agent: *\nDisallow: /admin", other.getData().getRobotRuleContent());
            assertNotNull(admin.getData().getSession_timeout());
            assertEquals(Boolean.TRUE, contentProtector.getData().getContent_protector_enabled());
            assertEquals("CC_BY_4_0", contentProtector.getData().getContent_protector_license_type());
            assertEquals("Content {{title}}", contentProtector.getData().getContent_protector_template());
            assertEquals("<b>ICP</b>", value(db, "icp"));
            assertEquals("CC_BY_4_0", value(db, "content_protector_license_type"));
            assertThrows(RuntimeException.class, upgrade::upgrade);
        }
    }

    @Test
    public void shouldMaskAndReuseExistingAiKeysWhenUpdatingAiSettings() throws Exception {
        try (InMemoryZrLogDatabase db = InMemoryZrLogDatabase.open()) {
            applicationConfig = db.config();
            db.putWebsite("ai_provider", "OPEN_AI");
            db.putWebsite("ai_model", "gpt-5");
            db.putWebsite("ai_api_key", "existing-key");
            db.putWebsite("ai_image_provider", "OPEN_AI");
            db.putWebsite("ai_image_model", "gpt-image-2");
            db.putWebsite("ai_image_base_url", "");
            db.putWebsite("ai_image_api_key", "existing-image-key");

            AdminPageDataResponse<AIWebSiteInfoResponse> response =
                    aiController(HttpMethod.POST, "/api/admin/website/ai",
                            "{\"ai_provider\":\"OPEN_AI\",\"ai_model\":\" custom-model:latest \","
                                    + "\"ai_base_url\":\"\","
                                    + "\"ai_api_key\":\"\",\"ai_prompt\":\"Prompt\","
                                    + "\"ai_max_completion_tokens\":1024,"
                                    + "\"ai_reasoning_enabled\":false,"
                                    + "\"ai_image_provider\":\"OPEN_AI\","
                                    + "\"ai_image_model\":\"gpt-image-2\","
                                    + "\"ai_image_base_url\":\"\","
                                    + "\"ai_image_api_key\":\"\"}", db.dataSource()).ai();

            assertEquals(AIProviderType.OPEN_AI, response.getData().getAi_provider());
            assertEquals("custom-model:latest", response.getData().getAi_model());
            assertEquals("", response.getData().getAi_base_url());
            assertEquals("", response.getData().getAi_api_key());
            assertEquals("", response.getData().getAi_image_api_key());
            assertEquals("", response.getData().getAi_image_base_url());
            assertEquals(Boolean.FALSE, response.getData().getAi_reasoning_enabled());
            assertTrue(response.getData().isHasAiApiKey());
            assertTrue(response.getData().isHasAiImageApiKey());
            assertFalse(response.getData().getAllProviders().isEmpty());
            assertEquals(AIProviderType.OPEN_AI.getBaseUrl(), response.getData().getAllProviders().stream()
                    .filter(provider -> provider.getName() == AIProviderType.OPEN_AI)
                    .findFirst().orElseThrow().getBaseUrl());
            assertFalse(response.getData().getAllImageProviders().isEmpty());
            assertEquals(AIProviderType.OPEN_AI.getBaseUrl(), response.getData().getAllImageProviders().stream()
                    .filter(provider -> provider.getName() == AIProviderType.OPEN_AI)
                    .findFirst().orElseThrow().getBaseUrl());
            assertEquals("existing-key", value(db, "ai_api_key"));
            assertEquals("existing-image-key", value(db, "ai_image_api_key"));
            assertEquals("false", value(db, "ai_reasoning_enabled"));
            assertEquals("", value(db, "ai_base_url"));
            assertEquals("", value(db, "ai_image_base_url"));
            assertTrue(String.valueOf(value(db, "admin_audit_log"))
                    .contains(com.zrlog.util.I18nUtil.getAdminBackendStringFromRes("admin.website.ai.manage")));
        }
    }

    @Test
    public void shouldAllowCustomImageEndpointWithoutReusingExistingImageKey() throws Exception {
        try (InMemoryZrLogDatabase db = InMemoryZrLogDatabase.open()) {
            applicationConfig = db.config();
            db.putWebsite("ai_provider", "OPEN_AI");
            db.putWebsite("ai_model", "gpt-5");
            db.putWebsite("ai_api_key", "existing-key");
            db.putWebsite("ai_image_provider", "OPEN_AI");
            db.putWebsite("ai_image_model", "gpt-image-2");
            db.putWebsite("ai_image_base_url", "");
            db.putWebsite("ai_image_api_key", "existing-image-key");

            AdminPageDataResponse<AIWebSiteInfoResponse> response =
                    aiController(HttpMethod.POST, "/api/admin/website/ai",
                            "{\"ai_provider\":\"OPEN_AI\",\"ai_model\":\"gpt-5\","
                                    + "\"ai_base_url\":\"\",\"ai_api_key\":\"\","
                                    + "\"ai_image_provider\":\"OPEN_AI\","
                                    + "\"ai_image_model\":\" local-image:latest \","
                                    + "\"ai_image_base_url\":\"http://localhost:11434/v1/\","
                                    + "\"ai_image_api_key\":\"\"}", db.dataSource()).ai();

            assertEquals("local-image:latest", response.getData().getAi_image_model());
            assertEquals("http://localhost:11434/v1", response.getData().getAi_image_base_url());
            assertFalse(response.getData().isHasAiImageApiKey());
            assertEquals("", value(db, "ai_image_api_key"));
            assertEquals("http://localhost:11434/v1", value(db, "ai_image_base_url"));
        }
    }

    @Test
    public void shouldRejectUnknownImageModelForProviderDefaultEndpoint() throws Exception {
        try (InMemoryZrLogDatabase ignored = InMemoryZrLogDatabase.open()) {
            applicationConfig = ignored.config();
            AIWebSiteController controller = aiController(HttpMethod.POST, "/api/admin/website/ai",
                    "{\"ai_provider\":\"OPEN_AI\",\"ai_model\":\"gpt-5\","
                            + "\"ai_api_key\":\"text-key\","
                            + "\"ai_image_provider\":\"OPEN_AI\","
                            + "\"ai_image_model\":\"local-image:latest\","
                            + "\"ai_image_base_url\":\"\","
                            + "\"ai_image_api_key\":\"image-key\"}", ignored.dataSource());

            assertThrows(ArgsException.class, controller::ai);
        }
    }

    @Test
    public void shouldNotReuseExistingAiKeyForChangedCustomEndpoint() throws Exception {
        try (InMemoryZrLogDatabase db = InMemoryZrLogDatabase.open()) {
            applicationConfig = db.config();
            db.putWebsite("ai_provider", "OPEN_AI");
            db.putWebsite("ai_model", "gpt-5");
            db.putWebsite("ai_api_key", "existing-key");

            AdminPageDataResponse<AIWebSiteInfoResponse> response =
                    aiController(HttpMethod.POST, "/api/admin/website/ai",
                            "{\"ai_provider\":\"OPEN_AI\",\"ai_model\":\"local-model\","
                                    + "\"ai_base_url\":\"http://localhost:11434/v1\","
                                    + "\"ai_api_key\":\"\",\"ai_prompt\":\"Prompt\"}", db.dataSource()).ai();

            assertEquals("local-model", response.getData().getAi_model());
            assertEquals("http://localhost:11434/v1", response.getData().getAi_base_url());
            assertFalse(response.getData().isHasAiApiKey());
            assertEquals("", value(db, "ai_api_key"));
            assertEquals("http://localhost:11434/v1", value(db, "ai_base_url"));
        }
    }

    @Test
    public void shouldDefaultAiReasoningEnabledWhenUnset() throws Exception {
        try (InMemoryZrLogDatabase db = InMemoryZrLogDatabase.open()) {
            applicationConfig = db.config();
            db.putWebsite("ai_provider", "OPEN_AI");
            db.putWebsite("ai_model", "gpt-5");
            db.putWebsite("ai_api_key", "existing-key");

            AdminPageDataResponse<AIWebSiteInfoResponse> response =
                    aiController(HttpMethod.GET, "/api/admin/website/ai", null, db.dataSource()).ai();

            assertEquals(Boolean.TRUE, response.getData().getAi_reasoning_enabled());
            assertTrue(response.getData().isReasoningEnabled());
        }
    }

    @Test
    public void shouldRejectAiUpdateWithoutNewOrExistingApiKey() throws Exception {
        try (InMemoryZrLogDatabase ignored = InMemoryZrLogDatabase.open()) {
            applicationConfig = ignored.config();
            AIWebSiteController controller = aiController(HttpMethod.POST, "/api/admin/website/ai",
                    "{\"ai_provider\":\"OPEN_AI\",\"ai_model\":\"gpt-5-mini\",\"ai_api_key\":\"\"}", ignored.dataSource());

            assertThrows(ArgsException.class, controller::ai);
        }
    }

    @Test
    public void shouldReadWebsiteVersionPayloadWithoutDatabaseWrites() throws Exception {
        try (InMemoryZrLogDatabase ignored = InMemoryZrLogDatabase.open()) {
            applicationConfig = ignored.config();
            WebSiteController controller = controller(HttpMethod.GET, "/api/admin/website/version", null);

            AdminPageDataResponse<VersionResponse> response = controller.version();

            assertNotNull(response.getData().getVersion());
            assertNotNull(response.getData().getBuildSystemInfo());
            assertEquals("test changelog", response.getData().getChangelog());
        }
    }

    private WebSiteController controller(HttpMethod method, String uri, String body) throws Exception {
        WebSiteController controller = new TestWebSiteController(applicationConfig);
        setControllerField(controller, "request", request(method, uri, body));
        setControllerField(controller, "response", response());
        return controller;
    }

    private AIWebSiteController aiController(HttpMethod method, String uri, String body, DataSourceWrapper testDataSource) throws Exception {
        AIWebSiteController controller =
                new AIWebSiteController(
                        new WebSiteSettingsService(
                                new InMemoryZrLogDatabase.TestCacheService(),
                                applicationConfig == null
                                        ? null
                                        : applicationConfig.getTokenService(),
                                () ->
                                        applicationConfig == null
                                                ? null
                                                : applicationConfig.getPlugin(
                                                UpdateVersionInfoPlugin.class),
                                ApplicationServicesFixture.audit(testDataSource),
                                ApplicationServicesFixture.websiteKv(testDataSource),
                                () -> new WebSite(testDataSource)),
                        new AdminPageDataFactory(
                                ApplicationServicesFixture.site(applicationConfig),
                                () -> "test",
                                ApplicationServicesFixture.messages(testDataSource)),
                        AIServiceDependencies.config(testDataSource),
                        AIServiceDependencies.tools(testDataSource),
                        AccountServicesFixture.component(testDataSource).requestIdentity());
        setControllerField(controller, "request", request(method, uri, body));
        setControllerField(controller, "response", response());
        return controller;
    }

    private static class TestWebSiteController extends WebSiteController {
        public TestWebSiteController(ZrLogConfig config) {
            super(
                    new WebSiteSettingsService(
                            new InMemoryZrLogDatabase.TestCacheService(),
                            config == null ? null : config.getTokenService(),
                            () ->
                                    config == null
                                            ? null
                                            : config.getPlugin(UpdateVersionInfoPlugin.class),
                            ApplicationServicesFixture.audit((DataSourceWrapper) config.getDataSource()),
                            ApplicationServicesFixture.websiteKv((DataSourceWrapper) config.getDataSource()),
                            () -> new WebSite((DataSourceWrapper) config.getDataSource())),
                    new UpgradeSettingsService(new WebSiteSettingsService(
                            new InMemoryZrLogDatabase.TestCacheService(),
                            config == null ? null : config.getTokenService(),
                            () ->
                                    config == null
                                            ? null
                                            : config.getPlugin(
                                            UpdateVersionInfoPlugin.class),
                            ApplicationServicesFixture.audit((DataSourceWrapper) config.getDataSource()),
                            ApplicationServicesFixture.websiteKv((DataSourceWrapper) config.getDataSource()),
                            () -> new WebSite((DataSourceWrapper) config.getDataSource())),
                            () ->
                                    UpdaterHosts.dockerControl(
                                            config == null ? null : config.getUpdater()),
                            AccountServicesFixture.component((DataSourceWrapper) config.getDataSource()).permissions(),
                            AccountServicesFixture.component((DataSourceWrapper) config.getDataSource()).identities(),
                            ApplicationServicesFixture.settings((DataSourceWrapper) config.getDataSource())),
                    new FeatureLabService(
                            () -> true,
                            new WebhookService(
                                    ApplicationServicesFixture.messages((DataSourceWrapper) config.getDataSource()),
                                    ApplicationServicesFixture.websiteCache((DataSourceWrapper) config.getDataSource())),
                            () -> new WebSite((DataSourceWrapper) config.getDataSource())),
                    new AdminPageDataFactory(
                            ApplicationServicesFixture.site(config),
                            () -> "test",
                            ApplicationServicesFixture.messages((DataSourceWrapper) config.getDataSource())),
                    ApplicationServicesFixture.settings((DataSourceWrapper) config.getDataSource()),
                    AccountServicesFixture.component((DataSourceWrapper) config.getDataSource()).requestIdentity());
        }


        @Override
        protected String getCurrentChangeLog(Map<String, Object> backendMessages) {
            return "test changelog";
        }
    }
}
