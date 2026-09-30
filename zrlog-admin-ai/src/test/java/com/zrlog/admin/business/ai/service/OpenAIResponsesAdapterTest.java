package com.zrlog.admin.business.ai.service;

import com.google.gson.*;
import com.zrlog.admin.business.ai.exception.AIIncompleteResponseException;
import com.zrlog.admin.business.ai.exception.AIResponseException;
import com.zrlog.admin.business.ai.model.AIProviderRequests;
import com.zrlog.admin.business.ai.model.AIProviderResponses;
import com.zrlog.admin.business.ai.model.OpenAIResponses;
import com.zrlog.admin.business.knowledge.ContentToolCatalog;
import org.junit.Test;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

import static org.junit.Assert.*;

public class OpenAIResponsesAdapterTest {
    private final Gson gson = new Gson();
    private final OpenAIResponsesAdapter adapter = new OpenAIResponsesAdapter();
    static final String REASONING = "{\"type\":\"reasoning\",\"id\":\"rs_1\",\"summary\":[{\"type\":\"summary_text\",\"text\":\"检查文章🙂\"}],\"encrypted_content\":\"opaque-state\"}";
    static final String MESSAGE = "{\"type\":\"message\",\"id\":\"msg_1\",\"role\":\"assistant\",\"status\":\"completed\",\"phase\":\"final_answer\",\"content\":[{\"type\":\"output_text\",\"text\":\"Answer\",\"annotations\":[]}]}";
    static final String CALL = "{\"type\":\"function_call\",\"id\":\"fc_1\",\"call_id\":\"call_1\",\"name\":\"read_article\",\"arguments\":\"{\\\"id\\\":7}\",\"status\":\"completed\"}";

