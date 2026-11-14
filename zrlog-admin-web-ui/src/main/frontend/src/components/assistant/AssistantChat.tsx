import { getBackgroundTasks, subscribeBackgroundTasks } from "../../utils/background-task-store";
import { ASSISTANT_NOTICE_PREFIX } from "../../utils/message-center-notice-sync";
import AssistantConversationActions from "./AssistantConversationActions";
import { Alert, Button, Space, Spin, Typography } from "antd";
import { useCallback, useEffect, useMemo, useRef, useState, useSyncExternalStore } from "react";
import type { ReactNode } from "react";
import { Link } from "react-router-dom";
import { useAxiosBaseInstance } from "../../base/AppBase";
import { getRealRouteUrl, getRes } from "../../utils/constants";
import { getErrorMessage } from "../../utils/error-message";
import { hasAction } from "../../utils/account-access";
import { createDraftAiSaveGate } from "../articleEdit/draft-ai-save-gate";
import type { ArticleEditInfo } from "../articleEdit/index.types";
import type { ApiResponse } from "../../type";
import { useAssistantConversation, AssistantConversationData } from "./use-assistant-conversation";
import AssistantConversationView from "./chat/assistant-conversation-view";
import { mergeTaskFeedback } from "./tasks/task-feedback-messages";

type ConversationInfo = Pick<
    ArticleEditInfo,
    "aiProvider" | "aiModel" | "aiConfigured" | "aiMessages" | "aiFileInput"
> & {
    hasEarlierFeedback?: boolean;
};

function Conversation({
    info,
    taskId,
    sessionId,
    toolbar,
    onBusyChange,
    onSaved,
    offline,
    leading,
    onEarlier,
    loadingEarlier,
    error,
    onRetry,
}: {
    info: ConversationInfo;
    taskId?: string;
    sessionId?: string;
    toolbar?: ReactNode;
    onBusyChange?: (busy: boolean) => void;
    onSaved?: () => void;
    offline: boolean;
    leading?: ReactNode;
    onEarlier: () => void;
    loadingEarlier: boolean;
    error?: string;
    onRetry: () => void;
}) {
    const api = useAxiosBaseInstance();
    const [messages, setMessages] = useState(info.aiMessages);
    useEffect(() => {
        if (!taskId) return;
        setMessages((current) => mergeTaskFeedback(current, info.aiMessages));
    }, [info, taskId]);
    const gate = useMemo(createDraftAiSaveGate, []);
    const scrollRef = useRef<HTMLDivElement>(null);
    const followMessages = useRef(true);
    // General and task conversations never restore the article editor's local draft.
    const data: AssistantConversationData = {
        ...info,
        aiConfigured: info.aiConfigured === true,
        aiMessages: messages,
        article: { title: "", version: 0, rubbish: true },
        articleCoverAspectRatio: "16:9",
    };
    const config = useAssistantConversation({
        context: { taskId, sessionId },
        data,
        draftAiSaveGate: gate,
        offline,
        axiosInstance: api,
        onAiMessagesChange: (next) =>
            setMessages((current) => (taskId && next.length ? mergeTaskFeedback(next, current) : next)),
        onApplyValues: () => undefined,
        getSkillContextRevision: () => "",
    });
    useEffect(() => {
        if (followMessages.current && scrollRef.current) scrollRef.current.scrollTop = scrollRef.current.scrollHeight;
    }, [config.messages]);
    useEffect(() => {
        onBusyChange?.(config.navigationBusy);
        return () => onBusyChange?.(false);
    }, [config.navigationBusy, onBusyChange]);
    const lastMessageId = (messages?.[messages.length - 1] as { messageId?: string } | undefined)?.messageId;
    const previousSaved = useRef(lastMessageId);
    useEffect(() => {
        if (lastMessageId && previousSaved.current !== lastMessageId) onSaved?.();
        previousSaved.current = lastMessageId;
    }, [lastMessageId, onSaved]);
    const actions = config.conversationActions;
    return (
        <div style={{ display: "flex", flexDirection: "column", height: "100%", minHeight: 0 }}>
            {error && (
                <Alert type="error" title={error} action={<Button onClick={onRetry}>{getRes().common.retry}</Button>} />
            )}
            <Space style={{ justifyContent: "space-between", padding: 8 }}>
                {toolbar}
                <AssistantConversationActions actions={actions} />
            </Space>
            {info.aiConfigured !== true && (
                <Alert
                    type="info"
                    title={getRes().assistant.configureAi}
                    action={
                        hasAction("site.configure") ? (
                            <Link to={getRealRouteUrl("/website/ai")}>{getRes().websiteAi.title}</Link>
                        ) : undefined
                    }
                />
            )}
            <div style={{ flex: 1, minHeight: 0 }}>
                <AssistantConversationView
                    config={config}
                    aiProvider={data.aiProvider}
                    leading={
                        <>
                            {leading}
                            {info.hasEarlierFeedback && (
                                <Button
                                    type="text"
                                    loading={loadingEarlier}
                                    disabled={offline}
                                    onClick={() => {
                                        followMessages.current = false;
                                        onEarlier();
                                    }}
                                >
                                    {getRes().assistant.earlierFeedback}
                                </Button>
                            )}
                            {taskId && messages?.length === 0 && (
                                <Typography.Paragraph type="secondary">
                                    {getRes().assistant.feedbackEmpty}
                                </Typography.Paragraph>
                            )}
                        </>
                    }
                    scrollRef={scrollRef}
                    onScroll={() => {
                        const scroll = scrollRef.current;
                        if (scroll)
                            followMessages.current = scroll.scrollHeight - scroll.scrollTop - scroll.clientHeight < 48;
                    }}
                />
            </div>
        </div>
    );
}

