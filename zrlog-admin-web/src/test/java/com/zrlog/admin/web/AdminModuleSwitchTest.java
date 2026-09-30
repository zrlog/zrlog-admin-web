package com.zrlog.admin.web;

import com.google.gson.Gson;
import com.hibegin.http.HttpMethod;
import com.hibegin.http.server.api.HttpRequest;
import com.zrlog.admin.business.content.ArticleAssistant;
import com.zrlog.admin.business.rest.request.CreateArticleRequest;
import com.zrlog.admin.business.service.AdminArticleService;
import com.zrlog.admin.business.service.ArticlePublishingService;
import com.zrlog.admin.support.InMemoryZrLogDatabase;
import com.zrlog.admin.web.controller.ai.AIArticleController;
import com.zrlog.admin.web.interceptor.McpInterceptor;
import com.zrlog.admin.web.interceptor.OAuthInterceptor;
import com.zrlog.admin.web.interceptor.BearerTokenInterceptor;
import com.zrlog.admin.web.interceptor.AdminLoginInterceptor;
import com.zrlog.admin.web.token.AdminTokenThreadLocal;
import com.zrlog.common.Constants;
import org.junit.Test;

import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

public class AdminModuleSwitchTest {
    @Test
    public void realDiscoveryEnablesAiAndCanDisableItWithoutDisablingContentOrMcp() throws Exception {
        probe("");
        probe("admin-ai");
        probe("admin-access");
        probe("admin-mcp");
        probe("admin-ui");
        probe("admin");
        probe("admin-content");
        probe("admin-assets");
        probe("admin-account");
        probe("admin-ai,admin-mcp");
    }

    private void probe(String disabled) throws Exception {
        Path output = Files.createTempFile("zrlog-module-probe-", ".log");
        ProcessBuilder builder = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-cp", System.getProperty("java.class.path"), getClass().getName(), disabled)
                .redirectErrorStream(true).redirectOutput(output.toFile());
        builder.environment().put("DISABLE_MODULES", disabled);
        builder.environment().put("WEB_SETUP_STRICT", "true");
        Process process = builder.start();
        try {
            assertTrue("Module startup timed out: " + disabled, process.waitFor(30, TimeUnit.SECONDS));
            assertEquals(Files.readString(output), 0, process.exitValue());
        } finally {
            process.destroyForcibly();
            Files.deleteIfExists(output);
        }
    }

