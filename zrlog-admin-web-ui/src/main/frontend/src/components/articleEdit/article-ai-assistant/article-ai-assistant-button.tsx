import { Alert, Button, Space, Typography } from "antd";
import Tag from "@zrlog/ui/antd/Tag";
import { useUiApp } from "@zrlog/ui/feedback";
import RobotIcon from "@zrlog/ui/icons/robot";
import { ApplyAiValues, SkillContextRevision } from "../use-article-field-ai";

import { isChatMessage, renderChatMessage, useArticleChat } from "./use-article-chat";
import ArticleAiApproval from "./article-ai-approval";
import ArticleAiInput from "./article-ai-input";
import { canApplySkillValues, editorContextRevision } from "./article-ai-skill-contract";
import ArticleAiReasoning from "./article-ai-reasoning";

import { cloneElement, FunctionComponent, isValidElement, useEffect, useMemo, useRef, useState } from "react";
import { AIContent } from "@zrlog/editor/core/ai/AIContentItem";
import AIButton, {
    AIButtonRenderMessageOptions,
    AIStateCache,
    getAIButtonDrawerOpen,
} from "@zrlog/editor/core/ai/AIButton";
import AIIcon from "../../../icons/AIProviderIcon";
import useArticleEditorScreens from "../use-article-editor-screens";
import ArticleAiAssistantDrawer from "./article-ai-assistant-drawer";
import { AxiosInstance } from "axios";
import {
    formatLabelValue,
    getLabelValueSeparator,
    getRealRouteUrl,
    getRes,
    tryAppendBackendServerUrl,
} from "../../../utils/constants";
import { getSsDate } from "../../../base/SsData";
import { getAppState } from "../../../base/ConfigProviderApp";
import { ArticleChangeableValue, ArticleEditState } from "../index.types";
import { useTheme } from "antd-style";
import { addToCache, getCacheByKey } from "../../../utils/cache";
import ImageCropper from "../../../common/ImageCropper";
import { parseCoverAspectRatio } from "../cover-aspect-ratio";
import { resolveBackendCropImageUrl } from "../../../utils/crop-image-url";
import {
    ArticleAiErrorMeta,
    ArticleAiMessageExportResponse,
    ArticleUpdatedEvent,
    AssistantTool,
    AssistantToolPayload,
    isAssistantTool,
    ToolAwareAIContent,
} from "./article-ai-assistant.types";
import { parseSseResponse } from "./article-ai-assistant-sse";
import { getAssistantToolLabel } from "./tool/article-ai-assistant-tools";
import ArticleAiAssistantToolContent from "./tool/article-ai-assistant-tool-content";
import ArticleAiAssistantSkillContent from "./article-ai-assistant-skill-content";
import { getShortcutTitle, isTouchLikeDevice } from "../shortcut-utils";
import { ApiResponse } from "../../../type";
import { DraftAiSaveGate } from "../draft-ai-save-gate";
import { useArticleAiQueue } from "./use-article-ai-queue";

type ArticleAiAssistantConfigProps = {
    data: ArticleEditState;
    draftAiSaveGate: DraftAiSaveGate;
    offline: boolean;
    axiosInstance: AxiosInstance;
    onAiMessagesChange?: (messages: AIContent[], articleId?: number) => void;
    onArticleUpdated?: (event: ArticleUpdatedEvent) => void;
    onApplyValues: ApplyAiValues;
    getSkillContextRevision: SkillContextRevision;

    onApplyGeneratedCover?: (cover: {
        dataUrl: string;
        extension?: string;
        messageId?: string;
    }) => Promise<string | undefined>;
};

type ArticleAiAssistantButtonProps = ArticleAiAssistantConfigProps & {
    getContainer?: () => HTMLElement;
    aiDrawerWidth?: number | "default" | "large";
    stateCache?: AIStateCache;
    open?: boolean;
    onOpenChange?: (open: boolean) => void;
    onAiDrawerSizeChange?: (newSize: number) => void;
};

const CHAT_CONTENT_MAX_WIDTH = 768;
const AI_ASSISTANT_SHORTCUT = {
    alt: true,
    shift: true,
    key: "A",
};

const AI_ASSISTANT_STATE_CACHE_KEY_PREFIX = "ai/chat/state";
let articleAiAssistantDrawerOpen = false;

export const getArticleAiAssistantDrawerOpen = () => articleAiAssistantDrawerOpen || getAIButtonDrawerOpen();

