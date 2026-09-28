package com.zrlog.admin.business.service;

import com.google.gson.*;
import com.zrlog.admin.business.knowledge.*;
import com.zrlog.admin.support.InMemoryZrLogDatabase;
import com.zrlog.data.security.AccountAccess;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class McpServiceTest {
    private final McpService service = new McpService(() -> "我的博客");
    private KnowledgeService knowledge() { return new KnowledgeService(()->{try{return AccountAccess.load(1);}catch(Exception e){throw new RuntimeException(e);}},Set.of("articles:read"),()->"https://blog.example"); }
    private McpService.Reply request(String method,String params) throws Exception { return service.handle("{\"jsonrpc\":\"2.0\",\"id\":\"client-1\",\"method\":\""+method+"\",\"params\":"+params+"}",knowledge()); }
    @Test public void negotiatesVersionsAdvertisesOnlyImplementedToolsAndAcceptsNotifications() throws Exception {
        for(String version:List.of("2025-03-26","2025-06-18","2025-11-25","2099-01-01")) {
            JsonObject result=request("initialize","{\"protocolVersion\":\""+version+"\",\"capabilities\":{},\"clientInfo\":{\"name\":\"test\",\"version\":\"1\"}}").body.getAsJsonObject("result");
            assertEquals(version.startsWith("2099")?McpService.LATEST:version,result.get("protocolVersion").getAsString());
            assertEquals(Set.of("tools"),result.getAsJsonObject("capabilities").keySet());
            JsonObject serverInfo = result.getAsJsonObject("serverInfo");
            assertEquals("zrlog-knowledge", serverInfo.get("name").getAsString());
            if ("2025-03-26".equals(version)) assertFalse(serverInfo.has("title"));
            else assertEquals("我的博客", serverInfo.get("title").getAsString());
        }
        assertEquals(2,request("tools/list","{}").body.getAsJsonObject("result").getAsJsonArray("tools").size());
        assertEquals(202,service.handle("{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}",knowledge()).status);
        assertEquals("client-1",request("ping","{}").body.get("id").getAsString());
    }
    @Test public void validatesJsonRpcAndDistinguishesProtocolAndToolErrors() throws Exception {
        for(String invalid:List.of("[]","null","{}","{\"jsonrpc\":\"2.0\",\"id\":null,\"method\":\"ping\"}")) assertEquals(-32600,service.handle(invalid,knowledge()).body.getAsJsonObject("error").get("code").getAsInt());
        for(String malformed:List.of("{broken", "{'jsonrpc':'2.0'}", "{} {}")) assertEquals(-32700,service.handle(malformed,knowledge()).body.getAsJsonObject("error").get("code").getAsInt());
        assertEquals(-32601,request("delete_article","{}").body.getAsJsonObject("error").get("code").getAsInt());
        assertEquals(-32602,request("tools/call","{\"name\":\"unknown\"}").body.getAsJsonObject("error").get("code").getAsInt());
        try(InMemoryZrLogDatabase db=InMemoryZrLogDatabase.open()) {
            JsonObject tool=request("tools/call","{\"name\":\"read_article\",\"arguments\":{\"id\":123}}").body.getAsJsonObject("result");
            assertTrue(tool.get("isError").getAsBoolean());
            assertEquals(tool.get("structuredContent"),JsonParser.parseString(tool.getAsJsonArray("content").get(0).getAsJsonObject().get("text").getAsString()));
            assertFalse(request("tools/call","{\"name\":\"search_articles\",\"arguments\":{}}").body.getAsJsonObject("result").get("isError").getAsBoolean());
        }
    }

    @Test public void localizesDescriptionsWithoutChangingSchemasOrProtocolIdentifiers() throws Exception {
        JsonArray baseline = null;
        for (String language : List.of("zh_CN", "en_US", "zh_CN")) {
            McpService localized = new McpService(() -> "My original blog", language);
            String body = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/list\"}";
            JsonArray tools = localized.handle(body, null).body.getAsJsonObject("result").getAsJsonArray("tools");
            boolean chinese = language.equals("zh_CN");
            assertEquals(chinese ? "搜索文章" : "Search articles", tools.get(0).getAsJsonObject().getAsJsonObject("annotations").get("title").getAsString());
            assertEquals(chinese ? "读取文章" : "Read article", tools.get(1).getAsJsonObject().getAsJsonObject("annotations").get("title").getAsString());
            for (JsonElement tool : tools) {
                JsonObject definition = tool.getAsJsonObject();
                assertLocalized(definition.remove("description").getAsString(), chinese);
                definition.getAsJsonObject("annotations").remove("title");
                for (JsonElement property : definition.getAsJsonObject("inputSchema").getAsJsonObject("properties").asMap().values()) {
                    assertLocalized(property.getAsJsonObject().remove("description").getAsString(), chinese);
                }
            }
            if (baseline == null) baseline = tools; else assertEquals(baseline, tools);
            for (String version : McpService.VERSIONS) {
                String initialize = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{\"protocolVersion\":\"" + version + "\",\"capabilities\":{},\"clientInfo\":{}}}";
                JsonObject result = localized.handle(initialize, null).body.getAsJsonObject("result");
                assertLocalized(result.get("instructions").getAsString(), chinese);
                assertTrue(result.get("instructions").getAsString().startsWith("My original blog\n\n"));
                assertEquals(version, result.get("protocolVersion").getAsString());
                assertEquals("zrlog-knowledge", result.getAsJsonObject("serverInfo").get("name").getAsString());
                if (!version.equals("2025-03-26")) assertEquals("My original blog", result.getAsJsonObject("serverInfo").get("title").getAsString());
            }
            JsonObject error = localized.handle("{broken", null).body.getAsJsonObject("error");
            assertEquals(-32700, error.get("code").getAsInt());
            assertEquals(chinese ? "JSON 解析失败" : "Parse error", error.get("message").getAsString());
        }
    }

    @Test public void instructionsAndDisplayTitleFollowWebsiteRenamesAndUseTheSameFallback() throws Exception {
        java.util.concurrent.atomic.AtomicReference<String> title = new java.util.concurrent.atomic.AtomicReference<>();
        McpService localized = new McpService(title::get, "en_US");
        String initialize = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{\"protocolVersion\":\"2025-11-25\",\"capabilities\":{},\"clientInfo\":{}}}";
        for (String name : Arrays.asList(null, " ", "我的网站", "Renamed site")) {
            title.set(name);
            JsonObject result = localized.handle(initialize, null).body.getAsJsonObject("result");
            String expected = name == null || name.isBlank() ? "ZrLog" : name;
            assertEquals(expected, result.getAsJsonObject("serverInfo").get("title").getAsString());
            assertTrue(result.get("instructions").getAsString().startsWith(expected + "\n\nWhen citing articles"));
            assertFalse(result.get("instructions").getAsString().contains("Read-only blog knowledge base"));
        }
    }

    private static void assertLocalized(String message, boolean chinese) {
        assertFalse(message.isBlank());
        assertFalse(message.startsWith("admin."));
        assertEquals(message, chinese, message.codePoints().anyMatch(c -> c >= 0x4E00 && c <= 0x9FFF));
    }
}
