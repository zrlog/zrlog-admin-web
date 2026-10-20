package com.zrlog.admin.web.interceptor;

import static org.junit.Assert.*;

import com.google.gson.*;
import com.hibegin.common.dao.DataSourceWrapper;
import com.hibegin.http.HttpMethod;
import com.hibegin.http.server.api.HttpRequest;
import com.hibegin.http.server.api.HttpResponse;
import com.hibegin.http.server.config.ServerConfig;
import com.zrlog.admin.business.content.ArticleAssistant;
import com.zrlog.admin.business.content.AttachmentStorage;
import com.zrlog.admin.business.knowledge.ContentToolFactory;
import com.zrlog.admin.business.security.OAuthModels.*;
import com.zrlog.admin.business.security.PersonalTokenModels;
import com.zrlog.admin.business.service.*;
import com.zrlog.admin.business.service.AdminPageDataFactory;
import com.zrlog.admin.business.service.OAuthService;
import com.zrlog.admin.business.service.PersonalAccessTokenService;
import com.zrlog.admin.support.AccountServicesFixture;
import com.zrlog.admin.support.ApplicationServicesFixture;
import com.zrlog.admin.support.InMemoryZrLogDatabase;
import com.zrlog.admin.util.BackendServerUrl;
import com.zrlog.admin.web.ContentWebSetup;
import com.zrlog.admin.web.McpWebSetup;
import com.zrlog.admin.web.di.access.DaggerAccessComponent;
import com.zrlog.admin.web.di.account.DaggerAccountComponent;
import com.zrlog.admin.web.di.content.ContentComponent;
import com.zrlog.admin.web.di.content.DaggerContentComponent;
import com.zrlog.admin.web.token.AdminTokenThreadLocal;
import com.zrlog.business.di.DaggerServiceComponent;
import com.zrlog.business.service.CacheRefreshService;
import com.zrlog.common.CacheService;
import com.zrlog.common.ZrLogConfig;
import com.zrlog.common.vo.AdminTokenVO;
import com.zrlog.common.vo.I18nVO;
import com.zrlog.data.di.DaggerDataComponent;
import com.zrlog.data.security.AccountAccess;
import com.zrlog.util.I18nUtil;

import org.junit.Test;

