package com.zrlog.admin.web.interceptor;

import com.google.gson.*;
import com.hibegin.http.HttpMethod;
import com.hibegin.http.server.api.HttpRequest;
import com.hibegin.http.server.api.HttpResponse;
import com.zrlog.admin.business.security.OAuthModels.*;
import com.zrlog.admin.business.security.PersonalTokenModels;
import com.zrlog.admin.business.service.*;
import com.zrlog.admin.support.InMemoryZrLogDatabase;
import com.zrlog.admin.web.token.AdminTokenThreadLocal;
import com.zrlog.common.vo.AdminTokenVO;
import com.zrlog.common.vo.I18nVO;
import com.zrlog.util.I18nUtil;
import org.junit.Test;

import java.io.InputStream;
import java.lang.reflect.Proxy;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.junit.Assert.*;

public class McpInterceptorTest {
    @Test public void followsTokenOwnerAcrossAccountsAndPreferenceChangesWithoutBrowserSession() throws Exception {
        I18nVO previous = I18nUtil.threadLocal.get();
        try (InMemoryZrLogDatabase db = InMemoryZrLogDatabase.open()) {
            db.putWebsite("language", "en_US");
            db.execute("update user set preferences=? where userId=1", "{\"language\":\"zh_CN\"}");
            String chineseToken = personalToken();
            db.execute("insert into user(userId,userName,role,preferences) values(2,'other','admin',?)", "{\"language\":\"en_US\"}");
            login(2);
            String englishToken = personalToken();
            // An unrelated browser identity and locale must not override the Bearer token owner.
            I18nVO context = I18nUtil.addToRequest(null);
            context.setLocale("en_US");
            assertTitle(chineseToken, "搜索文章");
            assertEquals(2, AdminTokenThreadLocal.getUser().getUserId());
            AdminTokenThreadLocal.remove();
            assertTitle(englishToken, "Search articles");
            assertTitle(chineseToken, "搜索文章");
            assertSame(context, I18nUtil.threadLocal.get());
            assertEquals("en_US", context.getLocale());
            assertNull(AdminTokenThreadLocal.getUser());

            db.execute("update user set preferences=? where userId=1", "{\"language\":\"en_US\"}");
            assertTitle(chineseToken, "Search articles");
            db.execute("update user set preferences=null where userId=1");
            db.putWebsite("language", "zh_CN");
            assertTitle(chineseToken, "搜索文章");
            assertTitle(englishToken, "Search articles");
            for (String preferences : List.of("{}", "{broken", "{\"language\":\"xx\"}")) {
                db.execute("update user set preferences=? where userId=1", preferences);
                assertTitle(chineseToken, "搜索文章");
            }
            db.putWebsite("language", "en_US");
            assertTitle(chineseToken, "Search articles");
            assertEquals(401, call("bad-token", "tools/list", "{}").status);
            assertSame(context, I18nUtil.threadLocal.get());
        } finally {
            if (previous == null) I18nUtil.removeI18n(); else I18nUtil.threadLocal.set(previous);
        }
    }

    @Test public void oauthUsesTheSameLanguageForInstructionsErrorsAndToolsWithoutTranslatingArticles() throws Exception {
        try (InMemoryZrLogDatabase db = InMemoryZrLogDatabase.open()) {
            String token = oauthToken();
            db.execute("update user set preferences=? where userId=1", "{\"language\":\"zh_CN\"}");
            AdminTokenThreadLocal.remove();
            assertTitle(token, "搜索文章");
            JsonObject initialize = call(token, "initialize", "{\"protocolVersion\":\"2025-11-25\",\"capabilities\":{},\"clientInfo\":{}}").result();
            assertTrue(initialize.get("instructions").getAsString().startsWith("ZrLog Test\n\n需要引用文章时"));
            assertEquals("ZrLog Test", initialize.getAsJsonObject("serverInfo").get("title").getAsString());
            Recorder unknown = call(token, "tools/call", "{\"name\":\"unknown\"}");
            assertEquals(-32602, unknown.body.getAsJsonObject("error").get("code").getAsInt());
            assertEquals("未知工具", unknown.body.getAsJsonObject("error").get("message").getAsString());
            assertToolError(call(token, "tools/call", "{\"name\":\"read_article\",\"arguments\":{\"id\":999}}").result(), "文章不可用");
            assertToolError(call(token, "tools/call", "{\"name\":\"search_articles\",\"arguments\":{\"limit\":11}}").result(), "知识库工具参数无效");
            db.execute("insert into log(logId,userId,typeId,title,markdown,rubbish,privacy) values(1,1,1,?,?,false,false)",
                    "Original English title", "Original English content");
            JsonObject article = call(token, "tools/call", "{\"name\":\"read_article\",\"arguments\":{\"id\":1}}").result().getAsJsonObject("structuredContent");
            assertEquals("Original English content", article.get("content").getAsString());
            assertEquals("Original English title", article.getAsJsonObject("source").get("title").getAsString());
            db.execute("update user set preferences=? where userId=1", "{\"language\":\"en_US\"}");
            assertTitle(token, "Search articles");
            assertToolError(call(token, "tools/call", "{\"name\":\"read_article\",\"arguments\":{\"id\":999}}").result(), "Article unavailable");
        }
    }