export const useArticleAiAssistantConfig = ({
    data,
    draftAiSaveGate,
    offline,
    axiosInstance,
    onAiMessagesChange,
    onArticleUpdated,
    onApplyValues,
    getSkillContextRevision,
    onApplyGeneratedCover,
}: ArticleAiAssistantConfigProps) => {
    const [loadingKey, setLoadingKey] = useState<string>();
    const [cropModalOpen, setCropModalOpen] = useState<boolean>(false);
    const [croppingImageUrl, setCroppingImageUrl] = useState<string>("");
    const [applyingCoverMessageId, setApplyingCoverMessageId] = useState<string>();
    const [toolPayloads, setToolPayloads] = useState<Record<number, AssistantToolPayload>>({});
    const [selectedTitles, setSelectedTitles] = useState<Record<number, string>>({});
    const [aiMessagesExporting, setAiMessagesExporting] = useState(false);
    const [aiMessagesClearing, setAiMessagesClearing] = useState(false);
    const { message } = useUiApp();
    const theme = useTheme();
    const latestDataRef = useRef(data);
    latestDataRef.current = data;
    const skillRequest = useRef<AbortController>();
    const scope = `${getSsDate().key}/${data.article.logId || "draft"}`;
    const scopeRef = useRef(scope);
    scopeRef.current = scope;
    const migrateQueue = useRef<(scope: string) => void>();

    const aiMessages = data.aiMessages ? data.aiMessages : [];
    const chat = useArticleChat(
        axiosInstance,
        offline || data.aiConfigured !== true,
        scope,
        onAiMessagesChange,
        (event) => {
            if (!data.article.logId && event.created) migrateQueue.current?.(`${getSsDate().key}/${event.articleId}`);
            onArticleUpdated?.(event);
        },
        { articleId: data.article.logId || 0, messages: aiMessages }
    );
    const visibleMessages = aiMessages;
    const activeRun = aiMessages.some(
        (content) =>
            isChatMessage(content) &&
            content.run &&
            ["awaiting_approval", "awaiting_input", "running", "executing"].includes(content.run.status)
    );
    const runningElsewhere = aiMessages.some(
        (content) => isChatMessage(content) && content.run && ["running", "executing"].includes(content.run.status)
    );
    const busy = Boolean(loadingKey) || chat.busy;
    const applicationBlockedRef = useRef(false);
    applicationBlockedRef.current = activeRun || busy;
    const queue = useArticleAiQueue(
        scope,
        offline || data.aiConfigured !== true || busy || activeRun || aiMessagesClearing,
        ({ input, tool, selectedText }) => sendMessage(input, tool, selectedText)
    );
    migrateQueue.current = queue.migrate;

    useEffect(() => {
        setLoadingKey(undefined);
        return () => {
            skillRequest.current?.abort();
            skillRequest.current = undefined;
        };
    }, [scope]);

    const submitMessage = (input: string, tool?: AssistantTool, selectedText?: string) => {
        if (!input.trim() || offline || data.aiConfigured !== true || aiMessagesClearing) return false;
        queue.add(input.trim(), tool, selectedText);
        return true;
    };

    const stopGeneration = () => {
        queue.pause();
        if (chat.busy) chat.stop();
        skillRequest.current?.abort();
    };

    useEffect(() => {
        // A paused tool run may already have created the draft. Keep ordinary creation blocked
        // until its remaining approvals finish and the editor adopts the persisted article ID.
        if (!data.article.logId && activeRun) return draftAiSaveGate.tryBeginAiRequest(0);
    }, [data.article.logId, activeRun, draftAiSaveGate]);

    useEffect(() => {
        setToolPayloads({});
        setSelectedTitles({});
    }, [data.article.logId]);

    const getArticleAiRequestBody = (selectedText?: string) => {
        const requestBody = {
            title: latestDataRef.current.article.title || "",
            alias: latestDataRef.current.article.alias || "",
            markdown: latestDataRef.current.article.markdown || "",
            digest: latestDataRef.current.article.digest || "",
            keywords: latestDataRef.current.article.keywords || "",
            thumbnail: latestDataRef.current.article.thumbnail || "",
            selectedText: selectedText?.trim() || "",
        };
        return requestBody;
    };

    const getArticleIdParam = () => `${latestDataRef.current.article.logId ? latestDataRef.current.article.logId : 0}`;

    const downloadJson = (payload: ArticleAiMessageExportResponse) => {
        const blob = new Blob([JSON.stringify(payload, null, 2)], {
            type: "application/json;charset=utf-8",
        });
        const url = URL.createObjectURL(blob);
        const link = document.createElement("a");
        link.href = url;
        link.download = `zrlog-article-${payload.draft ? "draft" : payload.articleId || "draft"}-ai-messages.json`;
        document.body.appendChild(link);
        link.click();
        document.body.removeChild(link);
        URL.revokeObjectURL(url);
    };

    const exportAiMessages = async () => {
        if (aiMessagesExporting || visibleMessages.length === 0) {
            return;
        }
        setAiMessagesExporting(true);
        try {
            const { data: response } = await axiosInstance.get<ApiResponse<ArticleAiMessageExportResponse>>(
                `/api/admin/article/ai/messages/export?id=${getArticleIdParam()}`
            );
            if (response.error) {
                await message.error(response.message || getRes().error.unknown);
                return;
            }
            downloadJson(response.data);
            await message.success(getRes().articleEdit.assistant.exportAiMessagesSuccess);
        } catch (e) {
            await message.error(e instanceof Error ? e.message : getRes().error.unknown);
        } finally {
            setAiMessagesExporting(false);
        }
    };

    const clearAiMessages = async () => {
        if (aiMessagesClearing || visibleMessages.length === 0) {
            return;
        }
        const articleId = latestDataRef.current.article.logId || 0;
        const releaseRequest = draftAiSaveGate.tryBeginAiRequest(articleId);
        if (!releaseRequest) {
            void message.warning(getRes().articleEdit.assistant.saveInProgress);
            return;
        }
        try {
            setAiMessagesClearing(true);
            const { data: response } = await axiosInstance.post<ApiResponse<boolean>>(
                `/api/admin/article/ai/messages/clear?id=${articleId}`
            );
            if (response.error) {
                await message.error(response.message || getRes().error.unknown);
                return;
            }
            setToolPayloads({});
            setSelectedTitles({});
            chat.clear();
            queue.clear();
            onAiMessagesChange?.([], articleId);
            await message.success(getRes().articleEdit.assistant.clearAiMessagesSuccess);
        } catch (e) {
            await message.error(e instanceof Error ? e.message : getRes().error.unknown);
        } finally {
            setAiMessagesClearing(false);
            releaseRequest();
        }
    };

    const cacheToolPayload = (messageIndex: number, toolPayload?: AssistantToolPayload) => {
        if (!toolPayload) {
            return;
        }
        setToolPayloads((prevState) => ({ ...prevState, [messageIndex]: toolPayload }));
        if (toolPayload.tool === "title") {
            const firstTitle = toolPayload.payload.titles?.[0] || "";
            if (firstTitle) {
                setSelectedTitles((prevState) =>
                    prevState[messageIndex] ? prevState : { ...prevState, [messageIndex]: firstTitle }
                );
            }
        }
    };

    const buildAssistantContent = (
        baseContent: AIContent,
        content: string,
        thinking: boolean,
        reasoningContent?: string,
        toolPayload?: AssistantToolPayload,
        messageId?: string
    ): ToolAwareAIContent => ({
        ...baseContent,
        content,
        thinking,
        ...(reasoningContent ? { reasoningContent } : {}),
        ...(messageId ? { messageId } : {}),
        ...(toolPayload ? { tool: toolPayload.tool, payload: toolPayload.payload } : {}),
    });

    const buildErrorContent = (
        errorMessage: string,
        status?: number,
        errorMeta?: ArticleAiErrorMeta
    ): ToolAwareAIContent => ({
        role: "assistant",
        content: errorMessage,
        thinking: false,
        messageType: "error",
        errorMeta: {
            ...errorMeta,
            provider: errorMeta?.provider || (data.aiProvider ? `${data.aiProvider}` : undefined),
            model: errorMeta?.model || data.aiModel,
            status: errorMeta?.status || status,
        },
    });

    const getToolPayload = (content: AIContent, messageIndex: number): AssistantToolPayload | undefined => {
        const toolAwareContent = content as ToolAwareAIContent;
        if (isAssistantTool(toolAwareContent.tool) && toolAwareContent.payload) {
            return { tool: toolAwareContent.tool, payload: toolAwareContent.payload } as AssistantToolPayload;
        }
        return toolPayloads[messageIndex];
    };

    const updateToolPayload = (
        messageIndex: number,
        toolPayload: AssistantToolPayload,
        persist = true,
        startedArticleId?: number
    ) => {
        const messageId = (aiMessages[messageIndex] as ToolAwareAIContent | undefined)?.messageId;
        const articleId = startedArticleId ?? latestDataRef.current.article.logId ?? 0;
        const releaseRequest = messageId && persist ? draftAiSaveGate.tryBeginAiRequest(articleId) : undefined;
        if (messageId && persist && !releaseRequest) {
            void message.warning(getRes().articleEdit.assistant.saveInProgress);
            return;
        }
        try {
            setToolPayloads((prevState) => ({ ...prevState, [messageIndex]: toolPayload }));
            onAiMessagesChange?.(
                aiMessages.map((content, index) => {
                    if (index !== messageIndex) {
                        return content;
                    }
                    return {
                        ...content,
                        tool: toolPayload.tool,
                        payload: toolPayload.payload,
                    } as ToolAwareAIContent;
                }),
                articleId
            );
        } catch (error) {
            releaseRequest?.();
            throw error;
        }
        if (messageId && persist && releaseRequest) {
            void (async () => {
                try {
                    await axiosInstance.post(`/api/admin/article/ai/message?id=${articleId}`, {
                        messageId,
                        tool: toolPayload.tool,
                        payload: toolPayload.payload,
                    });
                } catch {
                    // Local state is already updated; the next AI response refresh can reconcile persistence failures.
                } finally {
                    releaseRequest();
                }
            })();
        }
    };

    const uploadTempImage = async (dataUrl: string) => {
        const blob = await (await fetch(dataUrl)).blob();
        const formData = new FormData();
        formData.append("imgFile", blob, "ai-cover-crop.png");
        const { data: response } = await axiosInstance.post("/api/admin/upload?dir=ai-cover&temporary=true", formData);
        if (response.error) {
            throw new Error(response.message || getRes().error.unknown);
        }
        return response.data.url as string;
    };

    const getMessageTool = (content: AIContent): AssistantTool | undefined => {
        const toolAwareContent = content as ToolAwareAIContent;
        if (isAssistantTool(toolAwareContent.tool)) {
            return toolAwareContent.tool;
        }
        return undefined;
    };

    const sendMessage = async (
        messageInput: string,
        tool?: AssistantTool,
        selectedText?: string
    ): Promise<boolean | undefined> => {
        const normalizedInput = messageInput.trim();
        if (!normalizedInput || loadingKey || chat.busy || activeRun) {
            return undefined;
        }
        const articleId = latestDataRef.current.article.logId || 0;
        const releaseRequest = draftAiSaveGate.tryBeginAiRequest(articleId);
        if (!releaseRequest) {
            void message.warning(getRes().articleEdit.assistant.saveInProgress);
            return undefined;
        }
        if (!tool) {
            setLoadingKey("chat");
            try {
                const editorContext = getArticleAiRequestBody(selectedText);
                await chat.send(normalizedInput, aiMessages, articleId, undefined, {
                    editorContext,
                    contextRevision: editorContextRevision(editorContext),
                });
                return chat.outcome.current;
            } finally {
                if (scopeRef.current === scope) setLoadingKey(undefined);
                releaseRequest();
            }
        }
        const controller = new AbortController();
        skillRequest.current = controller;
        const baseContents = [...aiMessages];
        const userContent: ToolAwareAIContent = {
            role: "user",
            content: normalizedInput,
            thinking: false,
            ...(tool ? { tool } : {}),
        };
        const assistantContent: AIContent = {
            role: "assistant",
            content: "",
            thinking: true,
        };
        const assistantIndex = baseContents.length + 1;
        const initialContents = [...baseContents, userContent, assistantContent];
        onAiMessagesChange?.(initialContents, articleId);
        setLoadingKey(tool || "chat");
        const showRequestError = async (errorMessage: string, status?: number, errorMeta?: ArticleAiErrorMeta) => {
            await message.error(errorMessage);
            onAiMessagesChange?.(
                [...baseContents, userContent, buildErrorContent(errorMessage, status, errorMeta)],
                articleId
            );
        };

        try {
            // Removed local cover generation short-circuit
            const query = new URLSearchParams({
                id: `${articleId}`,
                input: normalizedInput,
            });
            if (tool) {
                query.set("tool", tool);
            }
            let currentContent = "";
            const { data: responseData, status } = await axiosInstance.post(
                `/api/admin/article/ai?${query.toString()}`,
                tool ? getArticleAiRequestBody(selectedText) : null,
                {
                    signal: controller.signal,
                    adapter: "xhr",
                    headers: {
                        accept: "text/event-stream",
                    },
                    validateStatus: () => true,
                    responseType: "text",
                    onDownloadProgress: (progressEvent) => {
                        if (controller.signal.aborted) return;
                        const target = progressEvent.event?.target as XMLHttpRequest | undefined;
                        const currentTarget = progressEvent.event?.currentTarget as XMLHttpRequest | undefined;
                        const responseText = target?.responseText || currentTarget?.responseText || "";
                        const parsed = parseSseResponse(responseText);
                        if (parsed.errorMessage) {
                            return;
                        }
                        currentContent = parsed.content;
                        cacheToolPayload(assistantIndex, parsed.toolPayload);
                        onAiMessagesChange?.(
                            [
                                ...baseContents,
                                userContent,
                                buildAssistantContent(
                                    assistantContent,
                                    currentContent,
                                    true,
                                    parsed.reasoningContent,
                                    parsed.toolPayload,
                                    parsed.messageId
                                ),
                            ],
                            articleId
                        );
                    },
                }
            );
            if (controller.signal.aborted) return false;
            const parsed = parseSseResponse(responseData || "");
            if (status < 200 || status >= 300) {
                await showRequestError(
                    parsed.errorMessage || formatLabelValue(getRes().error.requestError, status),
                    status,
                    parsed.errorMeta
                );
                return false;
            }
            if (parsed.errorMessage) {
                await showRequestError(parsed.errorMessage, undefined, parsed.errorMeta);
                return false;
            }
            currentContent = parsed.content || currentContent;
            cacheToolPayload(assistantIndex, parsed.toolPayload);
            onAiMessagesChange?.(
                [
                    ...baseContents,
                    userContent,
                    buildAssistantContent(
                        assistantContent,
                        currentContent,
                        false,
                        parsed.reasoningContent,
                        parsed.toolPayload,
                        parsed.messageId
                    ),
                ],
                articleId
            );
            return true;
        } catch (e) {
            if (controller.signal.aborted) {
                // Discard only this unfinished turn, matching ordinary chat cancellation.
                if (skillRequest.current === controller) onAiMessagesChange?.(baseContents, articleId);
                return false;
            }
            await showRequestError(e instanceof Error ? e.message : getRes().error.unknown);
            return false;
        } finally {
            if (controller.signal.aborted && skillRequest.current === controller) {
                setToolPayloads((previous) =>
                    Object.fromEntries(Object.entries(previous).filter(([index]) => Number(index) < assistantIndex))
                );
                setSelectedTitles((previous) =>
                    Object.fromEntries(Object.entries(previous).filter(([index]) => Number(index) < assistantIndex))
                );
            }
            if (skillRequest.current === controller) {
                skillRequest.current = undefined;
                setLoadingKey(undefined);
            }
            releaseRequest();
        }
    };

    const renderAiErrorMessage = (content: ToolAwareAIContent) => {
        const errorMeta = content.errorMeta;
        const assistantRes = getRes().articleEdit.assistant;
        const getErrorTypeLabel = (errorType?: ArticleAiErrorMeta["errorType"]) => {
            switch (errorType) {
                case "incomplete_response":
                    return assistantRes.requestFailedIncomplete;
                case "provider_request":
                    return assistantRes.requestFailedProviderRequest;
                case "provider_response":
                    return assistantRes.requestFailedProviderResponse;
                case "unsupported_tool":
                    return assistantRes.requestFailedUnsupportedTool;
                case "unsupported_image_generation":
                    return assistantRes.requestFailedUnsupportedImageGeneration;
                case "configuration_required":
                    return assistantRes.requestFailedConfigurationRequired;
                case "unknown":
                    return assistantRes.requestFailedUnknown;
                default:
                    return "";
            }
        };
        const getErrorSuggestion = (errorType?: ArticleAiErrorMeta["errorType"]) => {
            switch (errorType) {
                case "incomplete_response":
                    return assistantRes.requestFailedSuggestionIncomplete;
                case "provider_request":
                    return assistantRes.requestFailedSuggestionProviderRequest;
                case "provider_response":
                    return assistantRes.requestFailedSuggestionProviderResponse;
                case "unsupported_tool":
                    return assistantRes.requestFailedSuggestionUnsupportedTool;
                case "unsupported_image_generation":
                    return assistantRes.requestFailedSuggestionUnsupportedImageGeneration;
                case "configuration_required":
                    return assistantRes.requestFailedSuggestionConfigurationRequired;
                case "unknown":
                default:
                    return assistantRes.requestFailedSuggestionUnknown;
            }
        };
        const errorTypeLabel = getErrorTypeLabel(errorMeta?.errorType);
        const errorSuggestion = getErrorSuggestion(errorMeta?.errorType);
        return (
            <div style={{ display: "flex", justifyContent: "center" }}>
                <Alert
                    type="error"
                    showIcon
                    message={assistantRes.requestFailed}
                    description={
                        <Space direction="vertical" size={6}>
                            <Typography.Text>{content.content || getRes().error.unknown}</Typography.Text>
                            <Typography.Text type="secondary">{errorSuggestion}</Typography.Text>
                            <Space wrap size={6}>
                                <Tag>
                                    {assistantRes.requestFailedProvider}
                                    {getLabelValueSeparator()}
                                    {errorMeta?.provider || assistantRes.requestFailedNotConfigured}
                                </Tag>
                                <Tag>
                                    {assistantRes.requestFailedModel}
                                    {getLabelValueSeparator()}
                                    {errorMeta?.model || assistantRes.requestFailedNotConfigured}
                                </Tag>
                                {errorMeta?.status ? (
                                    <Tag>
                                        {assistantRes.requestFailedStatus}
                                        {getLabelValueSeparator()}
                                        {errorMeta.status}
                                    </Tag>
                                ) : null}
                                {errorTypeLabel ? (
                                    <Tag>
                                        {assistantRes.requestFailedType}
                                        {getLabelValueSeparator()}
                                        {errorTypeLabel}
                                    </Tag>
                                ) : null}
                                {errorMeta?.finishReason ? (
                                    <Tag>
                                        {assistantRes.requestFailedFinishReason}
                                        {getLabelValueSeparator()}
                                        {errorMeta.finishReason}
                                    </Tag>
                                ) : null}
                                {errorMeta?.continuationRounds !== undefined ? (
                                    <Tag>
                                        {assistantRes.requestFailedContinuationRounds}
                                        {getLabelValueSeparator()}
                                        {errorMeta.continuationRounds}
                                    </Tag>
                                ) : null}
                            </Space>
                        </Space>
                    }
                    style={{ maxWidth: "90%" }}
                />
            </div>
        );
    };

    const renderMessage = ({ content, index, defaultNode }: AIButtonRenderMessageOptions) => {
        const status =
            busy && index === aiMessages.length - 1 && content.role === "assistant"
                ? chat.status ||
                  (content.content
                      ? getRes().articleEdit.knowledge.generating
                      : (content as ToolAwareAIContent).reasoningContent
                      ? getRes().articleEdit.knowledge.thinking
                      : getRes().articleEdit.knowledge.waitingForResponse)
                : undefined;
        // The host owns progress; avoid the editor's second spinner/empty thinking label.
        if (status && isValidElement<{ content: AIContent }>(defaultNode)) {
            defaultNode = content.content
                ? cloneElement(defaultNode, { content: { ...content, thinking: false } })
                : null;
        }
        if (isChatMessage(content) && content.run) {
            const run = content.run;
            if (run.interaction)
                return (
                    <ArticleAiInput
                        key={run.interaction.id}
                        run={run}
                        disabled={offline || chat.busy || Boolean(loadingKey)}
                        contextRevision={editorContextRevision(getArticleAiRequestBody())}
                        onExpired={() => void chat.refreshRun(run.articleId)}
                        onRespond={(decision, value) => {
                            const release = draftAiSaveGate.tryBeginAiRequest(run.articleId);
                            if (!release) {
                                void message.warning(getRes().articleEdit.assistant.saveInProgress);
                                return;
                            }
                            void chat
                                .respond(
                                    run,
                                    decision,
                                    value,
                                    editorContextRevision(getArticleAiRequestBody()),
                                    aiMessages
                                )
                                .then(() => {
                                    if (!chat.outcome.current) queue.pause();
                                })
                                .finally(release);
                        }}
                    />
                );
            return (
                <ArticleAiApproval
                    run={run}
                    disabled={offline || chat.busy || Boolean(loadingKey)}
                    onRefresh={() => void chat.refreshRun(run.articleId)}
                    onDecide={(decision) => {
                        const release = draftAiSaveGate.tryBeginAiRequest(run.articleId);
                        if (!release) {
                            void message.warning(getRes().articleEdit.assistant.saveInProgress);
                            return;
                        }
                        void chat
                            .decide(run, decision, aiMessages)
                            .then(() => {
                                if (!chat.outcome.current) queue.pause();
                            })
                            .finally(release);
                    }}
                />
            );
        }
        if (isChatMessage(content)) return renderChatMessage({ content, index, defaultNode }, status);
        const toolAwareContent = content as ToolAwareAIContent;
        if (
            toolAwareContent.messageId &&
            aiMessages.some(
                (entry) =>
                    isChatMessage(entry) &&
                    entry.run?.status === "awaiting_input" &&
                    entry.run.interaction?.resultId === toolAwareContent.messageId
            )
        ) {
            // The input card owns the options while a choice is pending. Restore the normal result card afterwards.
            return null;
        }
        if (toolAwareContent.messageType === "articleContext") {
            return null;
        }
        if (toolAwareContent.messageType === "error") {
            return renderAiErrorMessage(toolAwareContent);
        }
        const toolPayload = content.role === "assistant" ? getToolPayload(content, index) : undefined;
        const messageTool = content.role === "user" ? getMessageTool(content) : undefined;
        if (toolPayload) {
            const contract = toolAwareContent.skillContract;
            if (
                toolAwareContent.messageType === "writingSkill" &&
                (!contract ||
                    contract.version !== 1 ||
                    !Array.isArray(contract.applicableFields) ||
                    !/^[a-f0-9]{32}$/.test(contract.contextRevision))
            )
                return <Alert type="warning" title={getRes().articleEdit.interaction.invalidResult} />;
            const stale = contract && contract.contextRevision !== getSkillContextRevision(toolAwareContent);
            const applyDisabled = Boolean(contract && (stale || activeRun || busy));
            const applyValues = (values: ArticleChangeableValue) => {
                if (
                    scopeRef.current !== scope ||
                    (contract && applicationBlockedRef.current) ||
                    !canApplySkillValues(toolAwareContent, getSkillContextRevision(toolAwareContent), values)
                ) {
                    void message.warning(getRes().articleEdit.interaction.staleResult);
                    return;
                }
                onApplyValues(values, toolAwareContent);
            };
            let toolContent = (
                <ArticleAiAssistantToolContent
                    aiProvider={data.aiProvider}
                    messageIndex={index}
                    messageId={(content as ToolAwareAIContent).messageId}
                    offline={offline}
                    loadingKey={loadingKey}
                    applyingCoverMessageId={applyingCoverMessageId}
                    selectedTitle={selectedTitles[index]}
                    currentMarkdown={data.article.markdown || ""}
                    toolPayload={toolPayload}
                    applyDisabled={applyDisabled}
                    onApplyValues={applyValues}
                    onSelectTitle={(messageIndex, title) =>
                        setSelectedTitles((prevState) => ({
                            ...prevState,
                            [messageIndex]: title,
                        }))
                    }
                    onRefine={(prompt, tool) => void submitMessage(prompt, tool)}
                    onUpdateToolPayload={updateToolPayload}
                    onApplyGeneratedCover={async (cover) => {
                        if (
                            scopeRef.current !== scope ||
                            applyDisabled ||
                            (contract && applicationBlockedRef.current) ||
                            !canApplySkillValues(toolAwareContent, getSkillContextRevision(toolAwareContent), {
                                thumbnail: cover.dataUrl,
                            })
                        )
                            return;
                        const url = await onApplyGeneratedCover?.(cover);
                        if (
                            scopeRef.current !== scope ||
                            (contract && applicationBlockedRef.current) ||
                            !url ||
                            !canApplySkillValues(toolAwareContent, getSkillContextRevision(toolAwareContent), {
                                thumbnail: url,
                            })
                        )
                            return;
                        return url;
                    }}
                    onCoverApplyingChange={setApplyingCoverMessageId}
                    onCropCover={(url) => {
                        if (applyDisabled) return;
                        setCroppingImageUrl(url);
                        setCropModalOpen(true);
                    }}
                />
            );
            if (contract && contract.applicableFields.length > 0 && (stale || activeRun || busy))
                toolContent = (
                    <>
                        <Typography.Paragraph type="secondary">
                            {stale
                                ? getRes().articleEdit.interaction.staleResult
                                : getRes().articleEdit.interaction.pendingResult}
                        </Typography.Paragraph>
                        {toolContent}
                    </>
                );
            return status || toolAwareContent.reasoningContent ? (
                <>
                    <ArticleAiReasoning
                        content={toolAwareContent.reasoningContent}
                        thinking={content.thinking}
                        status={status}
                    />
                    {toolContent}
                </>
            ) : (
                toolContent
            );
        }
        return (
            <>
                {content.role === "assistant" && (
                    <ArticleAiReasoning
                        content={toolAwareContent.reasoningContent}
                        thinking={content.thinking}
                        status={status}
                    />
                )}
                {messageTool && (
                    <Space style={{ display: "flex", justifyContent: "flex-end" }}>
                        <Tag color="processing">{getAssistantToolLabel(messageTool)}</Tag>
                    </Space>
                )}
                {defaultNode}
            </>
        );
    };

    const renderFooter = (selectedText?: string) => (
        <ArticleAiAssistantSkillContent
            key={queue.key}
            aiProvider={data.aiProvider}
            disabled={offline || data.aiConfigured !== true || aiMessagesClearing}
            busy={busy}
            waiting={activeRun}
            onStop={busy ? stopGeneration : undefined}
            queuedMessages={queue.messages}
            queuePaused={queue.paused}
            onRemoveQueued={queue.remove}
            onResumeQueue={queue.resume}
            theme={theme}
            selectedText={selectedText}
            markdownLength={(data.article.markdown || "").trim().length}
            onSubmit={(messageInput, tool) => submitMessage(messageInput, tool, selectedText)}
        />
    );

    const overlays = (
        <>
            <ImageCropper
                open={cropModalOpen}
                imageUrl={croppingImageUrl}
                aspectRatio={parseCoverAspectRatio(data.articleCoverAspectRatio)}
                resolveImageUrl={resolveBackendCropImageUrl}
                onCancel={() => setCropModalOpen(false)}
                onError={(errorMessage) => message.error(errorMessage)}
                onOk={async (croppedDataUrl) => {
                    const articleId = latestDataRef.current.article.logId || 0;
                    const releaseRequest = draftAiSaveGate.tryBeginAiRequest(articleId);
                    if (!releaseRequest) {
                        void message.warning(getRes().articleEdit.assistant.saveInProgress);
                        return;
                    }
                    try {
                        const targetIndex = aiMessages.findIndex((m, index) => {
                            const tp = getToolPayload(m, index);
                            return (
                                tp &&
                                tp.tool === "cover" &&
                                tp.payload.url &&
                                tryAppendBackendServerUrl(tp.payload.url) === croppingImageUrl
                            );
                        });
                        if (targetIndex >= 0) {
                            const tempUrl = await uploadTempImage(croppedDataUrl);
                            updateToolPayload(
                                targetIndex,
                                {
                                    tool: "cover",
                                    payload: {
                                        url: tempUrl,
                                    },
                                },
                                true,
                                articleId
                            );
                        }
                        setCropModalOpen(false);
                    } catch (e) {
                        await message.error(e instanceof Error ? e.message : getRes().error.unknown);
                    } finally {
                        releaseRequest();
                    }
                }}
            />
        </>
    );

    return {
        conversationActions: {
            disabled: offline || Boolean(loadingKey) || chat.busy || runningElsewhere || aiMessages.length === 0,
            exporting: aiMessagesExporting,
            clearing: aiMessagesClearing,
            onExport: exportAiMessages,
            onClear: clearAiMessages,
        },
        messages: visibleMessages,
        contentMaxWidth: CHAT_CONTENT_MAX_WIDTH,
        renderMessage,
        renderFooter,
        overlays,
    };
};

