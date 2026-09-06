import { Alert, App, Button, Collapse, Drawer, Grid, Space, Tag, Typography } from "antd";
import { EyeOutlined } from "@ant-design/icons";
import { FunctionComponent, useEffect, useMemo, useRef, useState } from "react";
import { AIContent } from "@editor/dist/ai/AIContentItem";
import AIButton, { AIButtonRenderMessageOptions, AIStateCache, getAIButtonDrawerOpen } from "@editor/dist/ai/AIButton";
import { resolveDrawerWidth } from "@editor/dist/ai/AIDrawer";
import { AxiosInstance } from "axios";
import {
    formatLabelValue,
    getLabelValueSeparator,
    getRealRouteUrl,
    getRes,
    tryAppendBackendServerUrl,
} from "../../../utils/constants";
import { getAppState } from "../../../base/ConfigProviderApp";
import { getEditorUser } from "../../../utils/helpers";
import { ArticleChangeableValue, ArticleEditState } from "../index.types";
import { useTheme } from "antd-style";
import { addToCache, getCacheByKey } from "../../../utils/cache";
import ImageCropper from "../../../common/ImageCropper";
import { parseCoverAspectRatio } from "../cover-aspect-ratio";
import { resolveBackendCropImageUrl } from "../../../utils/crop-image-url";
import {
    ArticleAiErrorMeta,
    ArticleAiMessageExportResponse,
    ArticleAiRequestField,
    ArticleAiRequestFieldSelection,
    ArticleAiRequestPreview,
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
import { markdownToHtmlSyncWithCallback } from "@editor/dist/editor/utils/marked-utils";
import ArticlePreviewSnapshot from "../../article/article-preview-snapshot";
import { collectMarkdownReferenceSummary } from "../markdown-reference-utils";
import { DraftAiSaveGate } from "../draft-ai-save-gate";

type ArticleAiAssistantConfigProps = {
    data: ArticleEditState;
    draftAiSaveGate: DraftAiSaveGate;
    offline: boolean;
    axiosInstance: AxiosInstance;
    onAiMessagesChange?: (messages: AIContent[], articleId?: number) => void;
    onApplyValues: (cv: ArticleChangeableValue) => void;

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
const DEFAULT_TOOL_FIELD_SELECTION: ArticleAiRequestFieldSelection = {
    title: true,
    digest: true,
    keywords: true,
    markdown: true,
};

const AI_ASSISTANT_SHORTCUT = {
    alt: true,
    shift: true,
    key: "A",
};

const AI_ASSISTANT_STATE_CACHE_KEY_PREFIX = "ai/chat/state";
const REQUEST_PREVIEW_SNIPPET_LIMIT = 180;

let articleAiAssistantDrawerOpen = false;

export const getArticleAiAssistantDrawerOpen = () => articleAiAssistantDrawerOpen || getAIButtonDrawerOpen();

type ArticleContextSnapshot = {
    title: string;
    version?: number;
    digest: string;
    keywords: string;
    markdown: string;
};

const extractBetween = (source: string, startMarker: string, endMarker: string) => {
    const start = source.indexOf(startMarker);
    if (start < 0) {
        return "";
    }
    const valueStart = start + startMarker.length;
    const end = source.indexOf(endMarker, valueStart);
    return (end < 0 ? source.substring(valueStart) : source.substring(valueStart, end)).trim();
};

const parseArticleContextSnapshot = (content: ToolAwareAIContent): ArticleContextSnapshot => {
    const rawContent = content.content || "";
    const markdownMarker = "\nMarkdown:\n";
    const markdownIndex = rawContent.indexOf(markdownMarker);
    const metaVersion = content.contextMeta?.articleVersion;
    const parsedVersion = extractBetween(rawContent, "Article version: ", "\nTitle: ");
    const version = metaVersion ?? (parsedVersion ? Number(parsedVersion) : undefined);
    const title =
        content.contextMeta?.title ||
        extractBetween(rawContent, "\nTitle: ", "\nDigest: ") ||
        getRes().articleEdit.assistant.articleContextUntitled;
    return {
        title,
        version: Number.isFinite(version) ? version : undefined,
        digest: extractBetween(rawContent, "\nDigest: ", "\nKeywords: "),
        keywords: extractBetween(rawContent, "\nKeywords: ", markdownMarker),
        markdown: markdownIndex >= 0 ? rawContent.substring(markdownIndex + markdownMarker.length) : rawContent,
    };
};

const buildRequestPreviewSnippet = (value: string) => {
    const normalizedValue = value.trim().replace(/\s+/g, " ");
    if (normalizedValue.length <= REQUEST_PREVIEW_SNIPPET_LIMIT) {
        return normalizedValue;
    }
    return `${normalizedValue.substring(0, REQUEST_PREVIEW_SNIPPET_LIMIT)}...`;
};

export const useArticleAiAssistantConfig = ({
    data,
    draftAiSaveGate,
    offline,
    axiosInstance,
    onAiMessagesChange,
    onApplyValues,
    onApplyGeneratedCover,
}: ArticleAiAssistantConfigProps) => {
    const [loadingKey, setLoadingKey] = useState<string>();
    const [cropModalOpen, setCropModalOpen] = useState<boolean>(false);
    const [croppingImageUrl, setCroppingImageUrl] = useState<string>("");
    const [applyingCoverMessageId, setApplyingCoverMessageId] = useState<string>();
    const [contextAppending, setContextAppending] = useState(false);
    const [contextPreview, setContextPreview] = useState<ArticleContextSnapshot>();
    const [contextPreviewHtml, setContextPreviewHtml] = useState("");
    const [toolPayloads, setToolPayloads] = useState<Record<number, AssistantToolPayload>>({});
    const [selectedTitles, setSelectedTitles] = useState<Record<number, string>>({});
    const [aiMessagesExporting, setAiMessagesExporting] = useState(false);
    const [aiMessagesClearing, setAiMessagesClearing] = useState(false);
    const [includeArticleContextInChat, setIncludeArticleContextInChat] = useState(true);
    const [toolFieldSelection, setToolFieldSelection] =
        useState<ArticleAiRequestFieldSelection>(DEFAULT_TOOL_FIELD_SELECTION);
    const { message } = App.useApp();
    const theme = useTheme();
    const screens = Grid.useBreakpoint();
    const latestDataRef = useRef(data);

    const aiMessages = data.aiMessages ? data.aiMessages : [];

    useEffect(() => {
        latestDataRef.current = data;
    }, [data]);

    useEffect(() => {
        setToolPayloads({});
        setSelectedTitles({});
        setToolFieldSelection(DEFAULT_TOOL_FIELD_SELECTION);
    }, [data.article.logId]);

    const getArticleAiRequestBody = (fieldSelection?: ArticleAiRequestFieldSelection, selectedText?: string) => {
        const requestBody = {
            title: latestDataRef.current.article.title || "",
            alias: latestDataRef.current.article.alias || "",
            markdown: latestDataRef.current.article.markdown || "",
            digest: latestDataRef.current.article.digest || "",
            keywords: latestDataRef.current.article.keywords || "",
            thumbnail: latestDataRef.current.article.thumbnail || "",
            selectedText: selectedText?.trim() || "",
        };
        if (!fieldSelection) {
            return requestBody;
        }
        return {
            title: fieldSelection.title ? requestBody.title : "",
            alias: requestBody.alias,
            markdown: fieldSelection.markdown ? requestBody.markdown : "",
            digest: fieldSelection.digest ? requestBody.digest : "",
            keywords: fieldSelection.keywords ? requestBody.keywords : "",
            thumbnail: requestBody.thumbnail,
            selectedText: requestBody.selectedText,
        };
    };

    const getArticleAiContextRequestBody = () => {
        const requestBody = getArticleAiRequestBody();
        return {
            title: requestBody.title,
            markdown: requestBody.markdown,
            digest: requestBody.digest,
            keywords: requestBody.keywords,
            articleVersion: latestDataRef.current.article.version,
        };
    };

    const hasArticleContextSource = () => {
        const requestBody = getArticleAiRequestBody();
        return Boolean(
            requestBody.title.trim() ||
                requestBody.markdown.trim() ||
                requestBody.digest.trim() ||
                requestBody.keywords.trim()
        );
    };

    const hasArticleContextMessage = () =>
        aiMessages.some((content) => (content as ToolAwareAIContent).messageType === "articleContext");

    const getConversationMessageStats = () =>
        aiMessages.reduce(
            (stats, content) => {
                const toolAwareContent = content as ToolAwareAIContent;
                if (`${toolAwareContent.role}` === "system") {
                    stats.systemMessageCount++;
                    return stats;
                }
                if (toolAwareContent.messageType === "articleContext") {
                    stats.articleContextMessageCount++;
                    return stats;
                }
                if (toolAwareContent.messageType === "error") {
                    stats.errorMessageCount++;
                    return stats;
                }
                if ((toolAwareContent.content || "").trim()) {
                    if (isAssistantTool(toolAwareContent.tool)) {
                        stats.toolMessageCount++;
                    } else {
                        stats.chatMessageCount++;
                    }
                    stats.conversationMessageCount++;
                }
                return stats;
            },
            {
                conversationMessageCount: 0,
                chatMessageCount: 0,
                toolMessageCount: 0,
                articleContextMessageCount: 0,
                systemMessageCount: 0,
                errorMessageCount: 0,
            }
        );

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
        if (aiMessagesExporting || aiMessages.length === 0) {
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
        if (aiMessagesClearing || aiMessages.length === 0) {
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
            onAiMessagesChange?.([], articleId);
            await message.success(getRes().articleEdit.assistant.clearAiMessagesSuccess);
        } catch (e) {
            await message.error(e instanceof Error ? e.message : getRes().error.unknown);
        } finally {
            setAiMessagesClearing(false);
            releaseRequest();
        }
    };

    const buildRequestPreview = (selectedText?: string): ArticleAiRequestPreview => {
        const requestBody = getArticleAiRequestBody(toolFieldSelection, selectedText);
        const title = requestBody.title.trim();
        const alias = requestBody.alias.trim();
        const digest = requestBody.digest.trim();
        const keywords = requestBody.keywords.trim();
        const cover = requestBody.thumbnail.trim();
        const selectedTextValue = requestBody.selectedText.trim();
        const markdown = requestBody.markdown.trim();
        const referenceSummary = collectMarkdownReferenceSummary(markdown);
        const conversationStats = getConversationMessageStats();
        return {
            provider: data.aiProvider,
            model: data.aiModel,
            titleLength: title.length,
            titleSnippet: buildRequestPreviewSnippet(title),
            aliasLength: alias.length,
            aliasSnippet: buildRequestPreviewSnippet(alias),
            digestLength: digest.length,
            digestSnippet: buildRequestPreviewSnippet(digest),
            keywordsLength: keywords.length,
            keywordsSnippet: buildRequestPreviewSnippet(keywords),
            coverLength: cover.length,
            coverSnippet: buildRequestPreviewSnippet(cover),
            selectedTextLength: selectedTextValue.length,
            selectedTextSnippet: buildRequestPreviewSnippet(selectedTextValue),
            markdownLength: markdown.length,
            markdownSnippet: buildRequestPreviewSnippet(markdown),
            imageReferenceCount: referenceSummary.imageReferenceCount,
            imageReferences: referenceSummary.imageReferences,
            linkReferenceCount: referenceSummary.linkReferenceCount,
            linkReferences: referenceSummary.linkReferences,
            externalLinkCount: referenceSummary.externalLinkCount,
            externalLinks: referenceSummary.externalLinks,
            articleContextAdded: hasArticleContextMessage(),
            ...conversationStats,
        };
    };

    const updateToolFieldSelection = (field: ArticleAiRequestField, selected: boolean) => {
        setToolFieldSelection((prevState) => ({ ...prevState, [field]: selected }));
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

    const appendArticleContext = async () => {
        if (contextAppending || loadingKey || !hasArticleContextSource()) {
            return;
        }
        const articleId = latestDataRef.current.article.logId || 0;
        const releaseRequest = draftAiSaveGate.tryBeginAiRequest(articleId);
        if (!releaseRequest) {
            void message.warning(getRes().articleEdit.assistant.saveInProgress);
            return;
        }
        setContextAppending(true);
        try {
            const { data: response } = await axiosInstance.post<ApiResponse<ToolAwareAIContent[]>>(
                `/api/admin/article/ai/context?id=${articleId}`,
                getArticleAiContextRequestBody()
            );
            if (response.error) {
                await message.error(response.message || getRes().error.unknown);
                return;
            }
            onAiMessagesChange?.(response.data || [], articleId);
        } catch (e) {
            await message.error(e instanceof Error ? e.message : getRes().error.unknown);
        } finally {
            setContextAppending(false);
            releaseRequest();
        }
    };

    const openArticleContextPreview = (content: ToolAwareAIContent) => {
        const snapshot = parseArticleContextSnapshot(content);
        const initialHtml = markdownToHtmlSyncWithCallback(snapshot.markdown, (html) => {
            setContextPreviewHtml(html);
        });
        setContextPreview(snapshot);
        setContextPreviewHtml(initialHtml);
    };

    const sendMessage = async (messageInput: string, tool?: AssistantTool, selectedText?: string) => {
        const normalizedInput = messageInput.trim();
        if (!normalizedInput || loadingKey) {
            return;
        }
        const articleId = latestDataRef.current.article.logId || 0;
        const releaseRequest = draftAiSaveGate.tryBeginAiRequest(articleId);
        if (!releaseRequest) {
            void message.warning(getRes().articleEdit.assistant.saveInProgress);
            return;
        }
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
            if (!tool && !includeArticleContextInChat) {
                query.set("includeArticleContext", "false");
            }
            let currentContent = "";
            const { data: responseData, status } = await axiosInstance.post(
                `/api/admin/article/ai?${query.toString()}`,
                tool ? getArticleAiRequestBody(toolFieldSelection, selectedText) : null,
                {
                    adapter: "xhr",
                    headers: {
                        accept: "text/event-stream",
                    },
                    validateStatus: () => true,
                    responseType: "text",
                    onDownloadProgress: (progressEvent) => {
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
            const parsed = parseSseResponse(responseData || "");
            if (status < 200 || status >= 300) {
                await showRequestError(
                    parsed.errorMessage || formatLabelValue(getRes().error.requestError, status),
                    status,
                    parsed.errorMeta
                );
                return;
            }
            if (parsed.errorMessage) {
                await showRequestError(parsed.errorMessage, undefined, parsed.errorMeta);
                return;
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
        } catch (e) {
            await showRequestError(e instanceof Error ? e.message : getRes().error.unknown);
        } finally {
            setLoadingKey(undefined);
            releaseRequest();
        }
    };

    const renderArticleContextMessage = (content: ToolAwareAIContent) => {
        const contextMeta = content.contextMeta;
        const markdownLength = contextMeta?.markdownLength ?? content.content.length;
        const title = contextMeta?.title || getRes().articleEdit.assistant.articleContextUntitled;
        const versionText =
            contextMeta?.articleVersion !== undefined
                ? `${getRes().articleEdit.assistant.articleContextVersionPrefix}${contextMeta.articleVersion}`
                : "";
        const lengthText = `${getRes().articleEdit.assistant.articleContextLengthPrefix}${markdownLength}${
            getRes().articleEdit.assistant.articleContextLengthSuffix
        }`;
        const borderSecondary = `${theme.lineWidth}px ${theme.lineType} ${theme.colorBorderSecondary}`;
        return (
            <div style={{ display: "flex", justifyContent: "center" }}>
                <div
                    style={{
                        background: theme.colorFillQuaternary,
                        border: borderSecondary,
                        borderRadius: theme.borderRadiusLG,
                        padding: "10px 12px",
                        maxWidth: "90%",
                    }}
                >
                    <Space direction="vertical" size={4}>
                        <Space wrap>
                            <Tag color="processing">{getRes().articleEdit.assistant.articleContextTag}</Tag>
                            <Typography.Text strong ellipsis style={{ maxWidth: 360 }}>
                                {title}
                            </Typography.Text>
                        </Space>
                        <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                            {[versionText, lengthText].filter(Boolean).join(" · ")}
                        </Typography.Text>
                        <Button
                            type="link"
                            size="small"
                            icon={<EyeOutlined />}
                            style={{ paddingInline: 0, alignSelf: "flex-start" }}
                            onClick={() => openArticleContextPreview(content)}
                        >
                            {getRes().articleEdit.assistant.viewArticleContext}
                        </Button>
                    </Space>
                </div>
            </div>
        );
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
                                    {assistantRes.requestPreviewProvider}
                                    {getLabelValueSeparator()}
                                    {errorMeta?.provider || assistantRes.requestPreviewNotConfigured}
                                </Tag>
                                <Tag>
                                    {assistantRes.requestPreviewModel}
                                    {getLabelValueSeparator()}
                                    {errorMeta?.model || assistantRes.requestPreviewNotConfigured}
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

    const renderReasoningProcess = (content: ToolAwareAIContent) => {
        if (content.role !== "assistant" || !content.reasoningContent) {
            return null;
        }
        return (
            <div style={{ maxWidth: CHAT_CONTENT_MAX_WIDTH, marginBottom: 8 }}>
                <Collapse
                    size="small"
                    ghost
                    items={[
                        {
                            key: "reasoning",
                            label: getRes().articleEdit.assistant.reasoningProcess,
                            children: (
                                <Typography.Paragraph
                                    type="secondary"
                                    style={{ whiteSpace: "pre-wrap", marginBottom: 0 }}
                                >
                                    {content.reasoningContent}
                                </Typography.Paragraph>
                            ),
                        },
                    ]}
                />
            </div>
        );
    };

    const renderArticleContextPreviewDrawer = () => {
        const drawerWidth = screens.lg ? 800 : screens.md ? 640 : "100%";
        const versionText =
            contextPreview?.version !== undefined
                ? `${getRes().articleEdit.assistant.articleContextVersionPrefix}${contextPreview.version}`
                : "";
        return (
            <Drawer
                title={contextPreview?.title || getRes().articleEdit.assistant.articleContextPreviewTitle}
                width={drawerWidth}
                open={Boolean(contextPreview)}
                onClose={() => {
                    setContextPreview(undefined);
                    setContextPreviewHtml("");
                }}
                styles={{
                    body: {
                        padding: 0,
                    },
                }}
            >
                {contextPreview && (
                    <ArticlePreviewSnapshot
                        htmlContent={contextPreviewHtml}
                        dark={getAppState().dark}
                        tagText={getRes().articleEdit.assistant.articleContextTag}
                        versionText={versionText}
                        digest={contextPreview.digest}
                        digestLabel={getRes().articleEdit.assistant.articleContextDigest}
                        keywords={contextPreview.keywords}
                        keywordsLabel={getRes().articleEdit.assistant.articleContextKeywords}
                        emptyDescription={getRes().articleEdit.assistant.articleContextEmpty}
                    />
                )}
            </Drawer>
        );
    };

    const renderMessage = ({ content, index, defaultNode }: AIButtonRenderMessageOptions) => {
        const toolAwareContent = content as ToolAwareAIContent;
        if (toolAwareContent.messageType === "articleContext") {
            return renderArticleContextMessage(toolAwareContent);
        }
        if (toolAwareContent.messageType === "error") {
            return renderAiErrorMessage(toolAwareContent);
        }
        const toolPayload = content.role === "assistant" ? getToolPayload(content, index) : undefined;
        const messageTool = content.role === "user" ? getMessageTool(content) : undefined;
        if (toolPayload) {
            return (
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
                    onApplyValues={onApplyValues}
                    onSelectTitle={(messageIndex, title) =>
                        setSelectedTitles((prevState) => ({
                            ...prevState,
                            [messageIndex]: title,
                        }))
                    }
                    onRefine={(prompt, tool) => void sendMessage(prompt, tool)}
                    onUpdateToolPayload={updateToolPayload}
                    onApplyGeneratedCover={onApplyGeneratedCover}
                    onCoverApplyingChange={setApplyingCoverMessageId}
                    onCropCover={(url) => {
                        setCroppingImageUrl(url);
                        setCropModalOpen(true);
                    }}
                />
            );
        }
        return (
            <>
                {renderReasoningProcess(toolAwareContent)}
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
            aiProvider={data.aiProvider}
            disabled={offline || Boolean(loadingKey)}
            loadingKey={loadingKey}
            aiMessageCount={aiMessages.length}
            aiMessagesExporting={aiMessagesExporting}
            aiMessagesClearing={aiMessagesClearing}
            includeArticleContextInChat={includeArticleContextInChat}
            toolFieldSelection={toolFieldSelection}
            theme={theme}
            selectedText={selectedText}
            requestPreview={buildRequestPreview(selectedText)}
            articleContextAvailable={hasArticleContextSource()}
            articleContextAdded={hasArticleContextMessage()}
            articleContextAppending={contextAppending}
            onAddArticleContext={() => void appendArticleContext()}
            onExportAiMessages={() => void exportAiMessages()}
            onClearAiMessages={() => void clearAiMessages()}
            onIncludeArticleContextInChatChange={setIncludeArticleContextInChat}
            onToolFieldSelectionChange={updateToolFieldSelection}
            onSubmit={(messageInput, tool) => void sendMessage(messageInput, tool, selectedText)}
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
            {renderArticleContextPreviewDrawer()}
        </>
    );

    return {
        messages: aiMessages,
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
    getContainer,
    aiDrawerWidth,
    stateCache,
    open,
    onOpenChange,
    onAiMessagesChange,
    onAiDrawerSizeChange,
    onApplyValues,
    onApplyGeneratedCover,
}) => {
    const [innerOpen, setInnerOpen] = useState(false);
    const { useBreakpoint } = Grid;
    const screens = useBreakpoint();
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
        onApplyValues,
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

    return (
        <AIButton
            aiProvider={aiConfigured ? data.aiProvider : undefined}
            dark={getAppState().dark}
            messages={assistantConfig.messages}
            user={getEditorUser()}
            subject={data.article.title}
            open={mergedOpen}
            drawerWidth={resolveDrawerWidth(aiDrawerWidth)}
            stateCache={stateCache ?? aiStateCache}
            configUrl={getRealRouteUrl("/website/ai")}
            getContainer={getContainer}
            contentMaxWidth={assistantConfig.contentMaxWidth}
            triggerClassName={"btn"}
            triggerStyle={{
                background: `linear-gradient(135deg, ${theme.colorInfo}, ${theme.colorPrimary})`,
                border: "none",
            }}
            triggerTitle={getShortcutTitle(getRes().websiteAi.label, AI_ASSISTANT_SHORTCUT)}
            triggerLabel={screens.sm ? <span>{getRes().websiteAi.label}</span> : undefined}
            onOpenChange={updateOpen}
            onSizeChange={(nextWidth: number) => {
                onAiDrawerSizeChange?.(nextWidth);
            }}
            renderMessage={assistantConfig.renderMessage}
            footer={assistantConfig.renderFooter()}
            overlays={assistantConfig.overlays}
        />
    );
};

export default ArticleAiAssistantButton;
