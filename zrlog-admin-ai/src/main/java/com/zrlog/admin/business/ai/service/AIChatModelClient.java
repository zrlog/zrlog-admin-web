package com.zrlog.admin.business.ai.service;

import com.google.gson.JsonObject;
import com.zrlog.admin.business.ai.exception.*;
import com.zrlog.admin.business.ai.model.*;
import com.zrlog.admin.business.knowledge.KnowledgeModels.Tool;
import com.zrlog.admin.business.rest.base.AIWebSiteInfo;
import com.zrlog.data.security.AccountAccess;
import jakarta.inject.Inject;

import java.io.IOException;
import java.io.InputStream;
import java.io.ByteArrayInputStream;
import com.hibegin.common.util.http.BoundedHttpExchange;
import com.zrlog.util.ExecutionDeadline;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.util.*;
import java.util.stream.Collectors;

/** Provider protocol, bounded retries and continuation of a single model turn. */
public class AIChatModelClient extends AIService {
    private static final Set<String> CONTINUABLE_FINISH_REASONS = Set.of("length", "max_tokens", "max_output_tokens", "max_completion_tokens");
    private final AIFileService files;

    @Inject
    public AIChatModelClient(AIFileService files) {
        this.files = files;
    }

    AIChatModelClient(HttpClient client, AIFileService files) {
        super(client);
        this.files = files;
    }

    AIProviderResponses.Choice completeTurn(AccountAccess actor, AIWebSiteInfo info, List<AIProviderRequests.Message> messages,
                                                    List<Tool> definitions, AIChatStreamReader.Progress progress) throws IOException, InterruptedException {
        return completeTurn(actor, info, messages, definitions, progress, null);
    }

    AIProviderResponses.Choice completeTurn(AccountAccess actor, AIWebSiteInfo info, List<AIProviderRequests.Message> messages,
                                            List<Tool> definitions, AIChatStreamReader.Progress progress,
                                            ExecutionDeadline deadline) throws IOException, InterruptedException {
        List<AIProviderRequests.Message> currentMessages = messages;
        StringBuilder content = new StringBuilder(), reasoning = new StringBuilder();
        List<OpenAIResponses.Item> responsesOutput = new ArrayList<>();
        for (int continuation = 0; ; continuation++) {
            AIProviderResponses.Choice choice = null;
            for (int attempt = 0; ; attempt++) {
                try {
                    String body = request(info, currentMessages, continuation == 0 ? definitions : List.of());
                    choice = deadline == null ? complete(actor, info, body, progress)
                            : complete(actor, info, body, progress, deadline);
                    break;
                } catch (AIRequestException e) {
                    if (!Objects.equals(e.getStatusCode(), 503) || attempt >= 2) throw e;
                    if (deadline == null) pauseBeforeStreamRetry(attempt);
                    else {
                        long pause = (1L << (attempt + 1)) * 1000;
                        if (deadline.requireRemainingMillis() <= pause) throw new java.net.http.HttpTimeoutException("AI time budget exhausted");
                        Thread.sleep(pause);
                        deadline.requireRemainingMillis();
                    }
                }
            }
            AIProviderResponses.Message reply = choice.getMessage();
            if (reply == null) throw new AIResponseException("Missing message");
            if (reply.getContent() != null) content.append(reply.getContent());
            if (reply.getReasoningText() != null) reasoning.append(reply.getReasoningText());
            if (reply.responsesOutput != null) responsesOutput.addAll(reply.responsesOutput);
            String finish = Objects.toString(choice.getFinishReason(), "").trim().toLowerCase(Locale.ROOT);
            choice.setFinishReason(finish);
            if (!CONTINUABLE_FINISH_REASONS.contains(finish)) {
                reply.setContent(content.toString());
                reply.reasoningContent = reasoning.toString();
                if (!responsesOutput.isEmpty()) reply.responsesOutput = responsesOutput;
                return choice;
            }
            // Partial tool arguments cannot safely be executed or continued as prose.
            if (continuation >= 3 || reply.toolCalls != null && !reply.toolCalls.isEmpty()) {
                throw new AIIncompleteResponseException(finish, continuation);
            }
            currentMessages = new ArrayList<>(messages);
            AIProviderRequests.Message partial = new AIProviderRequests.Message("assistant", content.toString());
            if (reasoning.length() > 0) partial.reasoningContent = reasoning.toString();
            if (!responsesOutput.isEmpty()) partial.responsesOutput = new ArrayList<>(responsesOutput);
            currentMessages.add(partial);
            currentMessages.add(new AIProviderRequests.Message("user",
                    "Continue exactly from where the previous response stopped. Do not repeat earlier content. Do not add a preface or summary."));
        }
    }

