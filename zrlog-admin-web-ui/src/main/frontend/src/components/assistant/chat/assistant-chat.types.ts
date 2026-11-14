import type { AIContent } from "@zrlog/editor/core/ai/AIContentItem";
import type { WritingSkill, WritingSkillPayload, ArticleContextMeta } from "../writing/writing-assistant.types";

export type ParsedSseResponse = {
    content: string;
    reasoningContent?: string;
    toolPayload?: WritingSkillPayload;
    messageId?: string;
    errorMessage?: string;
    errorMeta?: AssistantErrorMeta;
};

export type AssistantMessage = AIContent & {
    createdAt?: number;
    taskResult?: { runId: string; version: number; status: string };
    skillContract?: { version: number; contextRevision: string; applicableFields: string[] };
    reasoningContent?: string;
    messageId?: string;
    messageType?: "articleContext" | "error" | string;
    contextMeta?: ArticleContextMeta;
    errorMeta?: AssistantErrorMeta;
    tool?: WritingSkill;
    payload?: WritingSkillPayload["payload"];
};

export type AssistantErrorMeta = {
    suggestion?: string;
    errorTypeLabel?: string;
    provider?: string;
    model?: string;
    status?: number;
    errorType?: AssistantErrorType;
    finishReason?: string;
    continuationRounds?: number;
};

export type AssistantErrorType =
    | "incomplete_response"
    | "provider_request"
    | "provider_response"
    | "unsupported_tool"
    | "unsupported_image_generation"
    | "configuration_required"
    | "unknown";

export type AssistantMessageExportResponse = {
    taskId?: string;
    articleId?: number;
    draft?: boolean;
    exportedAt?: number;
    messageCount?: number;
    messages?: AssistantMessage[];
};
