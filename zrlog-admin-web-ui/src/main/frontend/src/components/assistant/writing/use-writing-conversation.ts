import { useEffect, useRef, useState } from "react";
import type { AxiosInstance } from "axios";
import type { AIContent } from "@zrlog/editor/core/ai/AIContentItem";
import { getSsDate } from "../../../base/SsData";
import { getErrorMessage } from "../../../utils/error-message";
import { getRes } from "../../../utils/constants";
import type { ApiResponse } from "../../../type";

const preferenceKey = (articleId: number) => `${getSsDate().key}/assistant/writing/${articleId}`;
const readSelected = (articleId: number, draftConversationId?: string) => {
    const params = new URLSearchParams(window.location.search);
    const fromUrl = params.get("assistantSession");
    const value =
        fromUrl || (articleId === 0 ? draftConversationId : window.localStorage.getItem(preferenceKey(articleId)));
    return value && /^[a-f0-9-]{36}$/.test(value) ? value : undefined;
};

const loadConversation = async (api: AxiosInstance, id: number, sessionId?: string, signal?: AbortSignal) => {
    const config = { params: { id, sessionId }, signal, showError: false };
    const { data } = await api.get<ApiResponse<{ aiMessages: AIContent[] }>>(
        "/api/admin/assistant/chat/conversation",
        config
    );
    if (data.error) throw new Error(data.message || getRes().error.unknown);
    return data.data.aiMessages;
};

export default function useWritingConversation(
    api: AxiosInstance,
    articleId: number,
    open: boolean,
    initialMessages?: AIContent[],
    draftConversationId?: string
) {
    const [sessionId, setSessionId] = useState<string | undefined>(() => readSelected(articleId, draftConversationId));
    const [messages, setMessages] = useState<AIContent[]>(() =>
        !sessionId || sessionId === draftConversationId ? initialMessages || [] : []
    );
    const canonicalDraft = useRef(draftConversationId);
    const pendingDraft = useRef<Promise<{ sessionId?: string; messages: AIContent[] }>>();
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState<string>();
    const [retry, setRetry] = useState(0);
    const priorArticle = useRef(articleId);
    const loaded = useRef<string>();
    const apiRef = useRef(api);
    apiRef.current = api;
    const loadKey = `${articleId}/${sessionId || "default"}/${retry}`;
    const ensureDraft = () => {
        if (articleId > 0 || sessionId) return Promise.resolve({ sessionId, messages });
        if (!pendingDraft.current) {
            pendingDraft.current = (async () => {
                const { data } = await api.post<ApiResponse<{ conversationId: string }>>(
                    "/api/admin/assistant/chat/draft",
                    {}
                );
                if (data.error || !data.data?.conversationId)
                    throw new Error(data.message || getRes().error.unknown);
                const id = data.data.conversationId;
                const history = await loadConversation(api, 0, id);
                canonicalDraft.current = id;
                loaded.current = `0/${id}/${retry}`;
                setMessages(history);
                setSessionId(id);
                return { sessionId: id, messages: history };
            })().finally(() => {
                pendingDraft.current = undefined;
            });
        }
        return pendingDraft.current;
    };
    useEffect(() => {
        if (priorArticle.current !== articleId) {
            if (priorArticle.current === 0 && articleId > 0) {
                window.localStorage.setItem(preferenceKey(articleId), sessionId || "default");
                // The server moved the same conversation. Keep any in-flight turn in place.
                loaded.current = loadKey;
                canonicalDraft.current = undefined;
                setLoading(false);
                setError(undefined);
            } else {
                const selected = readSelected(articleId, draftConversationId);
                canonicalDraft.current = articleId === 0 ? draftConversationId : undefined;
                setSessionId(selected);
                setMessages(selected ? [] : initialMessages || []);
            }
            priorArticle.current = articleId;
        }
    }, [articleId, sessionId, loadKey, draftConversationId, initialMessages]);
    useEffect(() => {
        if (!open || loaded.current === loadKey) return;
        const controller = new AbortController();
        setLoading(true);
        setError(undefined);
        void loadConversation(apiRef.current, articleId, sessionId, controller.signal)
            .then((history) => {
                if (controller.signal.aborted) return;
                loaded.current = loadKey;
                setMessages(history);
            })
            .catch((error) => {
                if (!controller.signal.aborted) setError(getErrorMessage(error, getRes().error.unknown));
            })
            .finally(() => {
                if (!controller.signal.aborted) setLoading(false);
            });
        return () => controller.abort();
    }, [articleId, sessionId, open, loadKey]);
    return {
        sessionId,
        messages,
        loading,
        error,
        readOnly:
            articleId === 0 &&
            (sessionId ? sessionId !== canonicalDraft.current :
                new URLSearchParams(window.location.search).get("assistantSession") === "default"),
        ensureDraft,
        change: setMessages,
        retry: () => setRetry((value) => value + 1),
    };
}