    private static void assertToolError(JsonObject result, String message) {
        assertTrue(result.get("isError").getAsBoolean());
        assertEquals(message, result.getAsJsonObject("structuredContent").get("error").getAsString());
        assertEquals(result.get("structuredContent"), JsonParser.parseString(result.getAsJsonArray("content").get(0).getAsJsonObject().get("text").getAsString()));
    }

    private static void assertTitle(String token, String title) throws Exception {
        Recorder response = call(token, "tools/list", "{}");
        assertEquals(response.body.toString(), 200, response.status);
        assertEquals(title, response.result().getAsJsonArray("tools").get(0).getAsJsonObject().getAsJsonObject("annotations").get("title").getAsString());
    }

    private static String personalToken() throws Exception {
        PersonalTokenModels.Create request = new PersonalTokenModels.Create();
        request.name = "MCP language test"; request.scopes = List.of("articles:read");
        return new PersonalAccessTokenService(new OAuthService().mcpResource()).create(request).token;
    }

    private static void login(int userId) throws Exception {
        AdminTokenVO token = new AdminTokenVO(); token.setUserId(userId); token.setSessionId("session-" + userId);
        var setter = AdminTokenThreadLocal.class.getDeclaredMethod("setAdminToken", AdminTokenVO.class);
        setter.setAccessible(true); AdminTokenThreadLocal.remove(); setter.invoke(null, token);
    }

    private static String oauthToken() throws Exception {
        OAuthService oauth = new OAuthService();
        Client client = new Client(); client.name = "MCP language test"; client.redirectUris = List.of("http://127.0.0.1:3000/callback");
        client = oauth.register(client);
        String verifier = OAuthService.random();
        AuthorizationRequest request = new AuthorizationRequest();
        request.client_id = client.clientId; request.redirect_uri = client.redirectUris.get(0);
        request.response_type = "code"; request.scope = "articles:read"; request.resource = oauth.mcpResource();
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

    private static Recorder call(String token, String method, String params) throws Exception {
        byte[] body = ("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"" + method + "\",\"params\":" + params + "}").getBytes(StandardCharsets.UTF_8);
        Map<String, String> headers = Map.of("Authorization", "Bearer " + token, "Accept", "application/json, text/event-stream",
                "Content-Type", "application/json", "Accept-Language", "fr-FR", "MCP-Protocol-Version", "2025-11-25");
        HttpRequest request = (HttpRequest) Proxy.newProxyInstance(McpInterceptorTest.class.getClassLoader(), new Class[]{HttpRequest.class}, (proxy, invoked, args) -> {
            switch (invoked.getName()) {
                case "getUri": return "/mcp";
                case "getMethod": return HttpMethod.POST;
                case "getHeader": return headers.get(args[0]);
                case "getRequestBodyByteBuffer": return ByteBuffer.wrap(body);
                default: throw new UnsupportedOperationException(invoked.getName());
            }
        });
        Recorder recorder = new Recorder();
        assertFalse(new McpInterceptor().doInterceptor(request, recorder.response()));
        return recorder;
    }

    private static class Recorder {
        int status = 200;
        JsonObject body;
        JsonObject result() { return body.getAsJsonObject("result"); }
        HttpResponse response() {
            return (HttpResponse) Proxy.newProxyInstance(getClass().getClassLoader(), new Class[]{HttpResponse.class}, (proxy, method, args) -> {
                if (method.getName().equals("write")) {
                    status = (Integer) args[1];
                    body = JsonParser.parseString(new String(((InputStream) args[0]).readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
                } else if (method.getName().equals("renderCode")) status = (Integer) args[0];
                return null;
            });
        }
    }
}
