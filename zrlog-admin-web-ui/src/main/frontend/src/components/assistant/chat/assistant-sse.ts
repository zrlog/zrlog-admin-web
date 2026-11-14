import { getResponseMessage } from "../../../utils/error-message";
import { getRes } from "../../../utils/constants";
import { AssistantErrorMeta, AssistantErrorType, ParsedSseResponse } from "./assistant-chat.types";
import { WritingSkillPayload, isWritingSkill } from "../writing/writing-assistant.types";

const errorTypes = new Set<AssistantErrorType>([
    "incomplete_response",
    "provider_request",
    "provider_response",
    "unsupported_tool",
    "unsupported_image_generation",
    "configuration_required",
    "unknown",
]);

const toAssistantErrorMeta = (chunk: Record<string, unknown>): AssistantErrorMeta => {
    const errorMeta: AssistantErrorMeta = {};
    if (typeof chunk.suggestion === "string") errorMeta.suggestion = chunk.suggestion;
    if (typeof chunk.errorTypeLabel === "string") errorMeta.errorTypeLabel = chunk.errorTypeLabel;
    if (typeof chunk.provider === "string") {
        errorMeta.provider = chunk.provider;
    }
    if (typeof chunk.model === "string") {
        errorMeta.model = chunk.model;
    }
    if (typeof chunk.status === "number") {
        errorMeta.status = chunk.status;
    }
    if (typeof chunk.errorType === "string" && errorTypes.has(chunk.errorType as AssistantErrorType)) {
        errorMeta.errorType = chunk.errorType as AssistantErrorType;
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
                    errorMessage: getResponseMessage(errorData) || getRes().error.unknown,
                    errorMeta: toAssistantErrorMeta(errorData),
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
    let reasoningContent = "";
    let toolPayload: WritingSkillPayload | undefined;
    let messageId: string | undefined;
    let errorMessage: string | undefined;
    let errorMeta: AssistantErrorMeta | undefined;
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
                    errorMessage =
                        getResponseMessage(chunk) ||
                        getResponseMessage(chunk.error) ||
                        (typeof chunk.error === "string" && chunk.error.trim() ? chunk.error : getRes().error.unknown);
                    errorMeta = toAssistantErrorMeta(chunk);
                    continue;
                }
                if (isWritingSkill(chunk.tool) && chunk.payload) {
                    toolPayload = { tool: chunk.tool, payload: chunk.payload } as WritingSkillPayload;
                }
                if (typeof chunk.messageId === "string") {
                    messageId = chunk.messageId;
                }
                if (typeof chunk.reasoningContent === "string") {
                    reasoningContent += chunk.reasoningContent;
                }
                if (typeof chunk.reasoning_content === "string") {
                    reasoningContent += chunk.reasoning_content;
                }
                if (typeof chunk.content === "string") {
                    content += chunk.content;
                }
            } catch (_) {
                content += data;
            }
        }
    }
    return { content, reasoningContent, toolPayload, messageId, errorMessage, errorMeta };
};
