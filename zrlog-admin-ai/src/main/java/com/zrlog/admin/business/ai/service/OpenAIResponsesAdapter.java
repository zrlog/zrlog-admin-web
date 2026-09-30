package com.zrlog.admin.business.ai.service;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonParseException;
import com.zrlog.admin.business.ai.exception.AIIncompleteResponseException;
import com.zrlog.admin.business.ai.exception.AIResponseException;
import com.zrlog.admin.business.ai.model.AIProviderRequests;
import com.zrlog.admin.business.ai.model.AIProviderResponses;
import com.zrlog.admin.business.ai.model.OpenAIResponses.*;
import com.zrlog.admin.business.knowledge.ContentToolCatalog;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Maps the assistant's normalized conversation to OpenAI's stateless Responses protocol. */
final class OpenAIResponsesAdapter {
    private final Gson gson = new Gson();

    Request request(AIProviderRequests.CompletionRequest completion, boolean summaries) {
        Request request = new Request();
        request.model = completion.getModel();
        request.stream = completion.isStream();
        request.max_output_tokens = completion.getMaxCompletionTokens();
        if (summaries && supportsSummary(request.model)) request.reasoning = new Reasoning();
        request.input = new ArrayList<>();
        for (AIProviderRequests.Message message : completion.getMessages()) {
            if (message.responsesOutput != null) {
                request.input.addAll(message.responsesOutput);
            } else if ("tool".equals(message.getRole())) {
                Item result = new Item(); result.type = "function_call_output";
                result.call_id = message.toolCallId; result.output = message.getContent();
                request.input.add(result);
            } else {
                if (message.getContent() != null && !message.getContent().isEmpty()) {
                    Item input = new Item(); input.type = "message"; input.role = message.getRole();
                    Content text = new Content(); text.text = message.getContent();
                    // Persisted conversation history has no raw Responses output to replay.
                    // Assistant text must still use the output content type on later turns.
                    boolean assistant = "assistant".equals(message.getRole());
                    text.type = assistant ? "output_text" : "input_text";
                    if (assistant) text.annotations = new JsonArray();
                    input.content = List.of(text); request.input.add(input);
                }
                // Also accepts checkpoints created before Responses was enabled.
                if (message.toolCalls != null) for (AIProviderRequests.ToolCall call : message.toolCalls) {
                    Item function = new Item(); function.type = "function_call"; function.call_id = call.id;
                    function.name = call.function.name; function.arguments = call.function.arguments;
                    request.input.add(function);
                }
            }
        }
        if (completion.tools != null && !completion.tools.isEmpty()) {
            request.tools = new ArrayList<>(); request.tool_choice = "auto";
            for (AIProviderRequests.Tool definition : completion.tools) {
                Tool tool = new Tool(); tool.name = definition.function.name;
                tool.description = definition.function.description; tool.parameters = definition.function.parameters;
                request.tools.add(tool);
            }
        }
        return request;
    }

    private boolean supportsSummary(String model) {
        // Chat-only and older non-reasoning models reject the reasoning parameter.
        return model != null && !model.endsWith("-chat-latest")
                && model.matches("(?:gpt-[56](?:[.-].*)?|o[34](?:-.*)?)");
    }

