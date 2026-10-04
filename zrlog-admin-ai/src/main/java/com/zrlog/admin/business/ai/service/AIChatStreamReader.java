package com.zrlog.admin.business.ai.service;

import com.google.gson.Gson;
import com.zrlog.admin.business.knowledge.ContentToolCatalog;
import com.google.gson.JsonParseException;
import com.zrlog.admin.business.ai.exception.AIResponseException;
import com.zrlog.admin.business.ai.exception.AIIncompleteResponseException;
import com.zrlog.admin.business.ai.model.AIProviderRequests;
import com.zrlog.admin.business.ai.model.AIProviderResponses;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Reads provider SSE incrementally, assembling fragmented tool arguments before executing tools. */
final class AIChatStreamReader {
    static final int MAX_RESPONSE_BYTES = 16 * 1024 * 1024;
    interface Progress { void emit(String type, String text) throws IOException; }
    private final Gson gson = new Gson();
    private final StringBuilder content = new StringBuilder();
    private final StringBuilder reasoning = new StringBuilder();
    private final Map<Integer, AIProviderRequests.ToolCall> calls = new TreeMap<>();
    private final Map<Integer, StringBuilder> toolArguments = new HashMap<>();
    private String finish;

    AIProviderResponses.Choice read(InputStream input, boolean sse, Progress progress) throws IOException {
        try (InputStream bounded = new LimitedInput(input)) {
            if (!sse) {
                AIProviderResponses.CompletionResponse response = parse(new String(bounded.readAllBytes(), StandardCharsets.UTF_8));
                AIProviderResponses.Choice choice = single(response);
                if (choice.getMessage() == null) throw invalid();
                text(progress, "reasoning_delta", choice.getMessage().getReasoningText());
                text(progress, "delta", choice.getMessage().getContent());
                return choice;
            }
            BufferedReader reader = new BufferedReader(new InputStreamReader(bounded, StandardCharsets.UTF_8));
            StringBuilder data = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty()) {
                    if (frame(data.toString(), progress)) break;
                    data.setLength(0);
                } else if (line.startsWith("data:")) {
                    if (data.length() > 0) data.append('\n');
                    data.append(line.substring(line.startsWith("data: ") ? 6 : 5));
                }
            }
            if (data.length() > 0 && !"[DONE]".equals(data.toString())) frame(data.toString(), progress);
            if (finish == null) throw new AIIncompleteResponseException("stream_ended");
            AIProviderResponses.Message message = new AIProviderResponses.Message();
            message.setContent(content.toString()); message.reasoningContent = reasoning.toString();
            if (!calls.isEmpty()) {
                calls.forEach((index, call) -> {
                    if (toolArguments.containsKey(index)) call.function.arguments = toolArguments.get(index).toString();
                });
                message.toolCalls = new ArrayList<>(calls.values());
            }
            AIProviderResponses.Choice choice = new AIProviderResponses.Choice(); choice.setMessage(message); choice.setFinishReason(finish);
            return choice;
        }
    }

    private boolean frame(String data, Progress progress) throws IOException {
        if (data.isEmpty()) return false;
        if ("[DONE]".equals(data)) return true;
        AIProviderResponses.CompletionResponse response = parse(data);
        if (response.getChoices().isEmpty()) return false; // Optional usage-only frame.
        AIProviderResponses.Choice choice = single(response);
        AIProviderResponses.Delta delta = choice.getDelta();
        String thought = delta == null ? null : delta.getReasoningContent();
        if (thought == null && delta != null && delta.getReasoning() instanceof String) thought = (String) delta.getReasoning();
        if (finish != null) {
            // Some providers repeat the finished choice with an empty delta in their usage frame.
            // Accept that metadata without allowing new output or a changed finish reason.
            if (choice.getFinishReason() != null && !finish.equals(choice.getFinishReason())
                    || delta != null && (hasText(delta.getContent()) || hasText(thought)
                    || delta.toolCalls != null && !delta.toolCalls.isEmpty())) throw invalid();
            return false;
        }
        if (delta != null) {
            if (thought != null) { reasoning.append(thought); text(progress, "reasoning_delta", thought); }
            if (delta.getContent() != null) { content.append(delta.getContent()); text(progress, "delta", delta.getContent()); }
            if (delta.toolCalls != null) for (AIProviderResponses.ToolCallDelta fragment : delta.toolCalls) merge(fragment);
        }
        if (choice.getFinishReason() != null) finish = choice.getFinishReason();
        return false;
    }

    private void merge(AIProviderResponses.ToolCallDelta part) {
        if (part == null || part.index == null || part.index < 0 || part.index >= 8) throw invalid();
        AIProviderRequests.ToolCall call = calls.computeIfAbsent(part.index, ignored -> {
            AIProviderRequests.ToolCall value = new AIProviderRequests.ToolCall(); value.function = new AIProviderRequests.FunctionCall(); return value;
        });
        if (part.id != null) call.id = identifier(call.id, part.id);
        if (part.type != null) call.type = identifier(call.type, part.type);
        if (part.function != null) {
            if (part.function.name != null) call.function.name = identifier(call.function.name, part.function.name);
            if (part.function.arguments != null) {
                StringBuilder arguments = toolArguments.computeIfAbsent(part.index, ignored -> new StringBuilder());
                if (arguments.length() + part.function.arguments.length() > ContentToolCatalog.MAX_ARGUMENT_LENGTH) throw invalid();
                arguments.append(part.function.arguments);
            }
        }
        if (part.extra_content != null) call.extra_content = part.extra_content;
    }

    private String identifier(String previous, String next) {
        String value = previous == null || previous.equals(next) ? next : previous + next;
        if (value.length() > 256) throw invalid();
        return value;
    }

    private AIProviderResponses.CompletionResponse parse(String data) {
        try {
            AIProviderResponses.CompletionResponse response = gson.fromJson(data, AIProviderResponses.CompletionResponse.class);
            if (response == null || response.getChoices() == null
                    || response.getError() != null && !response.getError().isJsonNull()) throw invalid();
            return response;
        } catch (JsonParseException e) { throw invalid(); }
    }

    private AIProviderResponses.Choice single(AIProviderResponses.CompletionResponse response) {
        if (response.getChoices().size() != 1 || response.getChoices().get(0) == null) throw invalid();
        return response.getChoices().get(0);
    }

    private static void text(Progress progress, String type, String text) throws IOException {
        if (hasText(text)) progress.emit(type, text);
    }
    private static boolean hasText(String text) { return text != null && !text.isEmpty(); }
    private static AIResponseException invalid() { return new AIResponseException("Invalid AI stream"); }

    static final class LimitedInput extends FilterInputStream {
        private long remaining = MAX_RESPONSE_BYTES;
        LimitedInput(InputStream input) { super(input); }
        private int count(int size) throws IOException {
            if (size > 0 && (remaining -= size) < 0) throw new IOException("Response too large");
            return size;
        }
        @Override public int read() throws IOException { int value = in.read(); if (value >= 0) count(1); return value; }
        @Override public int read(byte[] data, int offset, int length) throws IOException { return count(in.read(data, offset, length)); }
    }
}
