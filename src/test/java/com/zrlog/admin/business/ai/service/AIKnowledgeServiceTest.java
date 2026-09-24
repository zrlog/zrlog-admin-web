package com.zrlog.admin.business.ai.service;

import com.google.gson.*;
import com.zrlog.admin.business.ai.model.*;
import com.zrlog.admin.business.knowledge.*;
import com.zrlog.admin.business.knowledge.KnowledgeModels.*;
import com.zrlog.admin.business.rest.base.AIWebSiteInfo;
import com.zrlog.admin.support.InMemoryZrLogDatabase;
import com.zrlog.data.security.AccountAccess;
import org.junit.Test;
import java.io.*;
import java.util.*;
import static org.junit.Assert.*;

public class AIKnowledgeServiceTest {
    @Test public void keepsProviderSignaturesAndUsesCompatibleQwenToolRequests() throws Exception {
        AIWebSiteInfo info=new AIWebSiteInfo();info.setAi_provider(AIProviderType.QWEN);info.setAi_reasoning_enabled(true);
        AIProviderRequests.Message message=new AIProviderRequests.Message("assistant",null);
        AIProviderRequests.ToolCall call=new Gson().fromJson("{\"id\":\"a\",\"type\":\"function\",\"function\":{\"name\":\"search_articles\",\"arguments\":\"{}\"},\"extra_content\":{\"google\":{\"thought_signature\":\"opaque-signature\"}}}",AIProviderRequests.ToolCall.class);
        message.toolCalls=List.of(call);message.reasoningContent="provider reasoning";
        JsonObject request=JsonParser.parseString(new AIKnowledgeService().request(info,List.of(message),true)).getAsJsonObject();
        assertFalse(request.get("enable_thinking").getAsBoolean());
        assertTrue(request.toString().contains("opaque-signature"));assertTrue(request.toString().contains("provider reasoning"));
    }
    @Test public void cancelsOversizeProviderBodies() {
        AIKnowledgeService.BoundedBody body=new AIKnowledgeService.BoundedBody();boolean[] cancelled={false};
        body.onSubscribe(new java.util.concurrent.Flow.Subscription(){public void request(long n){}public void cancel(){cancelled[0]=true;}});
        body.onNext(List.of(java.nio.ByteBuffer.allocate(1024*1024+1)));
        assertTrue(cancelled[0]);assertTrue(body.getBody().toCompletableFuture().isCompletedExceptionally());
    }
    private ChatRequest input() { ChatRequest r=new ChatRequest();r.input="What is in the blog?";return r; }
    private KnowledgeService knowledge() { return new KnowledgeService(()->{try{return AccountAccess.load(1);}catch(Exception e){throw new RuntimeException(e);}},Set.of("articles:read"),()->"https://blog.example"); }
    private static String tool(String name,String args) { return "{\"finish_reason\":\"tool_calls\",\"message\":{\"tool_calls\":[{\"id\":\"call-1\",\"type\":\"function\",\"function\":{\"name\":\""+name+"\",\"arguments\":"+new Gson().toJson(args)+"}}]}}"; }
    private static class Model extends AIKnowledgeService {
        final List<String> responses; final List<JsonObject> requests=new ArrayList<>();
        Model(String... responses){this.responses=List.of(responses);}
        @Override protected AIProviderResponses.Choice complete(AIWebSiteInfo info,String body) {
            requests.add(JsonParser.parseString(body).getAsJsonObject());
            return new Gson().fromJson(responses.get(Math.min(requests.size()-1,responses.size()-1)),AIProviderResponses.Choice.class);
        }
    }
    @Test public void modelSearchesReadsThenAnswersWithServerSourcesWithoutSavingSharedHistory() throws Exception {
        try(InMemoryZrLogDatabase db=InMemoryZrLogDatabase.open()) {
            db.execute("insert into log(logId,userId,typeId,title,markdown,rubbish,privacy) values(?,?,?,?,?,?,?)",1,1,1,"Deployment","Use the deploy command.",false,false);
            Model model=new Model(tool("search_articles","{}"),tool("read_article","{\"id\":1}"),"{\"finish_reason\":\"stop\",\"message\":{\"content\":\"Use deploy. [Source](https://blog.example/1)\"}}");
            ByteArrayOutputStream out=new ByteArrayOutputStream();int[] auth={0};
            model.run(input(),new AIWebSiteInfo(),knowledge(),out,()->auth[0]++);
            assertEquals(3,model.requests.size());assertEquals(4,auth[0]);
            assertEquals(2,model.requests.get(0).getAsJsonArray("tools").size());
            JsonArray messages=model.requests.get(2).getAsJsonArray("messages");
            JsonObject last=messages.get(messages.size()-1).getAsJsonObject();
            assertEquals("tool",last.get("role").getAsString());assertEquals("call-1",last.get("tool_call_id").getAsString());
            assertTrue(last.get("content").getAsString().contains("Use the deploy command."));
            assertTrue(out.toString().contains("\"sources\":[{\"id\":1"));assertTrue(out.toString().contains("\"type\":\"done\""));
        }
    }
    @Test public void unknownToolsAndBadArgumentsAreToolErrorsAndLoopsAreBounded() throws Exception {
        try(InMemoryZrLogDatabase db=InMemoryZrLogDatabase.open()) {
            Model model=new Model(tool("delete_article","{}"));
            assertThrows(com.zrlog.admin.business.ai.exception.AIResponseException.class,()->model.run(input(),new AIWebSiteInfo(),knowledge(),new ByteArrayOutputStream(),()->{}));
            assertEquals(5,model.requests.size());assertFalse(model.requests.get(4).has("tools"));
            assertTrue(model.requests.get(1).toString().contains("Invalid tool arguments"));
        }
    }
    @Test public void permissionChangeStopsBeforeSendingAnotherModelRequestAndDisconnectStopsWork() throws Exception {
        try(InMemoryZrLogDatabase db=InMemoryZrLogDatabase.open()) {
            Model model=new Model(tool("search_articles","{}")); int[] calls={0};
            assertThrows(com.zrlog.admin.business.exception.PermissionErrorException.class,()->model.run(input(),new AIWebSiteInfo(),knowledge(),new ByteArrayOutputStream(),()->{if(++calls[0]>1)throw new com.zrlog.admin.business.exception.PermissionErrorException();}));
            assertEquals(1,model.requests.size());
            Model disconnected=new Model(tool("search_articles","{}"));
            assertThrows(IOException.class,()->disconnected.run(input(),new AIWebSiteInfo(),knowledge(),new OutputStream(){public void write(int b)throws IOException{throw new IOException();}},()->{}));
            assertTrue(disconnected.requests.isEmpty());
        }
    }
}