const ArticleAiAssistantButton: FunctionComponent<ArticleAiAssistantButtonProps> = ({
    data,
    draftAiSaveGate,
    offline,
    axiosInstance,
    onAiMessagesChange,
    onArticleUpdated,
    onApplyValues,
    getSkillContextRevision,
    onApplyGeneratedCover,
    getContainer,
    aiDrawerWidth,
    stateCache,
    open,
    onOpenChange,
    onAiDrawerSizeChange,
}) => {
    const [innerOpen, setInnerOpen] = useState(false);
    const screens = useArticleEditorScreens();
    const theme = useTheme();
    const mergedOpen = open ?? innerOpen;
    const updateOpen = (nextOpen: boolean) => {
        setInnerOpen(nextOpen);
        onOpenChange?.(nextOpen);
    };
    const assistantConfig = useArticleAiAssistantConfig({
        data,
        draftAiSaveGate,
        offline,
        axiosInstance,
        onAiMessagesChange,
        onArticleUpdated,
        onApplyValues,
        getSkillContextRevision,
        onApplyGeneratedCover,
    });
    const aiStateCacheKey = `${AI_ASSISTANT_STATE_CACHE_KEY_PREFIX}/${
        data.article.logId ? data.article.logId : "draft"
    }`;
    const aiStateCache = useMemo<AIStateCache>(
        () => ({
            key: aiStateCacheKey,
            read: getCacheByKey,
            write: addToCache,
        }),
        [aiStateCacheKey]
    );
    const aiConfigured = data.aiConfigured === true;

    useEffect(() => {
        articleAiAssistantDrawerOpen = mergedOpen;
        return () => {
            articleAiAssistantDrawerOpen = false;
        };
    }, [mergedOpen]);

    useEffect(() => {
        const handleKeyPress = (event: KeyboardEvent) => {
            if (!aiConfigured || mergedOpen || isTouchLikeDevice()) {
                return;
            }
            if (event.altKey && event.shiftKey && event.key.toLowerCase() === "a") {
                event.preventDefault();
                updateOpen(true);
            }
        };

        window.addEventListener("keydown", handleKeyPress);
        return () => {
            window.removeEventListener("keydown", handleKeyPress);
        };
    }, [aiConfigured, mergedOpen, onOpenChange]);

    const trigger = (
        <Button
            type="primary"
            className="btn"
            style={{
                width: screens.sm ? 120 : undefined,
                background: `linear-gradient(135deg, ${theme.colorInfo}, ${theme.colorPrimary})`,
                border: "none",
            }}
            onClick={aiConfigured ? () => updateOpen(true) : undefined}
            icon={aiConfigured ? <AIIcon name={data.aiProvider} /> : <RobotIcon />}
            title={getShortcutTitle(getRes().websiteAi.label, AI_ASSISTANT_SHORTCUT)}
            aria-label={getRes().websiteAi.label}
        >
            {screens.sm && <span>{getRes().websiteAi.label}</span>}
        </Button>
    );

    if (!aiConfigured) {
        return (
            <AIButton dark={getAppState().dark} configUrl={getRealRouteUrl("/website/ai")}>
                {trigger}
            </AIButton>
        );
    }
    return (
        <>
            {trigger}
            <ArticleAiAssistantDrawer
                data={data}
                config={assistantConfig}
                open={mergedOpen}
                onClose={() => updateOpen(false)}
                getContainer={getContainer}
                width={aiDrawerWidth}
                onSizeChange={onAiDrawerSizeChange}
                stateCache={stateCache ?? aiStateCache}
            />
        </>
    );
};

export default ArticleAiAssistantButton;
