import { assistantComposerScope } from "./chat/assistant-composer-drafts";
import TaskResultMessage from "./tasks/task-result-message";
import { encodeAiFiles } from "./chat/assistant-files";
import { Alert, Space, Typography } from "antd";
import Tag from "@zrlog/ui/antd/Tag";
import { useUiApp } from "@zrlog/ui/feedback";
import { ApplyAiValues, SkillContextRevision } from "../articleEdit/use-article-field-ai";

import { isChatMessage, renderChatMessage, useAssistantChat } from "./chat/use-assistant-chat";
import AssistantApproval from "./chat/assistant-approval";
import AssistantInputRequest from "./chat/assistant-input-request";
import { canApplySkillValues, editorContextRevision } from "./writing/writing-skill-contract";
import AssistantReasoning from "./chat/assistant-reasoning";

import { cloneElement, isValidElement, useEffect, useRef, useState } from "react";
import { AIContent } from "@zrlog/editor/core/ai/AIContentItem";
import { AIButtonRenderMessageOptions } from "@zrlog/editor/core/ai/AIButton";
import { AxiosInstance } from "axios";
import { formatLabelValue, getLabelValueSeparator, getRes, tryAppendBackendServerUrl } from "../../utils/constants";
import { getSsDate } from "../../base/SsData";
import { ArticleChangeableValue, ArticleEditState } from "../articleEdit/index.types";
import { useTheme } from "antd-style";
import ImageCropper from "../../common/ImageCropper";
import { parseCoverAspectRatio } from "../articleEdit/cover-aspect-ratio";
import { resolveBackendCropImageUrl } from "../../utils/crop-image-url";
import { AssistantErrorMeta, AssistantMessageExportResponse, AssistantMessage } from "./chat/assistant-chat.types";
import {
    ArticleUpdatedEvent,
    WritingSkill,
    WritingSkillPayload,
    isWritingSkill,
} from "./writing/writing-assistant.types";
import { parseSseResponse } from "./chat/assistant-sse";
import { getWritingSkillLabel } from "./writing/skills/writing-skills";
import WritingSkillContent from "./writing/skills/writing-skill-content";
import AssistantComposer from "./chat/assistant-composer";
import { ApiResponse } from "../../type";
import { DraftAiSaveGate } from "../articleEdit/draft-ai-save-gate";
import { useAssistantQueue } from "./chat/use-assistant-queue";
import type { AiMentionInput } from "./chat/assistant-mentions";

/** Data consumed by a conversation; editor-only save/layout state stays with the editor. */
export type AssistantConversationData = Pick<
    ArticleEditState,
    "article" | "aiProvider" | "aiModel" | "aiConfigured" | "aiMessages" | "aiFileInput" | "articleCoverAspectRatio"
>;

export type AssistantConversationContext = { articleId?: number; taskId?: string; sessionId?: string };

