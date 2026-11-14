import { useEffect, useRef, useState } from "react";
import type { AxiosInstance } from "axios";
import type { AIContent } from "@zrlog/editor/core/ai/AIContentItem";
import { getSsDate } from "../../../base/SsData";
import { getErrorMessage } from "../../../utils/error-message";
import { getRes } from "../../../utils/constants";
import type { ApiResponse } from "../../../type";

const preferenceKey = (articleId: number) => `${getSsDate().key}/assistant/writing/${articleId}`;
const readSelected = (articleId: number) => {
    const params = new URLSearchParams(window.location.search);
    const fromUrl = params.get("assistantSession");
    const value = fromUrl || window.localStorage.getItem(preferenceKey(articleId));
    return value && /^[a-f0-9-]{36}$/.test(value) ? value : undefined;
};

export default function useWritingConversation(
    api: AxiosInstance,
    articleId: number,
    open: boolean,
    initialMessages?: AIContent[]
) {
    const [sessionId, setSessionId] = useState<string | undefined>(() => readSelected(articleId));
    const [messages, setMessages] = useState<AIContent[]>(sessionId ? [] : initialMessages || []);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState<string>();
    const [retry, setRetry] = useState(0);
    const priorArticle = useRef(articleId);
    const loaded = useRef<string>();
    const loadKey = `${articleId}/${sessionId || "default"}/${retry}`;
    const selectedRef = useRef(sessionId);
    selectedRef.current = sessionId;
    useEffect(() => {
        if (priorArticle.current !== articleId) {
            if (priorArticle.current === 0 && articleId > 0) {
                window.localStorage.setItem(preferenceKey(articleId), sessionId || "default");
                window.localStorage.removeItem(preferenceKey(0));
                // The server moved the same conversation. Keep any in-flight turn in place.
                loaded.current = loadKey;
            } else {
                const selected = readSelected(articleId);
                setSessionId(selected);
                setMessages([]);
            }
            priorArticle.current = articleId;
        }
    }, [articleId, sessionId, loadKey]);
    useEffect(() => {
        if (!open || loaded.current === loadKey) return;
        const controller = new AbortController();
        setLoading(true);
        setError(undefined);
        const config = { params: { id: articleId, sessionId }, signal: controller.signal, showError: false };
        void api
            .get<ApiResponse<{ aiMessages: AIContent[] }>>("/api/admin/assistant/chat/conversation", config)
            .then(({ data }) => {
                if (controller.signal.aborted) return;
                if (data.error) throw new Error(data.message || getRes().error.unknown);
                loaded.current = loadKey;
                setMessages(data.data.aiMessages);
            })
            .catch((error) => {
                if (!controller.signal.aborted) setError(getErrorMessage(error, getRes().error.unknown));
            })
            .finally(() => {
                if (!controller.signal.aborted) setLoading(false);
            });
        return () => controller.abort();
    }, [api, articleId, sessionId, open, loadKey]);
    const change = (next: AIContent[]) => {
        if (selectedRef.current !== sessionId) return;
        setMessages(next);
    };
    return {
        sessionId,
        messages,
        loading,
        error,
        change,
        retry: () => setRetry((value) => value + 1),
    };
}