    static String completed(String output) { return "{\"status\":\"completed\",\"output\":[" + output + "]}"; }
    static String frame(String json) { return "event: ignored\r\ndata: " + json + "\r\n\r\n"; }
    static String terminal(String output) { return frame("{\"type\":\"response.completed\",\"response\":" + completed(output) + "}"); }
    private AIProviderResponses.Choice read(String body, boolean sse, boolean summaries, List<String> events) throws IOException {
        return adapter.read(new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8)), sse, summaries,
                (type, text) -> events.add(type + ":" + text));
    }
    private AIProviderRequests.CompletionRequest completion() {
        AIProviderRequests.CompletionRequest request = new AIProviderRequests.CompletionRequest();
        request.setModel("gpt-6-astra"); request.setStream(true); request.setMaxCompletionTokens(8192);
        request.setMessages(new ArrayList<>(List.of(new AIProviderRequests.Message("system", "Prompt"),
                new AIProviderRequests.Message("user", "Question"))));
        return request;
    }

    @Test public void buildsStatelessResponsesRequestAndPreservesOptionalToolFields() {
        var completion = completion();
        completion.tools = List.of(gson.fromJson("{\"type\":\"function\",\"function\":{\"name\":\"read_article\",\"description\":\"Read\",\"parameters\":{\"type\":\"object\",\"properties\":{\"id\":{\"type\":\"integer\"}}}}}", AIProviderRequests.Tool.class));
        JsonObject body = gson.toJsonTree(adapter.request(completion, true)).getAsJsonObject();
        assertEquals("gpt-6-astra", body.get("model").getAsString());
        assertEquals("auto", body.getAsJsonObject("reasoning").get("summary").getAsString());
        assertFalse(body.getAsJsonObject("reasoning").has("effort"));
        assertEquals(8192, body.get("max_output_tokens").getAsInt());
        assertTrue(body.get("stream").getAsBoolean()); assertFalse(body.get("store").getAsBoolean());
        assertEquals("reasoning.encrypted_content", body.getAsJsonArray("include").get(0).getAsString());
        assertFalse(body.has("messages")); assertFalse(body.has("max_completion_tokens"));
        assertEquals("system", body.getAsJsonArray("input").get(0).getAsJsonObject().get("role").getAsString());
        JsonObject tool = body.getAsJsonArray("tools").get(0).getAsJsonObject();
        assertEquals("read_article", tool.get("name").getAsString());
        assertFalse(tool.has("function")); assertFalse(tool.get("strict").getAsBoolean());
        assertFalse(tool.getAsJsonObject("parameters").has("required"));
        assertNull(adapter.request(completion, false).reasoning);
        for (String model : List.of("gpt-4.1", "gpt-4o", "gpt-5-chat-latest")) {
            completion.setModel(model); assertNull(adapter.request(completion, true).reasoning);
        }
        for (String model : List.of("gpt-5.4", "gpt-6-astra-2026-09-01", "o3", "o4-mini")) {
            completion.setModel(model); assertNotNull(adapter.request(completion, true).reasoning);
        }
    }

    @Test public void emitsSummaryBeforeCompletionAndDoesNotDuplicateSnapshots() throws Exception {
        try (PipedInputStream input = new PipedInputStream(4096); PipedOutputStream output = new PipedOutputStream(input)) {
            CountDownLatch first = new CountDownLatch(1);
            List<String> events = new CopyOnWriteArrayList<>();
            ExecutorService pool = Executors.newSingleThreadExecutor();
            try {
                Future<AIProviderResponses.Choice> result = pool.submit(() -> adapter.read(input, true, true, (type, text) -> {
                    events.add(type + ":" + text); first.countDown();
                }));
                byte[] bytes = frame("{\"type\":\"response.reasoning_summary_text.delta\",\"item_id\":\"rs_1\",\"summary_index\":0,\"delta\":\"检查文章🙂\"}").getBytes(StandardCharsets.UTF_8);
                for (byte value : bytes) output.write(value);
                output.flush();
                assertTrue(first.await(3, TimeUnit.SECONDS)); assertFalse(result.isDone());
                output.write((frame("{\"type\":\"response.reasoning_summary_text.done\",\"text\":\"检查文章🙂\"}")
                        + frame("{\"type\":\"response.output_text.delta\",\"delta\":\"Answer\"}")
                        + terminal(REASONING + "," + MESSAGE)).getBytes(StandardCharsets.UTF_8)); output.flush();
                var reply = result.get(3, TimeUnit.SECONDS).getMessage();
                assertEquals("检查文章🙂", reply.getReasoningText()); assertEquals("Answer", reply.getContent());
                assertEquals(List.of("reasoning_delta:检查文章🙂", "delta:Answer"), events);
                assertFalse(events.toString().contains("opaque-state"));
            } finally { pool.shutdownNow(); }
        }
    }

    @Test public void replaysOpaqueReasoningPhaseAndToolCallIdsWithoutDuplicatingAssistantContent() throws Exception {
        List<String> events = new ArrayList<>();
        var reply = read(terminal(REASONING + "," + MESSAGE + "," + CALL), true, true, events).getMessage();
        assertEquals("call_1", reply.toolCalls.get(0).id);
        var completion = completion();
        var assistant = new AIProviderRequests.Message("assistant", reply.getContent());
        assistant.responsesOutput = reply.responsesOutput; assistant.toolCalls = reply.toolCalls;
        var tool = new AIProviderRequests.Message("tool", "{\"title\":\"Article\"}"); tool.toolCallId = "call_1";
        completion.getMessages().addAll(List.of(assistant, tool));
        var input = adapter.request(completion, true).input;
        assertEquals(6, input.size());
        assertEquals("opaque-state", input.get(2).encrypted_content);
        assertEquals("final_answer", input.get(3).phase);
        assertEquals("fc_1", input.get(4).id); assertEquals("call_1", input.get(4).call_id);
        assertEquals("function_call_output", input.get(5).type); assertEquals("call_1", input.get(5).call_id);
        assertEquals("{\"title\":\"Article\"}", input.get(5).output);
        // Old durable Chat Completions runs still resume after an upgrade.
        assistant.responsesOutput = null;
        input = adapter.request(completion, true).input;
        assertEquals(5, input.size()); assertEquals("call_1", input.get(3).call_id);
        assertEquals("assistant", input.get(2).role);
        assertEquals("output_text", input.get(2).content.get(0).type);
        assertEquals("Answer", input.get(2).content.get(0).text);
        assertEquals("function_call_output", input.get(4).type);
        assertEquals("call_1", input.get(4).call_id);
    }

    @Test public void disablingSummariesDropsVisibleContentButKeepsOpaqueContinuationAndJsonFallback() throws Exception {
        for (boolean sse : List.of(false, true)) {
            List<String> events = new ArrayList<>();
            String body = sse ? frame("{\"type\":\"response.reasoning_summary_text.delta\",\"delta\":\"hidden\"}") + terminal(REASONING + "," + MESSAGE)
                    : completed(REASONING + "," + MESSAGE);
            var reply = read(body, sse, false, events).getMessage();
            assertNull(reply.getReasoningText()); assertEquals(List.of("delta:Answer"), events);
            assertEquals("opaque-state", reply.responsesOutput.get(0).encrypted_content);
            assertTrue(reply.responsesOutput.get(0).summary.isEmpty());
            assertFalse(gson.toJson(reply).contains("检查文章"));
        }
    }

    @Test public void supportsMultipleSummaryPartsAndRefusals() throws Exception {
        String reasoning = REASONING.replace("检查文章🙂", "First").replace("}],", "},{\"type\":\"summary_text\",\"text\":\"Second\"}],");
        String message = MESSAGE.replace("\"output_text\",\"text\":\"Answer\",\"annotations\":[]", "\"refusal\",\"refusal\":\"Cannot comply\"");
        List<String> events = new ArrayList<>();
        var reply = read(frame("{\"type\":\"response.reasoning_summary_text.delta\",\"item_id\":\"rs_1\",\"summary_index\":0,\"delta\":\"First\"}")
                + frame("{\"type\":\"response.reasoning_summary_text.delta\",\"item_id\":\"rs_1\",\"summary_index\":1,\"delta\":\"Second\"}")
                + frame("{\"type\":\"response.refusal.delta\",\"delta\":\"Cannot comply\"}") + terminal(reasoning + "," + message), true, true, events).getMessage();
        assertEquals("First\n\nSecond", reply.getReasoningText());
        assertEquals("Cannot comply", reply.getContent());
        assertEquals(List.of("reasoning_delta:First", "reasoning_delta:\n\n", "reasoning_delta:Second", "delta:Cannot comply"), events);
    }

    @Test public void truncationCanContinueProseButNeverExecutesPartialTools() throws Exception {
        String incomplete = completed(REASONING + "," + MESSAGE).replace("\"status\":\"completed\",\"output\"", "\"status\":\"incomplete\",\"incomplete_details\":{\"reason\":\"max_output_tokens\"},\"output\"");
        assertEquals("length", read(incomplete, false, true, new ArrayList<>()).getFinishReason());
        assertThrows(AIIncompleteResponseException.class, () -> read(incomplete.replace(MESSAGE, CALL), false, true, new ArrayList<>()));
        assertThrows(AIIncompleteResponseException.class, () -> read(incomplete.replace("max_output_tokens", "content_filter"), false, true, new ArrayList<>()));
        for (String body : List.of("", "data: [DONE]\n\n", frame("{\"type\":\"response.output_text.delta\",\"delta\":\"partial\"}"))) {
            assertThrows(AIIncompleteResponseException.class, () -> read(body, true, true, new ArrayList<>()));
        }
    }

    @Test public void rejectsErrorsMalformedToolsAndExcessiveInput() {
        for (String body : List.of(frame("{\"type\":\"error\",\"message\":\"private detail\"}"),
                frame("{\"type\":\"response.failed\"}"), "data: null\n\n", "data: {broken}\n\n",
                terminal(CALL + "," + CALL), terminal(CALL.replace("\"completed\"", "\"in_progress\"")),
                terminal(CALL.replace("\"call_1\"", "\"\"")),
                frame("{\"type\":\"response.function_call_arguments.delta\",\"output_index\":-1,\"delta\":\"{}\"}"),
                frame("{\"type\":\"response.function_call_arguments.delta\",\"output_index\":0,\"delta\":" + gson.toJson("x".repeat(ContentToolCatalog.MAX_ARGUMENT_LENGTH + 1)) + "}"))) {
            assertThrows(AIResponseException.class, () -> read(body, true, true, new ArrayList<>()));
        }
        assertThrows(IOException.class, () -> read("data: " + "x".repeat(AIChatStreamReader.MAX_RESPONSE_BYTES), true, true, new ArrayList<>()));
    }

    @Test public void closesProviderConnectionWhenBrowserDisconnects() {
        boolean[] closed = {false};
        InputStream input = new ByteArrayInputStream(frame("{\"type\":\"response.output_text.delta\",\"delta\":\"Answer\"}").getBytes(StandardCharsets.UTF_8)) {
            @Override public void close() { closed[0] = true; }
        };
        assertThrows(IOException.class, () -> adapter.read(input, true, true, (type, text) -> { throw new IOException("Disconnected"); }));
        assertTrue(closed[0]);
    }
}
