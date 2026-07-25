import { useCallback } from "react";
import { MessageInstance } from "antd/es/message/interface";
import { AIContent } from "@editor/dist/ai/AIContentItem";
import { getRes } from "../../utils/constants";
import { getStaticProgressText, postRefreshCacheSse } from "../../utils/sse-utils";
import { ArticleEntry, PublishStatusPopoverState } from "./index.types";

type UseTransparentPublishOptions = {
    aiMessages: AIContent[];
    messageApi: MessageInstance;
    onAiMessagesChange: (messages: AIContent[]) => void;
    updatePublishStatus: (
        action: PublishStatusPopoverState | ((previousState: PublishStatusPopoverState) => PublishStatusPopoverState)
    ) => void;
};

const useTransparentPublish = ({
    aiMessages,
    messageApi,
    onAiMessagesChange,
    updatePublishStatus,
}: UseTransparentPublishOptions) =>
    useCallback(
        async (uri: string, article: ArticleEntry) => {
            updatePublishStatus({
                open: true,
                visible: true,
                updatedAt: Date.now(),
                publishText: getRes().staticSite.publishStart,
                publishError: undefined,
                checkStatus: "idle",
            });
            let refreshResponse: any;
            let articleResponse: any;
            await postRefreshCacheSse(uri, {
                body: article,
                messageApi,
                messageKey: "transparentPublish",
                responseEvents: ["article"],
                backgroundTaskTitle: getRes().backgroundTask.title + " · " + getRes().articleEdit.actions.release,
                showErrorMessage: false,
                onResponse: (data) => {
                    refreshResponse = data;
                },
                onEvent: (event) => {
                    if (event.event === "article") {
                        articleResponse = event.data;
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
                            staticText: getStaticProgressText(event.data),
                        }));
                    }
                    if (event.event === "static-sync-complete") {
                        updatePublishStatus((previousState) => ({
                            ...previousState,
                            visible: true,
                            updatedAt: Date.now(),
                            staticText: undefined,
                        }));
                    }
                    if (event.event === "publish-complete") {
                        updatePublishStatus((previousState) => ({
                            ...previousState,
                            visible: true,
                            updatedAt: Date.now(),
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
                            onAiMessagesChange([...aiMessages, ...event.data.aiMessages]);
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
                        updatePublishStatus((previousState) => ({
                            ...previousState,
                            open: true,
                            visible: true,
                            updatedAt: Date.now(),
                            publishError: event.data?.message || getRes().articleEdit.saveFailed,
                        }));
                    }
                },
            });
            return articleResponse || refreshResponse;
        },
        [aiMessages, messageApi, onAiMessagesChange, updatePublishStatus]
    );

export default useTransparentPublish;