    void pauseBeforeStreamRetry(int attempt) throws InterruptedException {
        Thread.sleep((long) Math.pow(2, attempt + 1) * 1000);
    }

    String request(AIWebSiteInfo info, List<AIProviderRequests.Message> messages, List<Tool> definitions) {
        AIProviderRequests.CompletionRequest request = gson.fromJson(buildRequestBody(List.of(), info, true), AIProviderRequests.CompletionRequest.class);
        request.setMessages(messages);
        if (!definitions.isEmpty()) {
            request.tools = new ArrayList<>();
            request.tool_choice = "auto";
            for (Tool tool : definitions) {
                AIProviderRequests.Tool definition = new AIProviderRequests.Tool();
                definition.function = new AIProviderRequests.Function();
                definition.function.name = tool.name;
                definition.function.description = tool.description;
                definition.function.parameters = tool.inputSchema;
                request.tools.add(definition);
            }
        }
        return gson.toJson(request);
    }

    protected AIProviderResponses.Choice complete(AccountAccess actor, AIWebSiteInfo info, String body, AIChatStreamReader.Progress progress)
            throws IOException, InterruptedException {
        return complete(actor, info, body, progress, null);
    }

    private AIProviderResponses.Choice complete(AccountAccess actor, AIWebSiteInfo info, String body,
                                                AIChatStreamReader.Progress progress, ExecutionDeadline deadline)
            throws IOException, InterruptedException {
        boolean responses = info.getAi_provider() == AIProviderType.OPEN_AI;
        OpenAIResponsesAdapter adapter = new OpenAIResponsesAdapter();
        AIProviderRequests.CompletionRequest completion = gson.fromJson(body, AIProviderRequests.CompletionRequest.class);
        Map<String, String> files = new HashMap<>();
        AIFileService storage = null;
        long size = 0;
        for (AIProviderRequests.Message message : completion.getMessages())
            if (message.attachments != null && !message.attachments.isEmpty()) {
                if (!"user".equals(message.getRole())) throw AIFileService.error("invalid");
                AIFileService.requireSupported(
                        info,
                        message.attachments.stream()
                                .map(file -> file.name)
                                .collect(Collectors.toList()));
                if (storage == null) storage = this.files;
                for (var file : message.attachments) {
                    if ((size += file.size) > AIFileService.MAX_CONTEXT_BYTES) throw AIFileService.error("size");
                    files.put(file.id, storage.dataUrl(actor.getUserId(), file));
                }
            }
        if (responses) body = gson.toJson(adapter.request(completion, info.isReasoningEnabled(), files));
        else {
            JsonObject wire = gson.toJsonTree(completion).getAsJsonObject();
            for (var message : wire.getAsJsonArray("messages")) {
                message.getAsJsonObject().remove("attachments");
                message.getAsJsonObject().remove("responsesOutput");
            }
            body = gson.toJson(wire);
        }
        var request = buildRequest(info, body, responses ? "/responses" : "/chat/completions");
        HttpResponse<?> response;
        InputStream responseBody;
        if (deadline == null) {
            HttpResponse<InputStream> streamed = client().send(request, HttpResponse.BodyHandlers.ofInputStream());
            response = streamed;
            responseBody = streamed.body();
        } else {
            HttpResponse<byte[]> buffered = BoundedHttpExchange.sendBytes(client(), request, deadline, 2 * 1024 * 1024);
            response = buffered;
            responseBody = new ByteArrayInputStream(buffered.body());
        }
        try (InputStream input = responseBody) {
            if (response.statusCode() != 200) throw new AIRequestException("AI request failed", response.statusCode());
            boolean sse = response.headers().firstValue("Content-Type").orElse("").toLowerCase(Locale.ROOT).contains("text/event-stream");
            return responses ? adapter.read(input, sse, info.isReasoningEnabled(), progress)
                    : new AIChatStreamReader().read(input, sse, progress);
        }
    }

}
