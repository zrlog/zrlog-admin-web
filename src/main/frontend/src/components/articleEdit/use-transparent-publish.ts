import { SetStateAction, useCallback } from "react";
import { MessageInstance } from "antd/es/message/interface";
import { AIContent } from "@editor/dist/ai/AIContentItem";
import { getRes } from "../../utils/constants";
import { getStaticProgressText, postRefreshCacheSse } from "../../utils/sse-utils";
import { ArticleEntry, PublishStatusPopoverState } from "./index.types";
import { ToolAwareAIContent } from "./article-ai-assistant/article-ai-assistant.types";

type UseTransparentPublishOptions = {
    messageApi: MessageInstance;
    onAiMessagesChange: (action: SetStateAction<AIContent[]>, articleId?: number) => void;
    updatePublishStatus: (
        action: PublishStatusPopoverState | ((previousState: PublishStatusPopoverState) => PublishStatusPopoverState)
    ) => void;
};

const appendUniqueAiMessages = (current: AIContent[], incoming: AIContent[]) => {
    const messageIds = new Set(
        current
            .map((message) => (message as ToolAwareAIContent).messageId)
            .filter((messageId): messageId is string => Boolean(messageId))
    );
    const additions = incoming.filter((message) => {
        const messageId = (message as ToolAwareAIContent).messageId;
        if (!messageId) {
            return true;
        }
        if (messageIds.has(messageId)) {
            return false;
        }
        messageIds.add(messageId);
        return true;
    });
    return additions.length === 0 ? current : [...current, ...additions];
};