export default function AssistantChat({
    taskId,
    sessionId,
    toolbar,
    onBusyChange,
    onSaved,
    offline,
    leading,
    revision = 0,
}: {
    taskId?: string;
    sessionId?: string;
    toolbar?: ReactNode;
    onBusyChange?: (busy: boolean) => void;
    onSaved?: () => void;
    offline: boolean;
    leading?: ReactNode;
    revision?: number;
}) {
    const api = useAxiosBaseInstance();
    const onSavedRef = useRef(onSaved);
    onSavedRef.current = onSaved;
    const [info, setInfo] = useState<ConversationInfo>();
    const [error, setError] = useState<string>();
    const [retry, setRetry] = useState(0);
    const noticeRevision = useSyncExternalStore(
        subscribeBackgroundTasks,
        useCallback(
            () =>
                taskId
                    ? getBackgroundTasks()
                          .filter((task) => task.taskKey?.startsWith(ASSISTANT_NOTICE_PREFIX))
                          .map((task) => `${task.taskKey}:${task.updatedAt}:${task.status}`)
                          .sort()
                          .join("|")
                    : "",
            [taskId]
        ),
        () => ""
    );
    useEffect(() => {
        if (!taskId || offline) return;
        const resume = () => {
            if (document.visibilityState === "visible") setRetry((value) => value + 1);
        };
        document.addEventListener("visibilitychange", resume);
        return () => document.removeEventListener("visibilitychange", resume);
    }, [taskId, offline]);
    const [loadingEarlier, setLoadingEarlier] = useState(false);
    const loadEarlier = async () => {
        if (loadingEarlier || offline) return;
        setLoadingEarlier(true);
        try {
            const requestConfig = { params: { taskId, sessionId, earlier: 1 }, showError: false };
            const { data } = await api.get<ApiResponse<ConversationInfo>>(
                "/api/admin/assistant/chat/conversation",
                requestConfig
            );
            if (data.error) throw new Error(data.message || getRes().error.unknown);
            setInfo(data.data);
        } catch (error) {
            setError(getErrorMessage(error, getRes().error.unknown));
        } finally {
            setLoadingEarlier(false);
        }
    };
    useEffect(() => {
        const controller = new AbortController();
        setError(undefined);
        if (offline) return;
        const requestConfig = { params: { taskId, sessionId }, signal: controller.signal, showError: false };
        void api
            .get<ApiResponse<ConversationInfo>>("/api/admin/assistant/chat/conversation", requestConfig)
            .then(({ data }) => {
                if (controller.signal.aborted) return;
                if (data.error) throw new Error(data.message || getRes().error.unknown);
                setInfo(data.data);
                // Reading a legacy/task conversation may have just added its directory entry.
                if (data.data.aiMessages.length) onSavedRef.current?.();
            })
            .catch((error) => {
                if (!controller.signal.aborted) setError(getErrorMessage(error, getRes().error.unknown));
            });
        return () => controller.abort();
    }, [api, taskId, sessionId, offline, revision, retry, noticeRevision]);
    if (!info)
        return (
            <div style={{ height: "100%", display: "flex", flexDirection: "column" }}>
                <div style={{ padding: 8 }}>{toolbar}</div>
                {error ? (
                    <Alert
                        type="error"
                        title={error}
                        action={<Button onClick={() => setRetry((value) => value + 1)}>{getRes().common.retry}</Button>}
                    />
                ) : offline ? (
                    <>
                        {leading}
                        <Alert type="info" title={getRes().assistant.offline} />
                    </>
                ) : (
                    <Spin />
                )}
            </div>
        );
    return (
        <Conversation
            key={sessionId || taskId || "general"}
            info={info}
            taskId={taskId}
            sessionId={sessionId}
            toolbar={toolbar}
            onBusyChange={onBusyChange}
            onSaved={onSaved}
            offline={offline}
            leading={leading}
            onEarlier={() => void loadEarlier()}
            loadingEarlier={loadingEarlier}
            error={error}
            onRetry={() => setRetry((value) => value + 1)}
        />
    );
}