    AIProviderResponses.Choice read(InputStream input, boolean sse, boolean summaries, AIChatStreamReader.Progress progress)
            throws IOException {
        try (InputStream bounded = new AIChatStreamReader.LimitedInput(input)) {
            if (!sse) {
                AIProviderResponses.Choice choice = choice(parse(new String(bounded.readAllBytes(), StandardCharsets.UTF_8), Response.class), summaries);
                emit(progress, "reasoning_delta", choice.getMessage().getReasoningText());
                emit(progress, "delta", choice.getMessage().getContent());
                return choice;
            }
            BufferedReader reader = new BufferedReader(new InputStreamReader(bounded, StandardCharsets.UTF_8));
            StringBuilder data = new StringBuilder();
            ProgressState state = new ProgressState();
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty()) {
                    AIProviderResponses.Choice completed = frame(data.toString(), summaries, progress, state);
                    if (completed != null) return completed;
                    data.setLength(0);
                } else if (line.startsWith("data:")) {
                    if (data.length() > 0) data.append('\n');
                    data.append(line.substring(line.startsWith("data: ") ? 6 : 5));
                }
            }
            if (data.length() > 0) {
                AIProviderResponses.Choice completed = frame(data.toString(), summaries, progress, state);
                if (completed != null) return completed;
            }
            throw new AIIncompleteResponseException("stream_ended");
        }
    }

    private static class ProgressState {
        boolean text;
        boolean summary;
        String summaryPart;
        final Map<Integer, Integer> argumentLengths = new HashMap<>();
    }

    private AIProviderResponses.Choice frame(String data, boolean summaries, AIChatStreamReader.Progress progress, ProgressState state)
            throws IOException {
        if (data.isEmpty()) return null;
        if ("[DONE]".equals(data)) throw new AIIncompleteResponseException("stream_ended");
        StreamEvent event = parse(data, StreamEvent.class);
        if (event.type == null) throw invalid();
        switch (event.type) {
            case "response.output_text.delta":
            case "response.refusal.delta":
                if (event.delta == null) throw invalid();
                state.text |= !event.delta.isEmpty();
                emit(progress, "delta", event.delta);
                break;
            case "response.reasoning_summary_text.delta":
                if (event.delta == null) throw invalid();
                if (summaries && !event.delta.isEmpty()) {
                    String part = event.item_id + "/" + event.summary_index;
                    if (state.summary && !part.equals(state.summaryPart)) emit(progress, "reasoning_delta", "\n\n");
                    state.summary = true; state.summaryPart = part;
                    emit(progress, "reasoning_delta", event.delta);
                }
                break;
            case "response.function_call_arguments.delta":
                if (event.output_index == null || event.output_index < 0 || event.delta == null) throw invalid();
                int length = state.argumentLengths.merge(event.output_index, event.delta.length(), Integer::sum);
                if (length > ContentToolCatalog.MAX_ARGUMENT_LENGTH || state.argumentLengths.size() > 8) throw invalid();
                break;
            case "response.completed":
            case "response.incomplete":
                if (event.response == null || !event.type.equals("response." + event.response.status)) throw invalid();
                AIProviderResponses.Choice choice = choice(event.response, summaries);
                if (!state.summary) emit(progress, "reasoning_delta", choice.getMessage().getReasoningText());
                if (!state.text) emit(progress, "delta", choice.getMessage().getContent());
                return choice;
            case "response.failed":
            case "error":
                throw invalid();
            default:
                // Item/done snapshots must not duplicate deltas. Raw reasoning and opaque
                // encrypted_content are never emitted to the browser.
                break;
        }
        return null;
    }

    private AIProviderResponses.Choice choice(Response response, boolean summaries) {
        if (response.error != null && !response.error.isJsonNull() || response.output == null) throw invalid();
        boolean incomplete = "incomplete".equals(response.status);
        if (incomplete) {
            String reason = response.incomplete_details == null ? null : response.incomplete_details.reason;
            if (!"max_output_tokens".equals(reason)) throw new AIIncompleteResponseException("response_incomplete");
        } else if (!"completed".equals(response.status)) throw invalid();
        List<String> text = new ArrayList<>(), reasoning = new ArrayList<>();
        List<AIProviderRequests.ToolCall> calls = new ArrayList<>();
        Set<String> callIds = new HashSet<>();
        for (Item item : response.output) {
            if (item == null || item.type == null) throw invalid();
            switch (item.type) {
                case "reasoning":
                    if (summaries && item.summary != null) for (Summary part : item.summary) {
                        if (part == null || !"summary_text".equals(part.type) || part.text == null) throw invalid();
                        if (!part.text.isEmpty()) reasoning.add(part.text);
                    }
                    if (!summaries) item.summary = List.of();
                    item.content = null;
                    break;
                case "message":
                    if (!"assistant".equals(item.role) || item.content == null) throw invalid();
                    for (Content part : item.content) {
                        if (part == null) throw invalid();
                        if ("output_text".equals(part.type) && part.text != null) text.add(part.text);
                        else if ("refusal".equals(part.type) && part.refusal != null) text.add(part.refusal);
                        else throw invalid();
                    }
                    break;
                case "function_call":
                    if (incomplete) throw new AIIncompleteResponseException("max_output_tokens");
                    if (item.status != null && !"completed".equals(item.status)) throw invalid();
                    if (!identifier(item.call_id) || !identifier(item.name) || !callIds.add(item.call_id)
                            || item.arguments == null || item.arguments.length() > ContentToolCatalog.MAX_ARGUMENT_LENGTH || calls.size() >= 8)
                        throw invalid();
                    AIProviderRequests.ToolCall call = new AIProviderRequests.ToolCall();
                    call.id = item.call_id; call.type = "function"; call.function = new AIProviderRequests.FunctionCall();
                    call.function.name = item.name; call.function.arguments = item.arguments; calls.add(call);
                    break;
                default:
                    throw invalid();
            }
        }
        AIProviderResponses.Message message = new AIProviderResponses.Message();
        message.setContent(String.join("", text));
        message.reasoningContent = reasoning.isEmpty() ? null : String.join("\n\n", reasoning);
        message.toolCalls = calls.isEmpty() ? null : calls;
        message.responsesOutput = response.output;
        AIProviderResponses.Choice choice = new AIProviderResponses.Choice(); choice.setMessage(message);
        choice.setFinishReason(incomplete ? "length" : calls.isEmpty() ? "stop" : "tool_calls");
        return choice;
    }

    private boolean identifier(String value) { return value != null && !value.isBlank() && value.length() <= 256; }

    private <T> T parse(String data, Class<T> type) {
        try {
            T value = gson.fromJson(data, type);
            if (value == null) throw invalid();
            return value;
        } catch (JsonParseException e) { throw invalid(); }
    }

    private static void emit(AIChatStreamReader.Progress progress, String type, String text) throws IOException {
        if (text != null && !text.isEmpty()) progress.emit(type, text);
    }
    private static AIResponseException invalid() { return new AIResponseException("Invalid OpenAI response"); }
}