const useTransparentPublish = ({ messageApi, onAiMessagesChange, updatePublishStatus }: UseTransparentPublishOptions) =>
    useCallback(
        async (uri: string, article: ArticleEntry) => {
            updatePublishStatus({
                open: true,
                visible: true,
                updatedAt: Date.now(),
                publishState: "running",
                publishText: getRes().staticSite.publishStart,
                publishError: undefined,
                publicUrl: undefined,
                staticStatus: "idle",
                staticText: undefined,
                staticError: undefined,
                checkStatus: "idle",
            });
            let refreshResponse: any;
            let articleResponse: any;
            const updatePostPublishFailure = (message: string) => {
                updatePublishStatus((previousState) => {
                    const staticStatusKnown =
                        previousState.staticStatus === "success" || previousState.staticStatus === "not-required";
                    return {
                        ...previousState,
                        visible: true,
                        updatedAt: Date.now(),
                        staticStatus: staticStatusKnown ? previousState.staticStatus : "failed",
                        staticText: undefined,
                        staticError: staticStatusKnown ? undefined : message,
                        checkStatus: previousState.checkStatus === "running" ? "idle" : previousState.checkStatus,
                    };
                });
            };
            try {
                const response = await postRefreshCacheSse(uri, {
                    body: getRes().supportSse === false ? { ...article, transparentPublish: false } : article,
                    messageApi,
                    messageKey: "transparentPublish",
                    responseEvents: ["article"],
                    requiredCompletionEvent: "publish-complete",
                    backgroundTaskTitle: getRes().backgroundTask.title + " · " + getRes().articleEdit.actions.release,
                    showErrorMessage: false,
                    onResponse: (data) => {
                        refreshResponse = data;
                    },
                    onBackgroundError: (error) => {
                        updatePostPublishFailure(error.message || getRes().staticSite.syncFailed);
                    },
                    onEvent: (event) => {
                        if (event.event === "article") {
                            articleResponse = event.data;
                            const previewUrl = event.data?.data?.article?.previewUrl;
                            updatePublishStatus((previousState) => ({
                                ...previousState,
                                visible: true,
                                updatedAt: Date.now(),
                                publishState: "success",
                                publishText: event.data?.message || getRes().staticSite.publishComplete,
                                publicUrl: typeof previewUrl === "string" ? previewUrl : undefined,
                            }));
                        }
                        if (event.event === "publish-start") {
                            updatePublishStatus((previousState) => ({
                                ...previousState,
                                visible: true,
                                updatedAt: Date.now(),
                                publishText: event.data?.message || getRes().staticSite.publishStart,
                            }));
                        }
                        if (event.event === "static-sync-start" || event.event === "static-progress") {
                            updatePublishStatus((previousState) => ({
                                ...previousState,
                                visible: true,
                                updatedAt: Date.now(),
                                staticStatus: "running",
                                staticText: getStaticProgressText(event.data),
                                staticError: undefined,
                            }));
                        }
                        if (event.event === "static-sync-complete") {
                            updatePublishStatus((previousState) => ({
                                ...previousState,
                                visible: true,
                                updatedAt: Date.now(),
                                staticStatus: "success",
                                staticText: undefined,
                                staticError: undefined,
                            }));
                        }
                        if (event.event === "static-sync-skipped") {
                            updatePublishStatus((previousState) => ({
                                ...previousState,
                                visible: true,
                                updatedAt: Date.now(),
                                staticStatus: "not-required",
                                staticText: undefined,
                                staticError: undefined,
                            }));
                        }
                        if (event.event === "static-error") {
                            updatePublishStatus((previousState) => ({
                                ...previousState,
                                visible: true,
                                updatedAt: Date.now(),
                                staticStatus: "failed",
                                staticText: undefined,
                                staticError: event.data?.message || getRes().staticSite.syncFailed,
                                checkStatus:
                                    previousState.checkStatus === "running" ? "idle" : previousState.checkStatus,
                            }));
                        }
                        if (event.event === "publish-complete") {
                            updatePublishStatus((previousState) => ({
                                ...previousState,
                                visible: true,
                                updatedAt: Date.now(),
                                publishState: "success",
                                publishText: event.data?.message || getRes().staticSite.publishComplete,
                            }));
                        }
                        if (event.event === "publish-check-start") {
                            updatePublishStatus((previousState) => ({
                                ...previousState,
                                visible: true,
                                updatedAt: Date.now(),
                                checkStatus: "running",
                            }));
                        }
                        if (event.event === "publish-check-complete") {
                            if (event.data?.aiMessages) {
                                onAiMessagesChange(
                                    (current) => appendUniqueAiMessages(current, event.data.aiMessages),
                                    articleResponse?.data?.article?.logId
                                );
                            }
                            updatePublishStatus((previousState) => ({
                                ...previousState,
                                visible: true,
                                updatedAt: Date.now(),
                                checkStatus: "success",
                                checkPayload: event.data?.toolPayload,
                            }));
                        }
                        if (event.event === "publish-check-error") {
                            updatePublishStatus((previousState) => ({
                                ...previousState,
                                visible: true,
                                updatedAt: Date.now(),
                                checkStatus: "error",
                                checkError: event.data?.message || getRes().articleEdit.publishCheck.failed,
                            }));
                        }
                        if (event.event === "publish-error") {
                            const message = event.data?.message || getRes().articleEdit.saveFailed;
                            if (articleResponse) {
                                updatePostPublishFailure(message);
                            } else {
                                updatePublishStatus((previousState) => ({
                                    ...previousState,
                                    open: true,
                                    visible: true,
                                    updatedAt: Date.now(),
                                    publishState: "failed",
                                    publishError: message,
                                }));
                            }
                        }
                    },
                });
                const result = articleResponse || refreshResponse || response;
                if (!articleResponse && result?.error === 0) {
                    const previewUrl = result.data?.article?.previewUrl;
                    updatePublishStatus((previousState) => ({
                        ...previousState,
                        visible: true,
                        updatedAt: Date.now(),
                        publishState: "success",
                        publishText: result.message || getRes().staticSite.publishComplete,
                        publicUrl: typeof previewUrl === "string" ? previewUrl : undefined,
                    }));
                } else if (!articleResponse && result?.error) {
                    updatePublishStatus((previousState) => ({
                        ...previousState,
                        open: true,
                        visible: true,
                        updatedAt: Date.now(),
                        publishState: "failed",
                        publishError: result.message || getRes().articleEdit.saveFailed,
                    }));
                }
                return result;
            } catch (error) {
                if (!articleResponse) {
                    updatePublishStatus((previousState) => ({
                        ...previousState,
                        open: true,
                        visible: true,
                        updatedAt: Date.now(),
                        publishState: "failed",
                        publishError: error instanceof Error ? error.message : getRes().articleEdit.saveFailed,
                    }));
                }
                throw error;
            }
        },
        [messageApi, onAiMessagesChange, updatePublishStatus]
    );

export default useTransparentPublish;
