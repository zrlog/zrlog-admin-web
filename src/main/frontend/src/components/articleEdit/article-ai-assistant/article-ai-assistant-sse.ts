import {getRes} from "../../../utils/constants";
import {ArticleAiErrorMeta, ArticleAiErrorType, AssistantToolPayload, isAssistantTool, ParsedSseResponse} from "./article-ai-assistant.types";

const errorTypes = new Set<ArticleAiErrorType>([
    "incomplete_response",
    "provider_request",
    "provider_response",
    "unsupported_tool",
    "unsupported_image_generation",
    "configuration_required",
    "unknown",
]);

const toArticleAiErrorMeta = (chunk: Record<string, unknown>): ArticleAiErrorMeta => {
    const errorMeta: ArticleAiErrorMeta = {};
    if (typeof chunk.provider === "string") {
        errorMeta.provider = chunk.provider;
    }
    if (typeof chunk.model === "string") {
        errorMeta.model = chunk.model;
    }
    if (typeof chunk.status === "number") {
        errorMeta.status = chunk.status;
    }
    if (typeof chunk.errorType === "string" && errorTypes.has(chunk.errorType as ArticleAiErrorType)) {
        errorMeta.errorType = chunk.errorType as ArticleAiErrorType;
    }
    if (typeof chunk.finishReason === "string") {
        errorMeta.finishReason = chunk.finishReason;
    }
    if (typeof chunk.continuationRounds === "number") {
        errorMeta.continuationRounds = chunk.continuationRounds;
    }
    return errorMeta;
};

export const parseSseResponse = (responseText: string): ParsedSseResponse => {
    if (!responseText) {
        return { content: "" };
    }
    const normalizedResponse = responseText.replace(/\r\n/g, "\n").replace(/\r/g, "\n");
    const trimmedResponse = normalizedResponse.trim();
    if (trimmedResponse.startsWith("{")) {
        try {
            const errorData = JSON.parse(trimmedResponse);
            if (errorData.error) {
                return {
                    content: "",
                    errorMessage: errorData.message || getRes().error.unknown,
                    errorMeta: toArticleAiErrorMeta(errorData),
                };
            }
        } catch (_) {
            return { content: "", errorMessage: getRes().error.unknown };
        }
    }

    const segments = normalizedResponse.split(/\n\n+/);
    const entries = (/\n\n+$/.test(normalizedResponse) ? segments : segments.slice(0, -1)).filter(
        (entry) => entry.trim().length > 0
    );
    let content = "";
    let toolPayload: AssistantToolPayload | undefined;
    let messageId: string | undefined;
    let errorMessage: string | undefined;
    let errorMeta: ArticleAiErrorMeta | undefined;
    for (const entry of entries) {
        const event = entry
            .split("\n")
            .find((line) => line.startsWith("event:"))
            ?.substring("event:".length)
            .trim();
        const dataLines = entry
            .split("\n")
            .filter((line) => line.startsWith("data:"))
            .map((line) => {
                const data = line.substring("data:".length);
                return data.startsWith(" ") ? data.substring(1) : data;
            });
        for (const dataLine of dataLines) {
            const data = dataLine.trim() === "[DONE]" ? "[DONE]" : dataLine;
            if (data === "[DONE]") {
                continue;
            }
            try {
                const chunk = JSON.parse(data);
                if (event === "ai-error" || chunk.error) {
                    errorMessage = chunk.message || chunk.error?.message || chunk.error || getRes().error.unknown;
                    errorMeta = toArticleAiErrorMeta(chunk);
                    continue;
                }
                if (isAssistantTool(chunk.tool) && chunk.payload) {
                    toolPayload = { tool: chunk.tool, payload: chunk.payload } as AssistantToolPayload;
                }
                if (typeof chunk.messageId === "string") {
                    messageId = chunk.messageId;
                }
                if (typeof chunk.content === "string") {
                    content += chunk.content;
                }
            } catch (_) {
                content += data;
            }
        }
    }
    return { content, toolPayload, messageId, errorMessage, errorMeta };
};
