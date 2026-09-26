package com.zrlog.admin.business.ai.service;

import com.google.gson.Gson;
import com.zrlog.admin.business.ai.exception.AIResponseException;
import com.zrlog.admin.business.ai.exception.AIIncompleteResponseException;
import com.zrlog.admin.business.ai.model.AIProviderResponses;
import org.junit.Test;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

import static org.junit.Assert.*;

public class AIChatStreamReaderTest {
    private String frame(String delta, String finish) {
        return "data: {\"choices\":[{\"delta\":" + delta + ",\"finish_reason\":" + new Gson().toJson(finish) + "}]}\r\n\r\n";
    }
    private AIProviderResponses.Choice read(String wire, AIChatStreamReader.Progress progress) throws IOException {
        return new AIChatStreamReader().read(new ByteArrayInputStream(wire.getBytes(StandardCharsets.UTF_8)), true, progress);
    }

    @Test public void emitsUnicodeTextBeforeTheProviderCompletesTheResponse() throws Exception {
        try (PipedInputStream input = new PipedInputStream(4096); PipedOutputStream output = new PipedOutputStream(input)) {
            CountDownLatch first = new CountDownLatch(1);
            List<String> events = new CopyOnWriteArrayList<>();
            ExecutorService pool = Executors.newSingleThreadExecutor();
            try {
                Future<AIProviderResponses.Choice> result = pool.submit(() -> new AIChatStreamReader().read(input, true, (type, text) -> {
                    events.add(type + ":" + text); first.countDown();
                }));
                byte[] bytes = frame("{\"content\":\"你🙂\"}", null).getBytes(StandardCharsets.UTF_8);
                for (byte value : bytes) output.write(value);
                output.flush();
                assertTrue("First chunk must arrive while the provider remains open", first.await(3, TimeUnit.SECONDS));
                assertFalse(result.isDone()); assertEquals(List.of("delta:你🙂"), events);
                output.write((frame("{\"content\":\"好\"}", "stop") + "data: [DONE]\n\n").getBytes(StandardCharsets.UTF_8)); output.flush();
                assertEquals("你🙂好", result.get(3, TimeUnit.SECONDS).getMessage().getContent());
            } finally { pool.shutdownNow(); }
        }
    }

    @Test public void assemblesInterleavedToolArgumentsAndPreservesProviderSignatures() throws Exception {
        String first = "{\"tool_calls\":[{\"index\":0,\"id\":\"a\",\"type\":\"function\",\"function\":{\"name\":\"read_article\",\"arguments\":\"{\\\"id\\\":\"},\"extra_content\":{\"google\":{\"thought_signature\":\"opaque\"}}}]}";
        String second = "{\"tool_calls\":[{\"index\":1,\"id\":\"b\",\"type\":\"function\",\"function\":{\"name\":\"search_articles\",\"arguments\":\"{}\"}},{\"index\":0,\"function\":{\"arguments\":\"7}\"}}]}";
        List<String> events = new ArrayList<>();
        AIProviderResponses.Choice result = read(frame(first, null) + frame(second, "tool_calls") + "data: [DONE]\n\n", (type, text) -> events.add(text));
        assertEquals(2, result.getMessage().toolCalls.size());
        assertEquals("{\"id\":7}", result.getMessage().toolCalls.get(0).function.arguments);
        assertEquals("opaque", result.getMessage().toolCalls.get(0).extra_content.google.thought_signature);
        assertEquals("{}", result.getMessage().toolCalls.get(1).function.arguments);
        assertTrue(events.isEmpty());
    }

    @Test public void onlyStreamsTextReasoningAndAcceptsUsageFrames() throws Exception {
        List<String> events = new ArrayList<>();
        String wire = frame("{\"reasoning\":{\"signature\":\"opaque\"}}", null)
                + frame("{\"reasoning_content\":\"First \"}", null)
                + frame("{\"reasoning\":\"then answer\"}", null)
                + frame("{\"content\":\"Answer\"}", "stop") + "data: {\"choices\":[],\"usage\":{}}\n\ndata:[DONE]\n\n";
        AIProviderResponses.Choice result = read(wire, (type, text) -> events.add(type + ":" + text));
        assertEquals("First then answer", result.getMessage().getReasoningText());
        assertEquals(List.of("reasoning_delta:First ", "reasoning_delta:then answer", "delta:Answer"), events);
    }

    @Test public void rejectsTruncationErrorsOversizeBodiesAndInvalidToolIndexes() throws Exception {
        for (String wire : List.of(frame("{\"content\":\"partial\"}", null), "data: [DONE]\n\n")) {
            assertThrows(AIIncompleteResponseException.class, () -> read(wire, (type, text) -> { }));
        }
        for (String wire : List.of("data: {\"error\":{\"message\":\"private provider detail\"}}\n\n",
                frame("{\"tool_calls\":[{\"index\":8}]}", "tool_calls"))) {
            assertThrows(AIResponseException.class, () -> read(wire, (type, text) -> { }));
        }
        assertThrows(IOException.class, () -> read("data: " + "x".repeat(1024 * 1024), (type, text) -> { }));
    }

    @Test public void closesTheProviderStreamWhenTheConsumerDisconnects() throws Exception {
        boolean[] closed = {false};
        InputStream input = new ByteArrayInputStream(frame("{\"content\":\"answer\"}", "stop").getBytes(StandardCharsets.UTF_8)) {
            @Override public void close() { closed[0] = true; }
        };
        assertThrows(IOException.class, () -> new AIChatStreamReader().read(input, true, (type, text) -> { throw new IOException("Client disconnected"); }));
        assertTrue(closed[0]);
    }

    @Test public void acceptsProvidersReturningJsonDespiteTheStreamRequest() throws Exception {
        List<String> events = new ArrayList<>();
        String json = "{\"choices\":[{\"message\":{\"content\":\"Answer\"},\"finish_reason\":\"stop\"}]}";
        AIProviderResponses.Choice result = new AIChatStreamReader().read(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)), false, (type, text) -> events.add(text));
        assertEquals("stop", result.getFinishReason()); assertEquals(List.of("Answer"), events);
    }
}
