import {AIContent} from "@editor/dist/ai/AIContentItem";
import {ReactNode} from "react";

export type AssistantTool =
    | "rewrite"
    | "title"
    | "alias"
    | "digest"
    | "tags"
    | "score"
    | "publishCheck"
    | "seo"
    | "proofread"
    | "structure"
    | "questions"
    | "cover";

export type ArticleScoreItem = {
    name: string;
    score: number;
    suggestion: string;
};

export type ArticleScoreResult = {
    score: number;
    summary: string;
    items: ArticleScoreItem[];
};

export type ArticleSeoItem = {
    name: string;
    status: "good" | "warning" | "bad" | string;
    suggestion: string;
};

export type ArticleSeoResult = {
    score: number;
    summary: string;
    items: ArticleSeoItem[];
};

export type ArticleProofreadItem = {
    original: string;
    issue: string;
    suggestion: string;
};

export type ArticleProofreadResult = {
    summary: string;
    items: ArticleProofreadItem[];
};

export type ArticleStructureItem = {
    name: string;
    status: "good" | "warning" | "bad" | string;
    suggestion: string;
};

export type ArticleStructureResult = {
    summary: string;
    items: ArticleStructureItem[];
};

export type ArticleReaderQuestionItem = {
    question: string;
    reason: string;
    suggestion: string;
};

export type ArticleReaderQuestionsResult = {
    summary: string;
    items: ArticleReaderQuestionItem[];
};

export type AssistantToolPayload =
    | {
          tool: "rewrite";
          payload: { summary?: string; markdown?: string };
      }
    | {
          tool: "title";
          payload: { titles?: string[] };
      }
    | {
          tool: "alias";
          payload: { alias?: string };
      }
    | {
          tool: "digest";
          payload: { digest?: string };
      }
    | {
          tool: "tags";
          payload: { tags?: string[] };
      }
    | {
          tool: "score";
          payload: Partial<ArticleScoreResult>;
      }
    | {
          tool: "publishCheck";
          payload: Partial<ArticleScoreResult>;
      }
    | {
          tool: "seo";
          payload: Partial<ArticleSeoResult>;
      }
    | {
          tool: "proofread";
          payload: Partial<ArticleProofreadResult>;
      }
    | {
          tool: "structure";
          payload: Partial<ArticleStructureResult>;
      }
    | {
          tool: "questions";
          payload: Partial<ArticleReaderQuestionsResult>;
      }
    | {
          tool: "cover";
          payload: { url?: string };
      };

export type ParsedSseResponse = {
    content: string;
    reasoningContent?: string;
    toolPayload?: AssistantToolPayload;
    messageId?: string;
    errorMessage?: string;
    errorMeta?: ArticleAiErrorMeta;
};

export type ToolAwareAIContent = AIContent & {
    reasoningContent?: string;
    messageId?: string;
    messageType?: "articleContext" | "error" | string;
    contextMeta?: ArticleContextMeta;
    errorMeta?: ArticleAiErrorMeta;
    tool?: AssistantTool;
    payload?: AssistantToolPayload["payload"];
};

export type ArticleContextMeta = {
    title?: string;
    articleVersion?: number;
    markdownLength?: number;
    createdAt?: number;
};

export type ArticleAiErrorMeta = {
    provider?: string;
    model?: string;
    status?: number;
    errorType?: ArticleAiErrorType;
    finishReason?: string;
    continuationRounds?: number;
};

export type ArticleAiErrorType =
    | "incomplete_response"
    | "provider_request"
    | "provider_response"
    | "unsupported_tool"
    | "unsupported_image_generation"
    | "configuration_required"
    | "unknown";

export type ArticleAiMessageExportResponse = {
    articleId?: number;
    draft?: boolean;
    exportedAt?: number;
    messageCount?: number;
    messages?: ToolAwareAIContent[];
};

export type AssistantToolButton = {
    key: AssistantTool;
    command: string;
    icon: ReactNode;
    label: string;
    prompt: string;
    group: "writing" | "publish" | "check";
    disabled?: boolean;
    disabledReason?: string;
};

export type AssistantToolContextPolicy = "fullConversation" | "chatOnly" | "none";

export type AssistantToolGroup = {
    key: AssistantToolButton["group"];
    label: string;
};

export type ArticleAiRequestPreview = {
    provider?: string;
    model?: string;
    titleLength: number;
    titleSnippet: string;
    aliasLength: number;
    aliasSnippet: string;
    digestLength: number;
    digestSnippet: string;
    keywordsLength: number;
    keywordsSnippet: string;
    coverLength: number;
    coverSnippet: string;
    selectedTextLength: number;
    selectedTextSnippet: string;
    markdownLength: number;
    markdownSnippet: string;
    imageReferenceCount: number;
    imageReferences: string[];
    linkReferenceCount: number;
    linkReferences: string[];
    externalLinkCount: number;
    externalLinks: string[];
    articleContextAdded: boolean;
    conversationMessageCount: number;
    chatMessageCount: number;
    toolMessageCount: number;
    articleContextMessageCount: number;
    systemMessageCount: number;
    errorMessageCount: number;
};

export type ArticleAiRequestField = "title" | "digest" | "keywords" | "markdown";

export type ArticleAiRequestFieldSelection = Record<ArticleAiRequestField, boolean>;

export const assistantTools: AssistantTool[] = [
    "rewrite",
    "title",
    "alias",
    "digest",
    "tags",
    "score",
    "publishCheck",
    "seo",
    "proofread",
    "structure",
    "questions",
    "cover",
];

export const isAssistantTool = (tool: unknown): tool is AssistantTool => assistantTools.includes(tool as AssistantTool);
