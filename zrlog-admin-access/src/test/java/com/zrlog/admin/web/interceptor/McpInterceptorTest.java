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
            Recorder rejected = call("bad-token", "tools/list", "{}");
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
            String token = oauthToken();
            db.execute("update user set preferences=? where userId=1", "{\"language\":\"zh_CN\"}");
            AdminTokenThreadLocal.remove();
            assertTitle(token, "搜索文章");
            JsonObject initialize = call(token, "initialize", "{\"protocolVersion\":\"2025-11-25\",\"capabilities\":{},\"clientInfo\":{}}").result();
            assertTrue(initialize.get("instructions").getAsString().startsWith("ZrLog Test\n\n按授权搜索和读取文章"));
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

    @Test public void exposesAndExecutesAuthorizedWritesThroughTheBearerTransport() throws Exception {
        try (InMemoryZrLogDatabase db = InMemoryZrLogDatabase.open()) {
            db.execute("update user set preferences=? where userId=1", "{\"language\":\"en_US\"}");
            PersonalTokenModels.Create input = new PersonalTokenModels.Create();input.name="writer";input.permissionMode="custom";
            input.permissions=List.of("article.read","article.create","article.update","article.publish","taxonomy.read");
            String token=new PersonalAccessTokenService(new OAuthService().mcpResource()).create(input).token;
            AdminTokenThreadLocal.remove();
            JsonObject created=call(token,"tools/call","{\"name\":\"create_article\",\"arguments\":{\"title\":\"Remote draft\",\"typeId\":1,\"status\":\"draft\",\"content\":\""+"a".repeat(20000)+"\"}}").result();
            assertFalse(created.toString(),created.get("isError").getAsBoolean());
            long id=created.getAsJsonObject("structuredContent").get("id").getAsLong();
            JsonObject detail=call(token,"tools/call","{\"name\":\"get_article\",\"arguments\":{\"id\":"+id+"}}").result();
            assertEquals(0,detail.getAsJsonObject("structuredContent").get("version").getAsInt());
            JsonObject published=call(token,"tools/call","{\"name\":\"publish_article\",\"arguments\":{\"id\":"+id+",\"version\":0}}").result();
            assertFalse(published.toString(),published.get("isError").getAsBoolean());
            assertEquals("published",published.getAsJsonObject("structuredContent").get("status").getAsString());
            assertNull(AdminTokenThreadLocal.getUser());
            login(1);
            String taxonomy=oauthToken("taxonomy:read");
            AdminTokenThreadLocal.remove();
            JsonArray tools=call(taxonomy,"tools/list","{}").result().getAsJsonArray("tools");
            assertEquals(2,tools.size());assertEquals("list_categories",tools.get(0).getAsJsonObject().get("name").getAsString());
            assertFalse(call(taxonomy,"tools/call","{\"name\":\"list_categories\",\"arguments\":{}}").result().get("isError").getAsBoolean());
            assertTrue(call(taxonomy,"tools/call","{\"name\":\"create_article\",\"arguments\":{}}").result().get("isError").getAsBoolean());
        }
    }

    @Test public void oauthWriteScopeDoesNotGrantPublishOrOtherAuthorsArticles() throws Exception {
        try (InMemoryZrLogDatabase db=InMemoryZrLogDatabase.open()) {
            String token=oauthToken("articles:write");
            db.execute("insert into user(userId,userName,role) values(2,'other','author')");
            db.execute("insert into log(logId,userId,typeId,title,content,rubbish,privacy,version) values(10,2,1,'Other','Text',true,false,0)");
            db.execute("insert into log(logId,userId,typeId,title,content,rubbish,privacy,version) values(11,1,1,'Live','Text',false,false,0)");
            AdminTokenThreadLocal.remove();
            assertEquals(2,call(token,"tools/list","{}").result().getAsJsonArray("tools").size());
            assertTrue(call(token,"tools/call","{\"name\":\"update_article\",\"arguments\":{\"id\":10,\"version\":0,\"title\":\"Changed\"}}").result().get("isError").getAsBoolean());
            assertTrue(call(token,"tools/call","{\"name\":\"create_article\",\"arguments\":{\"title\":\"Denied\",\"typeId\":1,\"status\":\"published\"}}").result().get("isError").getAsBoolean());
            assertEquals("Other",db.scalar("select title from log where logId=10"));
            assertTrue(call(token,"tools/call","{\"name\":\"update_article\",\"arguments\":{\"id\":11,\"version\":0,\"title\":\"Changed\"}}").result().get("isError").getAsBoolean());
            assertTrue(call(token,"tools/call","{\"name\":\"update_article\",\"arguments\":{\"id\":11,\"version\":0,\"status\":\"draft\"}}").result().get("isError").getAsBoolean());
            assertEquals("Live",db.scalar("select title from log where logId=11"));
            assertFalse(com.zrlog.data.security.AccountAccess.truth(db.scalar("select rubbish from log where logId=11")));
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

    private static String oauthToken() throws Exception { return oauthToken("articles:read"); }
    private static String oauthToken(String scopes) throws Exception {
        OAuthService oauth = new OAuthService();
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

    private static Recorder call(String token, String method, String params) throws Exception {
        byte[] body = ("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"" + method + "\",\"params\":" + params + "}").getBytes(StandardCharsets.UTF_8);
        Map<String, String> headers = Map.of("Authorization", "Bearer " + token, "Accept", "application/json, text/event-stream",
                "Content-Type", "application/json", "Accept-Language", "fr-FR", "MCP-Protocol-Version", "2025-11-25");
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
        assertFalse(new McpInterceptor().doInterceptor(request, recorder.response()));
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
