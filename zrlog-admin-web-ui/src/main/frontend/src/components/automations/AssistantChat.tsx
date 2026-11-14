import AssistantConversationActions from "./AssistantConversationActions";
import { Alert, Button, Space, Spin } from "antd";
import { useEffect, useMemo, useRef, useState } from "react";
import type { ReactNode } from "react";
import { Link } from "react-router-dom";
import { useAxiosBaseInstance } from "../../base/AppBase";
import { getRealRouteUrl, getRes } from "../../utils/constants";
import { getErrorMessage } from "../../utils/error-message";
import { hasAction } from "../../utils/account-access";
import { createDraftAiSaveGate } from "../articleEdit/draft-ai-save-gate";
import type { ArticleEditInfo, ArticleEditState } from "../articleEdit/index.types";
import type { ApiResponse } from "../../type";
import { useArticleAiAssistantConfig } from "../articleEdit/article-ai-assistant/article-ai-assistant-button";
import AssistantConversationView from "../articleEdit/article-ai-assistant/assistant-conversation-view";
import type { ConversationActivity } from "../articleEdit/article-ai-assistant/assistant-conversation-view";

type ConversationInfo = Pick<ArticleEditInfo, "aiProvider" | "aiModel" | "aiConfigured" | "aiMessages" | "aiFileInput">;

function Conversation({
    info,
    taskId,
    offline,
    leading,
    activities,
}: {
    info: ConversationInfo;
    taskId?: string;
    offline: boolean;
    leading?: ReactNode;
    activities?: ConversationActivity[];
}) {
    const api = useAxiosBaseInstance();
    const [messages, setMessages] = useState(info.aiMessages);
    const gate = useMemo(createDraftAiSaveGate, []);
    const scrollRef = useRef<HTMLDivElement>(null);
    const followMessages = useRef(true);
    // General and task conversations never restore the article editor's local draft.
    const data: ArticleEditState = {
        ...info,
        aiConfigured: info.aiConfigured === true,
        aiMessages: messages,
        article: { title: "", version: 0, rubbish: true },
        tags: [],
        typeOptions: [],
        rubbish: true,
        editorVersion: 0,
        contentSource: "server",
        linkPreviewEnabled: false,
        publishCheckEnabled: false,
        articleCoverAspectRatio: "16:9",
        articleEditAutoSaveInterval: 5,
        saving: { rubbishSaving: false, previewIng: false, autoSaving: false, releaseSaving: false },
    };
    const config = useArticleAiAssistantConfig({
        context: { taskId },
        data,
        draftAiSaveGate: gate,
        offline,
        axiosInstance: api,
        onAiMessagesChange: setMessages,
        onApplyValues: () => undefined,
        getSkillContextRevision: () => "",
    });
    useEffect(() => {
        if (followMessages.current && scrollRef.current) scrollRef.current.scrollTop = scrollRef.current.scrollHeight;
    }, [config.messages]);
    const actions = config.conversationActions;
    return (
        <div style={{ display: "flex", flexDirection: "column", height: "100%", minHeight: 0 }}>
            <Space style={{ alignSelf: "flex-end" }}>
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
                    leading={leading}
                    activities={activities}
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
    offline,
    leading,
    activities,
}: {
    taskId?: string;
    offline: boolean;
    leading?: ReactNode;
    activities?: ConversationActivity[];
}) {
    const api = useAxiosBaseInstance();
    const [info, setInfo] = useState<ConversationInfo>();
    const [error, setError] = useState<string>();
    const [revision, setRevision] = useState(0);
    useEffect(() => {
        const controller = new AbortController();
        setInfo(undefined);
        setError(undefined);
        if (offline) return;
        void api
            .get<ApiResponse<ConversationInfo>>("/api/admin/assistant/chat/conversation", {
                params: { taskId },
                signal: controller.signal,
            })
            .then(({ data }) => {
                if (controller.signal.aborted) return;
                if (data.error) throw new Error(data.message || getRes().error.unknown);
                setInfo(data.data);
            })
            .catch((error) => {
                if (!controller.signal.aborted) setError(getErrorMessage(error, getRes().error.unknown));
            });
        return () => controller.abort();
    }, [api, taskId, offline, revision]);
    if (error)
        return (
            <Alert
                type="error"
                title={error}
                action={<Button onClick={() => setRevision((value) => value + 1)}>{getRes().common.retry}</Button>}
            />
        );
    if (!info)
        return offline ? (
            <>
                {leading}
                <Alert type="info" title={getRes().assistant.offline} />
            </>
        ) : (
            <Spin />
        );
    return (
        <Conversation
            key={taskId || "general"}
            info={info}
            taskId={taskId}
            offline={offline}
            leading={leading}
            activities={activities}
        />
    );
}