export type AssistantConversationProps = {
    context?: AssistantConversationContext;
    data: AssistantConversationData;
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

const CHAT_CONTENT_MAX_WIDTH = 768;

export const useAssistantConversation = ({
    context,
    data,
    draftAiSaveGate,
    offline,
    axiosInstance,
    onAiMessagesChange,
    onArticleUpdated,
    onApplyValues,
    getSkillContextRevision,
    onApplyGeneratedCover,
}: AssistantConversationProps) => {
    const articleContext = context === undefined || context.articleId !== undefined;
    const [loadingKey, setLoadingKey] = useState<string>();
    const [cropModalOpen, setCropModalOpen] = useState<boolean>(false);
    const [croppingImageUrl, setCroppingImageUrl] = useState<string>("");
    const [applyingCoverMessageId, setApplyingCoverMessageId] = useState<string>();
    const [toolPayloads, setToolPayloads] = useState<Record<number, WritingSkillPayload>>({});
    const [selectedTitles, setSelectedTitles] = useState<Record<number, string>>({});
    const [aiMessagesExporting, setAiMessagesExporting] = useState(false);
    const [aiMessagesClearing, setAiMessagesClearing] = useState(false);
    const { message } = useUiApp();
    const theme = useTheme();
    const latestDataRef = useRef(data);
    latestDataRef.current = data;
    const skillRequest = useRef<AbortController>();
    const fileReadGeneration = useRef(0);
    const scope = assistantComposerScope(getSsDate().key, {
        sessionId: context?.sessionId,
        articleId: articleContext ? data.article.logId || 0 : undefined,
        taskId: context?.taskId,
    });
    const scopeRef = useRef(scope);
    scopeRef.current = scope;
    const migrateQueue = useRef<(scope: string) => void>();

    const aiMessages = data.aiMessages ? data.aiMessages : [];
    const chat = useAssistantChat(
        axiosInstance,
        offline || data.aiConfigured !== true,
        scope,
        onAiMessagesChange,
        (event) => {
            if (articleContext && !data.article.logId && event.created)
                migrateQueue.current?.(
                    `${getSsDate().key}/${context?.sessionId ? `session/${context.sessionId}` : event.articleId}`
                );
            onArticleUpdated?.(event);
        },
        {
            articleId: articleContext ? data.article.logId || 0 : undefined,
            taskId: context?.taskId,
            sessionId: context?.sessionId,
            messages: aiMessages,
        }
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
    const queue = useAssistantQueue(
        scope,
        offline || data.aiConfigured !== true || busy || activeRun || aiMessagesClearing,
        ({ input, tool, selectedText, files, references }) => sendMessage(input, tool, selectedText, files, references)
    );
    migrateQueue.current = queue.migrate;

    useEffect(() => {
        setLoadingKey(undefined);
        return () => {
            fileReadGeneration.current++;
            skillRequest.current?.abort();
            skillRequest.current = undefined;
        };
    }, [scope]);

    const submitMessage = (
        input: string,
        tool?: WritingSkill,
        selectedText?: string,
        files?: File[],
        references?: AiMentionInput[]
    ) => {
        if (!input.trim() || offline || data.aiConfigured !== true || aiMessagesClearing) return false;
        queue.add(input.trim(), tool, selectedText, files, references);
        return true;
    };

    const stopGeneration = () => {
        queue.pause();
        fileReadGeneration.current++;
        if (chat.busy) chat.stop();
        skillRequest.current?.abort();
    };

    useEffect(() => {
        // A paused tool run may already have created the draft. Keep ordinary creation blocked
        // until its remaining approvals finish and the editor adopts the persisted article ID.
        if (articleContext && !data.article.logId && activeRun) return draftAiSaveGate.tryBeginAiRequest(0);
    }, [articleContext, data.article.logId, activeRun, draftAiSaveGate]);

    useEffect(() => {
        setToolPayloads({});
        setSelectedTitles({});
    }, [data.article.logId]);

    const getEditorContext = (selectedText?: string) => {
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

    const getContextQuery = (articleId = latestDataRef.current.article.logId || 0) => {
        const params = new URLSearchParams();
        if (articleContext) params.set("id", String(articleId));
        if (context?.taskId) params.set("taskId", context.taskId);
        if (context?.sessionId) params.set("sessionId", context.sessionId);
        return params.toString();
    };

    const downloadJson = (payload: AssistantMessageExportResponse) => {
        const blob = new Blob([JSON.stringify(payload, null, 2)], {
            type: "application/json;charset=utf-8",
        });
        const url = URL.createObjectURL(blob);
        const link = document.createElement("a");
        link.href = url;
        const contextName = payload.taskId
            ? `task-${payload.taskId}`
            : payload.articleId == null
            ? "general"
            : `article-${payload.draft ? "draft" : payload.articleId}`;
        link.download = `zrlog-assistant-${contextName}.json`;
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
            const { data: response } = await axiosInstance.get<ApiResponse<AssistantMessageExportResponse>>(
                `/api/admin/assistant/chat/messages/export?${getContextQuery()}`
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
        const releaseRequest = articleContext ? draftAiSaveGate.tryBeginAiRequest(articleId) : () => undefined;
        if (!releaseRequest) {
            void message.warning(getRes().articleEdit.assistant.saveInProgress);
            return;
        }
        try {
            setAiMessagesClearing(true);
            const { data: response } = await axiosInstance.post<ApiResponse<boolean>>(
                `/api/admin/assistant/chat/messages/clear?${getContextQuery()}`
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
            await message.success(response.message || getRes().common.operationCompleted);
        } catch (e) {
            await message.error(e instanceof Error ? e.message : getRes().error.unknown);
        } finally {
            setAiMessagesClearing(false);
            releaseRequest();
        }
    };

    const cacheToolPayload = (messageIndex: number, toolPayload?: WritingSkillPayload) => {
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
        toolPayload?: WritingSkillPayload,
        messageId?: string
    ): AssistantMessage => ({
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
        errorMeta?: AssistantErrorMeta
    ): AssistantMessage => ({
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

    const getToolPayload = (content: AIContent, messageIndex: number): WritingSkillPayload | undefined => {
        const toolAwareContent = content as AssistantMessage;
        if (isWritingSkill(toolAwareContent.tool) && toolAwareContent.payload) {
            return { tool: toolAwareContent.tool, payload: toolAwareContent.payload } as WritingSkillPayload;
        }
        return toolPayloads[messageIndex];
    };

    const updateToolPayload = (
        messageIndex: number,
        toolPayload: WritingSkillPayload,
        persist = true,
        startedArticleId?: number
    ) => {
        const messageId = (aiMessages[messageIndex] as AssistantMessage | undefined)?.messageId;
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
                    } as AssistantMessage;
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
                    await axiosInstance.post(`/api/admin/assistant/chat/message?${getContextQuery(articleId)}`, {
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

    const getMessageTool = (content: AIContent): WritingSkill | undefined => {
        const toolAwareContent = content as AssistantMessage;
        if (isWritingSkill(toolAwareContent.tool)) {
            return toolAwareContent.tool;
        }
        return undefined;
    };

    const sendMessage = async (
        messageInput: string,
        tool?: WritingSkill,
        selectedText?: string,
        files?: File[],
        references?: AiMentionInput[]
    ): Promise<boolean | undefined> => {
        const normalizedInput = messageInput.trim();
        if (!normalizedInput || loadingKey || chat.busy || activeRun) {
            return undefined;
        }
        const articleId = latestDataRef.current.article.logId || 0;
        const releaseRequest = articleContext ? draftAiSaveGate.tryBeginAiRequest(articleId) : () => undefined;
        if (!releaseRequest) {
            void message.warning(getRes().articleEdit.assistant.saveInProgress);
            return undefined;
        }
        if (!tool || files?.length || references?.length) {
            setLoadingKey(files?.length ? "files" : "chat");
            try {
                const fileRead = fileReadGeneration.current;
                const editorContext = getEditorContext(selectedText);
                const fileInputs = files?.length ? await encodeAiFiles(files) : undefined;
                if (scopeRef.current !== scope) return undefined;
                if (fileRead !== fileReadGeneration.current) return false;
                setLoadingKey("chat");
                await chat.send(
                    tool && references?.length ? `${getWritingSkillLabel(tool)}\n${normalizedInput}` : normalizedInput,
                    aiMessages,
                    articleContext ? articleId : undefined,
                    undefined,
                    articleContext
                        ? {
                              editorContext,
                              contextRevision: editorContextRevision(editorContext),
                          }
                        : undefined,
                    fileInputs,
                    files,
                    references
                );

                return chat.outcome.current;
            } catch (error) {
                void message.error(error instanceof Error ? error.message : getRes().articleEdit.files.readFailed);
                return false;
            } finally {
                if (scopeRef.current === scope) setLoadingKey(undefined);
                releaseRequest();
            }
        }
        const controller = new AbortController();
        skillRequest.current = controller;
        const baseContents = [...aiMessages];
        const userContent: AssistantMessage = {
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
        const showRequestError = async (errorMessage: string, status?: number, errorMeta?: AssistantErrorMeta) => {
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
                `/api/admin/assistant/chat?${query.toString()}${
                    context?.sessionId ? "&sessionId=" + context.sessionId : ""
                }`,
                tool ? getEditorContext(selectedText) : null,
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

    const renderAiErrorMessage = (content: AssistantMessage) => {
        const errorMeta = content.errorMeta;
        const assistantRes = getRes().articleEdit.assistant;
        const errorTypeLabel = errorMeta?.errorTypeLabel;
        const errorSuggestion = errorMeta?.suggestion;
        return (
            <div style={{ display: "flex", justifyContent: "center" }}>
                <Alert
                    type="error"
                    showIcon
                    message={assistantRes.requestFailed}
                    description={
                        <Space direction="vertical" size={6}>
                            <Typography.Text>{content.content || getRes().error.unknown}</Typography.Text>
                            {errorSuggestion && <Typography.Text type="secondary">{errorSuggestion}</Typography.Text>}
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
                      : (content as AssistantMessage).reasoningContent
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
                    <AssistantInputRequest
                        key={run.interaction.id}
                        run={run}
                        disabled={offline || chat.busy || Boolean(loadingKey)}
                        contextRevision={editorContextRevision(getEditorContext())}
                        onExpired={() => void chat.refreshRun(run.articleId)}
                        onRespond={(decision, value) => {
                            const release = articleContext
                                ? draftAiSaveGate.tryBeginAiRequest(run.articleId ?? 0)
                                : () => undefined;
                            if (!release) {
                                void message.warning(getRes().articleEdit.assistant.saveInProgress);
                                return;
                            }
                            void chat
                                .respond(run, decision, value, editorContextRevision(getEditorContext()), aiMessages)
                                .then(() => {
                                    if (!chat.outcome.current) queue.pause();
                                })
                                .finally(release);
                        }}
                    />
                );
            return (
                <AssistantApproval
                    run={run}
                    disabled={offline || chat.busy || Boolean(loadingKey)}
                    onRefresh={() => void chat.refreshRun(run.articleId)}
                    onDecide={(decision) => {
                        const release = articleContext
                            ? draftAiSaveGate.tryBeginAiRequest(run.articleId ?? 0)
                            : () => undefined;
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
        if (isChatMessage(content))
            return renderChatMessage({ content, index, defaultNode }, status, {
                api: axiosInstance,
                articleId: articleContext ? data.article.logId || 0 : undefined,
                taskId: context?.taskId,
                sessionId: context?.sessionId,
            });
        const toolAwareContent = content as AssistantMessage;
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
        if (toolAwareContent.messageType === "taskResult" && toolAwareContent.taskResult && context?.taskId) {
            return (
                <TaskResultMessage
                    content={content.content}
                    taskId={context.taskId}
                    runId={toolAwareContent.taskResult.runId}
                />
            );
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
                <WritingSkillContent
                    aiProvider={data.aiProvider}
                    messageIndex={index}
                    messageId={(content as AssistantMessage).messageId}
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
                    <AssistantReasoning
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
                    <AssistantReasoning
                        content={toolAwareContent.reasoningContent}
                        thinking={content.thinking}
                        status={status}
                    />
                )}
                {messageTool && (
                    <Space style={{ display: "flex", justifyContent: "flex-end" }}>
                        <Tag color="processing">{getWritingSkillLabel(messageTool)}</Tag>
                    </Space>
                )}
                {defaultNode}
            </>
        );
    };

    const renderFooter = (selectedText?: string) => (
        <AssistantComposer
            key={queue.key}
            draftKey={scope}
            aiProvider={data.aiProvider}
            writingContext={articleContext}
            api={axiosInstance}
            fileInput={data.aiFileInput}
            preparingFiles={loadingKey === "files"}
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
            onSubmit={(messageInput, tool, files, references) =>
                submitMessage(messageInput, tool, selectedText, files, references)
            }
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
                    const releaseRequest = articleContext
                        ? draftAiSaveGate.tryBeginAiRequest(articleId)
                        : () => undefined;
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
        navigationBusy: busy || runningElsewhere || aiMessagesClearing,
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