    public static void main(String[] args) throws Exception {
        var disabled = java.util.Set.of(args[0].split(","));
        boolean admin = !disabled.contains("admin");
        boolean ui = admin && !disabled.contains("admin-ui");
        boolean account = admin && !disabled.contains("admin-account");
        boolean content = account && !disabled.contains("admin-content");
        boolean assets = content && !disabled.contains("admin-assets");
        boolean ai = assets && !disabled.contains("admin-ai");
        boolean access = account && !disabled.contains("admin-access");
        boolean mcp = access && content && !disabled.contains("admin-mcp");
        try (InMemoryZrLogDatabase db = InMemoryZrLogDatabase.open()) {
            db.loadAdminWebModules();
            var config = Constants.zrLogConfig;
            var router = config.getServerConfig().getRouter();
            assertEquals(ui, config.getWebSetup(AdminUiWebSetup.class) != null);
            assertEquals(ui, router.getMethod("/admin", HttpMethod.GET) != null);
            assertEquals(ai, config.getWebSetup(ArticleAssistant.class) != null);
            assertEquals(assets, com.zrlog.admin.business.content.AttachmentStorage.current() != null);
            assertEquals(mcp, config.getServerConfig().getInterceptors().contains(McpInterceptor.class));
            assertEquals(access, config.getServerConfig().getInterceptors().contains(OAuthInterceptor.class));
            assertEquals(access, config.getServerConfig().getInterceptors().contains(BearerTokenInterceptor.class));
            assertEquals(admin, config.getServerConfig().getInterceptors().contains(AdminLoginInterceptor.class));
            assertEquals(access, router.getMethod("/api/admin/oauth", HttpMethod.GET) != null);
            assertEquals(access, router.getMethod("/api/webhook/message-center/notice", HttpMethod.POST) != null);
            for (String path : new String[]{"/api/admin/article/ai",
                    "/api/admin/article/ai/approval",
                    "/api/admin/article/clearAiMessages",
                    "/api/admin/article/cover/apply", "/api/admin/website/ai", "/api/admin/website/optimizeAiPrompt"}) {
                var method = router.getMethod(path, HttpMethod.POST);
                if (ai) assertNotNull(path, method);
                else assertNull(path, method);
            }
            assertEquals(ai, router.getMethod("/api/admin/article/ai/run", HttpMethod.GET) != null);
            assertEquals(account, router.getMethod("/api/admin/login", HttpMethod.POST) != null);
            assertEquals(content, router.getMethod("/api/admin/article/create", HttpMethod.POST) != null);
            assertEquals(assets, router.getMethod("/api/admin/upload", HttpMethod.POST) != null);
            if (!admin) return;
            if (access) {
                var oauth = new com.zrlog.admin.business.service.OAuthService(() -> "https://blog.example");
                assertEquals(mcp, oauth.page().mcpResource != null);
                var token = new com.zrlog.admin.business.security.OAuthModels.TokenRequest();
                token.resource = oauth.mcpResource();
                token.grant_type = "authorization_code";
                if (!mcp) assertEquals("invalid_target", assertThrows(
                        com.zrlog.admin.business.security.OAuthException.class, () -> oauth.token(token)).getOAuthError());
            }
            if (!content) return;
            if (ai) assertEquals(AIArticleController.class,
                    router.getMethod("/api/admin/article/ai", HttpMethod.POST).getDeclaringClass());
            assertNotNull(router.getMethod("/api/admin/article/create", HttpMethod.POST));
            db.putWebsite("ai_provider", "OPEN_AI");
            db.putWebsite("ai_model", "unreachable-model");
            db.putWebsite("ai_api_key", "test-key");
            CreateArticleRequest article = new CreateArticleRequest();
            article.setTitle("Independent content"); article.setAlias("independent-content");
            article.setTypeId(1L); article.setContent("<p>Saved without invoking a model</p>");
            article.setRubbish(true);
            HttpRequest request = (HttpRequest) Proxy.newProxyInstance(AdminModuleSwitchTest.class.getClassLoader(),
                    new Class<?>[]{HttpRequest.class}, (proxy, method, values) -> {
                        if (method.getName().equals("getUri")) return "/api/admin/article-edit";
                        if (method.getName().equals("getContextPath")) return "";
                        if (method.getName().equals("getRemoteHost")) return "127.0.0.1";
                        if (method.getName().equals("getScheme")) return "http";
                        if (method.getName().equals("getHeader")) return "Host".equals(values[0]) ? "localhost:18080" : null;
                        return null;
                    });
            var capabilities = new com.zrlog.admin.business.service.AdminResourceService().adminResourceInfo(request).getCapabilities();
            assertEquals(ai, capabilities.ai);
            assertEquals(access, capabilities.access);
            assertEquals(mcp, capabilities.mcp);
            assertEquals(assets, capabilities.assets);
            var saved = new ArticlePublishingService().create(AdminTokenThreadLocal.getUser(), article, request);
            var detail = new AdminArticleService().loadDetailById(saved.getLogId().toString(), request);
            // The same article tools serve MCP without AI, and internal AI without external access.
            var knowledge = new com.zrlog.admin.business.knowledge.KnowledgeService(
                    com.zrlog.admin.business.service.AccountPermissionService::current,
                    java.util.Set.of("articles:read", "articles:read_drafts"), () -> "https://blog.example");
            var argsJson = new com.google.gson.JsonObject(); argsJson.addProperty("id", saved.getLogId());
            var source = (com.zrlog.admin.business.knowledge.KnowledgeModels.ArticleResult) knowledge.call("read_article", argsJson);
            assertEquals("Independent content", source.source.title);
            if (mcp) {
                var reply = new com.zrlog.admin.business.knowledge.McpService(() -> "Blog").handle(
                        "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/call\",\"params\":{\"name\":\"read_article\",\"arguments\":" + argsJson + "}}", knowledge);
                assertEquals(200, reply.status);
                assertFalse(reply.body.getAsJsonObject("result").get("isError").getAsBoolean());
            }
            var json = new Gson().toJsonTree(detail.getData()).getAsJsonObject();
            assertEquals(ai, json.has("aiConfigured"));
            assertTrue(json.has("article"));
            assertEquals("Independent content", detail.getData().getArticle().getTitle());
            if (!ai) assertSame(ArticleAssistant.PublishProgress.NONE,
                    ArticleAssistant.current().beginPublishCheck(detail.getData(), article, null));
            assertTrue(new AdminArticleService().delete(saved.getLogId()));
        }
    }
}
