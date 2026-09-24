package com.zrlog.admin.business.service;

import com.google.gson.*;
import com.zrlog.admin.business.knowledge.*;
import com.zrlog.admin.support.InMemoryZrLogDatabase;
import com.zrlog.data.security.AccountAccess;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class McpServiceTest {
    private final McpService service = new McpService();
    private KnowledgeService knowledge() { return new KnowledgeService(()->{try{return AccountAccess.load(1);}catch(Exception e){throw new RuntimeException(e);}},Set.of("articles:read"),()->"https://blog.example"); }
    private McpService.Reply request(String method,String params) throws Exception { return service.handle("{\"jsonrpc\":\"2.0\",\"id\":\"client-1\",\"method\":\""+method+"\",\"params\":"+params+"}",knowledge()); }
    @Test public void negotiatesVersionsAdvertisesOnlyImplementedToolsAndAcceptsNotifications() throws Exception {
        for(String version:List.of("2025-03-26","2025-06-18","2025-11-25","2099-01-01")) {
            JsonObject result=request("initialize","{\"protocolVersion\":\""+version+"\",\"capabilities\":{},\"clientInfo\":{\"name\":\"test\",\"version\":\"1\"}}").body.getAsJsonObject("result");
            assertEquals(version.startsWith("2099")?McpService.LATEST:version,result.get("protocolVersion").getAsString());
            assertEquals(Set.of("tools"),result.getAsJsonObject("capabilities").keySet());
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
}