import java.io.InputStream;
import java.lang.reflect.Proxy;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class McpInterceptorTest {

    private ZrLogConfig applicationConfig;

    @Test public void followsTokenOwnerAcrossAccountsAndPreferenceChangesWithoutBrowserSession() throws Exception {
        I18nVO previous = I18nUtil.threadLocal.get();
        try (InMemoryZrLogDatabase db = InMemoryZrLogDatabase.open()) {
            applicationConfig = db.config();
            db.putWebsite("language", "en_US");
            db.execute("update user set preferences=? where userId=1", "{\"language\":\"zh_CN\"}");
            String chineseToken = personalToken(db.dataSource());
            db.execute("insert into user(userId,userName,role,preferences) values(2,'other','admin',?)", "{\"language\":\"en_US\"}");
            login(2);
            String englishToken = personalToken(db.dataSource());
            // An unrelated browser identity and locale must not override the Bearer token owner.
            I18nVO context = I18nUtil.addToRequest(null, "en_US");
            context.setLocale("en_US");
            assertTitle(chineseToken, "搜索文章", db.dataSource());
            assertEquals(2, AdminTokenThreadLocal.getUser().getUserId());
            AdminTokenThreadLocal.remove();
            assertTitle(englishToken, "Search articles", db.dataSource());
            assertTitle(chineseToken, "搜索文章", db.dataSource());
            assertSame(context, I18nUtil.threadLocal.get());
            assertEquals("en_US", context.getLocale());
            assertNull(AdminTokenThreadLocal.getUser());

            db.execute("update user set preferences=? where userId=1", "{\"language\":\"en_US\"}");
            assertTitle(chineseToken, "Search articles", db.dataSource());
            db.execute("update user set preferences=null where userId=1");
            db.putWebsite("language", "zh_CN");
            assertTitle(chineseToken, "搜索文章", db.dataSource());
            assertTitle(englishToken, "Search articles", db.dataSource());
            for (String preferences : List.of("{}", "{broken", "{\"language\":\"xx\"}")) {
                db.execute("update user set preferences=? where userId=1", preferences);
                assertTitle(chineseToken, "搜索文章", db.dataSource());
            }
            db.putWebsite("language", "en_US");
            assertTitle(chineseToken, "Search articles", db.dataSource());
            Recorder rejected = call("bad-token", "tools/list", "{}", db.dataSource());
            assertEquals(401, rejected.status);
            assertTrue(rejected.headers.get("WWW-Authenticate").contains("resource_metadata="));
            assertFalse(rejected.headers.get("WWW-Authenticate").contains("scope="));
            assertSame(context, I18nUtil.threadLocal.get());
        } finally {
            if (previous == null) I18nUtil.removeI18n(); else I18nUtil.threadLocal.set(previous);
        }
    }

    @Test public void oauthUsesTheSameLanguageForInstructionsErrorsAndToolsWithoutTranslatingArticles() throws Exception {
        try (InMemoryZrLogDatabase db = InMemoryZrLogDatabase.open()) {
            applicationConfig = db.config();
            String token = oauthToken(db.dataSource());
            db.execute("update user set preferences=? where userId=1", "{\"language\":\"zh_CN\"}");
            AdminTokenThreadLocal.remove();
            assertTitle(token, "搜索文章", db.dataSource());
            JsonObject initialize = call(token, "initialize", "{\"protocolVersion\":\"2025-11-25\",\"capabilities\":{},\"clientInfo\":{}}", db.dataSource()).result();
            assertTrue(initialize.get("instructions").getAsString().startsWith("ZrLog Test\n\n按授权搜索和读取文章"));
            assertEquals("ZrLog Test", initialize.getAsJsonObject("serverInfo").get("title").getAsString());
            Recorder unknown = call(token, "tools/call", "{\"name\":\"unknown\"}", db.dataSource());
            assertEquals(-32602, unknown.body.getAsJsonObject("error").get("code").getAsInt());
            assertEquals("未知工具", unknown.body.getAsJsonObject("error").get("message").getAsString());
            assertToolError(call(token, "tools/call", "{\"name\":\"read_article\",\"arguments\":{\"id\":999}}", db.dataSource()).result(), "文章不可用");
            assertToolError(call(token, "tools/call", "{\"name\":\"search_articles\",\"arguments\":{\"limit\":11}}", db.dataSource()).result(), "知识库工具参数无效");
            db.execute("insert into log(logId,userId,typeId,title,markdown,rubbish,privacy) values(1,1,1,?,?,false,false)",
                    "Original English title", "Original English content");
            JsonObject article = call(token, "tools/call", "{\"name\":\"read_article\",\"arguments\":{\"id\":1}}", db.dataSource()).result().getAsJsonObject("structuredContent");
            assertEquals("Original English content", article.get("content").getAsString());
            assertEquals("Original English title", article.getAsJsonObject("source").get("title").getAsString());
            db.execute("update user set preferences=? where userId=1", "{\"language\":\"en_US\"}");
            assertTitle(token, "Search articles", db.dataSource());
            assertToolError(call(token, "tools/call", "{\"name\":\"read_article\",\"arguments\":{\"id\":999}}", db.dataSource()).result(), "Article unavailable");
        }
    }

    @Test public void exposesAndExecutesAuthorizedWritesThroughTheBearerTransport() throws Exception {
        try (InMemoryZrLogDatabase db = InMemoryZrLogDatabase.open()) {
            applicationConfig = db.config();
            db.execute("update user set preferences=? where userId=1", "{\"language\":\"en_US\"}");
            PersonalTokenModels.Create input = new PersonalTokenModels.Create();input.name="writer";input.permissionMode="custom";
            input.permissions=List.of("article.read","article.create","article.update","article.publish","taxonomy.read");
            String token =
                    new PersonalAccessTokenService(
                                    new OAuthService(
                                                    ApplicationServicesFixture.backend(db.config())
                                                            ::configured,
                                                    ApplicationServicesFixture.available(
                                                            db.config(), McpWebSetup.class),
                                                    AccountServicesFixture.component(db.dataSource())
                                                            .permissions(),
                                                    AccountServicesFixture.component(db.dataSource()).security(),
                                                    tokenResource ->
                                                            new PersonalAccessTokenService(
                                                                    tokenResource,
                                                                    AccountServicesFixture
                                                                            .component(db.dataSource())
                                                                            .permissions(),
                                                                    AccountServicesFixture
                                                                            .component(db.dataSource())
                                                                            .security()),
                                                    AccountServicesFixture.component(db.dataSource()).backend())
                                            .mcpResource(),
                                    AccountServicesFixture.component(db.dataSource()).permissions(),
                                    AccountServicesFixture.component(db.dataSource()).security())
                            .create(input)
                            .token;
            AdminTokenThreadLocal.remove();
            JsonObject created=call(token,"tools/call","{\"name\":\"create_article\",\"arguments\":{\"title\":\"Remote draft\",\"typeId\":1,\"status\":\"draft\",\"content\":\""+"a".repeat(20000)+"\"}}", db.dataSource()).result();
            assertFalse(created.toString(),created.get("isError").getAsBoolean());
            long id=created.getAsJsonObject("structuredContent").get("id").getAsLong();
            JsonObject detail=call(token,"tools/call","{\"name\":\"get_article\",\"arguments\":{\"id\":"+id+"}}", db.dataSource()).result();
            assertEquals(0,detail.getAsJsonObject("structuredContent").get("version").getAsInt());
            JsonObject published=call(token,"tools/call","{\"name\":\"publish_article\",\"arguments\":{\"id\":"+id+",\"version\":0}}", db.dataSource()).result();
            assertFalse(published.toString(),published.get("isError").getAsBoolean());
            assertEquals("published",published.getAsJsonObject("structuredContent").get("status").getAsString());
            assertNull(AdminTokenThreadLocal.getUser());
            login(1);
            String taxonomy=oauthToken("taxonomy:read", db.dataSource());
            AdminTokenThreadLocal.remove();
            JsonArray tools=call(taxonomy,"tools/list","{}", db.dataSource()).result().getAsJsonArray("tools");
            assertEquals(2,tools.size());assertEquals("list_categories",tools.get(0).getAsJsonObject().get("name").getAsString());
            assertFalse(call(taxonomy,"tools/call","{\"name\":\"list_categories\",\"arguments\":{}}", db.dataSource()).result().get("isError").getAsBoolean());
            assertTrue(call(taxonomy,"tools/call","{\"name\":\"create_article\",\"arguments\":{}}", db.dataSource()).result().get("isError").getAsBoolean());
        }
    }

    @Test public void oauthWriteScopeDoesNotGrantPublishOrOtherAuthorsArticles() throws Exception {
        try (InMemoryZrLogDatabase db=InMemoryZrLogDatabase.open()) {
            applicationConfig = db.config();
            String token=oauthToken("articles:write", db.dataSource());
            db.execute("insert into user(userId,userName,role) values(2,'other','author')");
            db.execute("insert into log(logId,userId,typeId,title,content,rubbish,privacy,version) values(10,2,1,'Other','Text',true,false,0)");
            db.execute("insert into log(logId,userId,typeId,title,content,rubbish,privacy,version) values(11,1,1,'Live','Text',false,false,0)");
            AdminTokenThreadLocal.remove();
            assertEquals(2,call(token,"tools/list","{}", db.dataSource()).result().getAsJsonArray("tools").size());
            assertTrue(call(token,"tools/call","{\"name\":\"update_article\",\"arguments\":{\"id\":10,\"version\":0,\"title\":\"Changed\"}}", db.dataSource()).result().get("isError").getAsBoolean());
            assertTrue(call(token,"tools/call","{\"name\":\"create_article\",\"arguments\":{\"title\":\"Denied\",\"typeId\":1,\"status\":\"published\"}}", db.dataSource()).result().get("isError").getAsBoolean());
            assertEquals("Other",db.scalar("select title from log where logId=10"));
            assertTrue(call(token,"tools/call","{\"name\":\"update_article\",\"arguments\":{\"id\":11,\"version\":0,\"title\":\"Changed\"}}", db.dataSource()).result().get("isError").getAsBoolean());
            assertTrue(call(token,"tools/call","{\"name\":\"update_article\",\"arguments\":{\"id\":11,\"version\":0,\"status\":\"draft\"}}", db.dataSource()).result().get("isError").getAsBoolean());
            assertEquals("Live",db.scalar("select title from log where logId=11"));
            assertFalse(AccountAccess.truth(db.scalar("select rubbish from log where logId=11")));
        }
    }

    @Test public void recordsClientDetailsForPersonalTokenArticleWrites() throws Exception {
        assertArticleAuditClientDetails(false);
    }

    @Test public void recordsClientDetailsForOAuthArticleWrites() throws Exception {
        assertArticleAuditClientDetails(true);
    }

    private void assertArticleAuditClientDetails(boolean useOAuth) throws Exception {
        try (InMemoryZrLogDatabase db = InMemoryZrLogDatabase.open()) {
            applicationConfig = db.config();
            String token;
            if (useOAuth) {
                token = oauthToken("articles:write articles:publish", db.dataSource());
            } else {
                PersonalTokenModels.Create input = new PersonalTokenModels.Create();
                input.name = "Audit writer"; input.permissionMode = "inherit";
                token =
                        new PersonalAccessTokenService(
                                        new OAuthService(
                                                        ApplicationServicesFixture.backend(
                                                                        db.config())
                                                                ::configured,
                                                        ApplicationServicesFixture.available(
                                                                db.config(), McpWebSetup.class),
                                                        AccountServicesFixture.component(db.dataSource())
                                                                .permissions(),
                                                        AccountServicesFixture.component(db.dataSource())
                                                                .security(),
                                                        tokenResource ->
                                                                new PersonalAccessTokenService(
                                                                        tokenResource,
                                                                        AccountServicesFixture
                                                                                .component(db.dataSource())
                                                                                .permissions(),
                                                                        AccountServicesFixture
                                                                                .component(db.dataSource())
                                                                                .security()),
                                                        AccountServicesFixture.component(db.dataSource())
                                                                .backend())
                                                .mcpResource(),
                                        AccountServicesFixture.component(db.dataSource()).permissions(),
                                        AccountServicesFixture.component(db.dataSource()).security())
                                .create(input)
                                .token;
            }
            AdminTokenThreadLocal.remove();
            call(token, "initialize", "{\"protocolVersion\":\"2025-11-25\",\"capabilities\":{},\"clientInfo\":{\"name\":\"initialize-only\",\"version\":\"99.0\"}}", db.dataSource());
            String[][] clients = {
                    {"codex/0.42.0-beta.1 (Linux; x86_64)", "Linux", "codex", "0.42.0-beta.1"},
                    {"openai-mcp/1.0.0", "Unknown", "openai-mcp", "1.0.0"},
                    {"custom-agent", "Unknown", "custom-agent", null},
                    {"zrlogctl/0.1.42 dot/1.2-beta.1", "Unknown", "dot", "1.2-beta.1"},
                    {"zrlogctl/0.1.42 muse", "Unknown", "muse", null},
                    {"Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 Chrome/146.0.0.0 Safari/537.36", "Linux", "Chrome", "146.0.0.0"},
                    {"client/", "Unknown", null, null},
                    {null, null, null, null},
                    {"   ", null, null, null}
            };
            for (String[] client : clients) {
                JsonObject created = call(token, "tools/call", "{\"name\":\"create_article\",\"arguments\":{\"title\":\"Audit article\",\"typeId\":1,\"status\":\"draft\",\"content\":\"Text\"}}", client[0], db.dataSource()).result();
                assertFalse(created.toString(), created.get("isError").getAsBoolean());
                long id = created.getAsJsonObject("structuredContent").get("id").getAsLong();
                JsonObject updated = call(token, "tools/call", "{\"name\":\"update_article\",\"arguments\":{\"id\":" + id + ",\"version\":0,\"title\":\"Updated draft\"}}", client[0], db.dataSource()).result();
                assertFalse(updated.toString(), updated.get("isError").getAsBoolean());
                assertEquals("draft", updated.getAsJsonObject("structuredContent").get("status").getAsString());
                JsonObject published = call(token, "tools/call", "{\"name\":\"publish_article\",\"arguments\":{\"id\":" + id + ",\"version\":1}}", client[0], db.dataSource()).result();
                assertFalse(published.toString(), published.get("isError").getAsBoolean());
                assertEquals("published", published.getAsJsonObject("structuredContent").get("status").getAsString());
                JsonArray audit = JsonParser.parseString(String.valueOf(db.scalar("select value from website where name='admin_audit_log'"))).getAsJsonArray();
                for (int index = 0; index < 2; index++) {
                    JsonObject entry = audit.get(index).getAsJsonObject();
                    assertEquals(index == 0 ? "PUBLISH_ARTICLE" : "CREATE_ARTICLE", entry.get("action").getAsString());
                    assertEquals(index == 0 ? "Updated draft" : "Audit article", entry.get("content").getAsString());
                    assertEquals(1, entry.get("actorUserId").getAsInt());
                    assertEquals("127.0.0.1", entry.get("ip").getAsString());
                    assertEquals("MCP", entry.get("browser").getAsString());
                    if (client[1] != null) assertEquals(client[1], entry.get("os").getAsString());
                    assertEquals(client[2], entry.has("agentName") ? entry.get("agentName").getAsString() : null);
                    assertEquals(client[3], entry.has("agentVersion") ? entry.get("agentVersion").getAsString() : null);
                }
                var displayed =
                        new AdminAuditService(ApplicationServicesFixture.websiteKv(db.dataSource()))
                                .getRecentLogs()
                                .get(0);
                assertEquals("MCP", displayed.getBrowser());
                assertEquals(client[2], displayed.getAgentName());
                assertEquals(client[3], displayed.getAgentVersion());
                assertNull(AdminTokenThreadLocal.getUser());
            }
        }
    }

    private static void assertToolError(JsonObject result, String message) {
        assertTrue(result.get("isError").getAsBoolean());
        assertEquals(message, result.getAsJsonObject("structuredContent").get("error").getAsString());
        assertEquals(result.get("structuredContent"), JsonParser.parseString(result.getAsJsonArray("content").get(0).getAsJsonObject().get("text").getAsString()));
    }

    private void assertTitle(String token, String title, DataSourceWrapper testDataSource) throws Exception {
        Recorder response = call(token, "tools/list", "{}", testDataSource);
        assertEquals(response.body.toString(), 200, response.status);
        assertEquals(title, response.result().getAsJsonArray("tools").get(0).getAsJsonObject().getAsJsonObject("annotations").get("title").getAsString());
    }

    private String personalToken(DataSourceWrapper testDataSource) throws Exception {
        PersonalTokenModels.Create request = new PersonalTokenModels.Create();
        request.name = "MCP language test"; request.scopes = List.of("articles:read");
        return new PersonalAccessTokenService(
                        new OAuthService(
                                        ApplicationServicesFixture.backend(applicationConfig)
                                                ::configured,
                                        ApplicationServicesFixture.available(
                                                applicationConfig, McpWebSetup.class),
                                        AccountServicesFixture.component(testDataSource).permissions(),
                                        AccountServicesFixture.component(testDataSource).security(),
                                        tokenResource ->
                                                new PersonalAccessTokenService(
                                                        tokenResource,
                                                        AccountServicesFixture.component(testDataSource)
                                                                .permissions(),
                                                        AccountServicesFixture.component(testDataSource)
                                                                .security()),
                                        AccountServicesFixture.component(testDataSource).backend())
                                .mcpResource(),
                        AccountServicesFixture.component(testDataSource).permissions(),
                        AccountServicesFixture.component(testDataSource).security())
                .create(request)
                .token;
    }

    private static void login(int userId) throws Exception {
        AdminTokenVO token = new AdminTokenVO(); token.setUserId(userId); token.setSessionId("session-" + userId);
        var setter = AdminTokenThreadLocal.class.getDeclaredMethod("setAdminToken", AdminTokenVO.class);
        setter.setAccessible(true); AdminTokenThreadLocal.remove(); setter.invoke(null, token);
    }

    private String oauthToken(DataSourceWrapper testDataSource) throws Exception { return oauthToken("articles:read", testDataSource); }
    private String oauthToken(String scopes, DataSourceWrapper testDataSource) throws Exception {
        OAuthService oauth =
                new OAuthService(
                        ApplicationServicesFixture.backend(applicationConfig)::configured,
                        ApplicationServicesFixture.available(applicationConfig, McpWebSetup.class),
                        AccountServicesFixture.component(testDataSource).permissions(),
                        AccountServicesFixture.component(testDataSource).security(),
                        tokenResource ->
                                new PersonalAccessTokenService(
                                        tokenResource,
                                        AccountServicesFixture.component(testDataSource).permissions(),
                                        AccountServicesFixture.component(testDataSource).security()),
                        AccountServicesFixture.component(testDataSource).backend());
        Client client = new Client(); client.name = "MCP language test"; client.redirectUris = List.of("http://127.0.0.1:3000/callback");
        client = oauth.register(client);
        String verifier = OAuthService.random();
        AuthorizationRequest request = new AuthorizationRequest();
        request.client_id = client.clientId; request.redirect_uri = client.redirectUris.get(0);
        request.response_type = "code"; request.scope = scopes; request.resource = oauth.mcpResource();
        request.state = "language-test"; request.code_challenge = OAuthService.hash(verifier); request.code_challenge_method = "S256";
        String requestId = OAuthInterceptor.parameters(URI.create(oauth.authorize(request)).getRawQuery()).get("request_id");
        Consent consent = oauth.consent(requestId);
        Decision decision = new Decision(); decision.requestId = requestId; decision.csrf = consent.csrf;
        decision.approve = true; decision.scopes = consent.availableScopes;
        TokenRequest exchange = new TokenRequest(); exchange.grant_type = "authorization_code";
        exchange.client_id = client.clientId; exchange.redirect_uri = request.redirect_uri;
        exchange.code = OAuthInterceptor.parameters(URI.create(oauth.decide(decision).redirectUri).getRawQuery()).get("code");
        exchange.code_verifier = verifier; exchange.resource = request.resource;
        return oauth.token(exchange).access_token;
    }

    private Recorder call(String token, String method, String params, DataSourceWrapper testDataSource) throws Exception {
        return call(token, method, params, null, testDataSource);
    }

    private Recorder call(String token, String method, String params, String userAgent, DataSourceWrapper testDataSource) throws Exception {
        byte[] body = ("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"" + method + "\",\"params\":" + params + "}").getBytes(StandardCharsets.UTF_8);
        Map<String, String> headers = new HashMap<>(Map.of("Authorization", "Bearer " + token, "Accept", "application/json, text/event-stream",
                "Content-Type", "application/json", "Accept-Language", "fr-FR", "MCP-Protocol-Version", "2025-11-25"));
        if (userAgent != null) headers.put("User-Agent", userAgent);
        var serverConfig = new ServerConfig();
        var cache = new InMemoryZrLogDatabase.TestCacheService();
        serverConfig
                .getBindings()
                .instance(CacheService.class, cache)
                .instance(
                        CacheRefreshService.class,
                        ApplicationServicesFixture.cacheRefresh(cache, applicationConfig));
        serverConfig
                .getBindings()
                .instance(
                        BackendServerUrl.class,
                        ApplicationServicesFixture.backend(applicationConfig));
        serverConfig
                .getBindings()
                .instance(
                        AdminPageDataFactory.class,
                        new AdminPageDataFactory(
                                ApplicationServicesFixture.site(applicationConfig),
                                () -> "test",
                                ApplicationServicesFixture.messages(testDataSource),
                                AccountServicesFixture.component(testDataSource).permissions()));
        var data =
                DaggerDataComponent.factory()
                        .create(
                                () -> (DataSourceWrapper) applicationConfig.getDataSource(),
                                () -> true);
        var shared =
                DaggerServiceComponent.factory()
                        .create(
                                data,
                                () -> true,
                                () -> null,
                                () -> null,
                                java.util.Collections::emptyList);
        var account = DaggerAccountComponent.factory().create(
                data, shared,
                () -> true, () -> null, Collections::emptySet,
                ApplicationServicesFixture.site(applicationConfig), () -> "test",
                serverConfig::getPort, serverConfig::getContextPath);
        serverConfig
                .getBindings()
                .instance(
                        ContentComponent.class,
                        DaggerContentComponent.factory()
                                .create(
                                        data,
                                        shared,
                                        account,
                                        ApplicationServicesFixture.capability(
                                                applicationConfig, ArticleAssistant.class),
                                        ApplicationServicesFixture.capability(
                                                applicationConfig, AttachmentStorage.class),
                                        ApplicationServicesFixture.publisher(
                                                cache, applicationConfig),
                                        markdown -> null));
        new ContentWebSetup(serverConfig)
                .setup();
        HttpRequest request = (HttpRequest) Proxy.newProxyInstance(McpInterceptorTest.class.getClassLoader(), new Class[]{HttpRequest.class}, (proxy, invoked, args) -> {
            switch (invoked.getName()) {
                case "getUri": return "/mcp";
                case "getMethod": return HttpMethod.POST;
                case "getContextPath": return "";
                case "getRemoteHost": return "127.0.0.1";
                case "getHeaderMap": return headers;
                case "getHeader": return headers.get(args[0]);
                case "getRequestBodyByteBuffer": return ByteBuffer.wrap(body);
                default: throw new UnsupportedOperationException(invoked.getName());
            }
        });
        Recorder recorder = new Recorder();
        var interceptor = DaggerAccessComponent.factory().create(
                data,
                shared,
                account,
                ApplicationServicesFixture.available(applicationConfig, McpWebSetup.class),
                () -> { throw new IllegalStateException("MCP must not use the OAuth dispatcher"); },
                () -> serverConfig.getBindings().build().get(ContentToolFactory.class),
                ApplicationServicesFixture.siteTitle(applicationConfig)).mcp();
        var config = applicationConfig;
        {
            assertFalse(interceptor.doInterceptor(request, recorder.response()));
        }
        return recorder;
    }

    private static class Recorder {
        int status = 200;
        JsonObject body;
        Map<String, String> headers = new HashMap<>();
        JsonObject result() { return body.getAsJsonObject("result"); }
        HttpResponse response() {
            return (HttpResponse) Proxy.newProxyInstance(getClass().getClassLoader(), new Class[]{HttpResponse.class}, (proxy, method, args) -> {
                if (method.getName().equals("write")) {
                    status = (Integer) args[1];
                    body = JsonParser.parseString(new String(((InputStream) args[0]).readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
                } else if (method.getName().equals("renderCode")) status = (Integer) args[0];
                else if (method.getName().equals("addHeader")) headers.put((String) args[0], (String) args[1]);
                return null;
            });
        }
    }
}
