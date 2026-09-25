import { useEffect, useRef, useState } from "react";
import { Alert, Space, Typography } from "antd";
import { AxiosInstance, AxiosRequestConfig } from "axios";
import { AIContent } from "@editor/dist/ai/AIContentItem";
import { AIButtonRenderMessageOptions } from "@editor/dist/ai/AIButton";
import ArticleAiReasoning from "./article-ai-reasoning";
import { getRes } from "../../../utils/constants";

type Source = { id: number; title: string; url: string; draft: boolean; privateArticle: boolean };
export type KnowledgeMessage = AIContent & {
    messageType?: string;
    messageId?: string;
    sources?: Source[];
    reasoningContent?: string;
    failed?: boolean;
};
type Event = {
    type: string;
    reasoningContent?: string;
    content?: string;
    tool?: string;
    sources?: Source[];
    error?: string;
    messages?: KnowledgeMessage[];
};

export const parseKnowledgeEvents = (text: string): Event[] =>
    text
        .split("\n\n")
        .slice(0, -1)
        .flatMap((frame) => {
            const data = frame
                .split("\n")
                .filter((line) => line.startsWith("data: "))
                .map((line) => line.slice(6))
                .join("\n");
            try {
                return data ? [JSON.parse(data) as Event] : [];
            } catch {
                return [];
            }
        });

export const isKnowledgeMessage = (message: AIContent): message is KnowledgeMessage =>
    (message as KnowledgeMessage).messageType === "knowledge";

export const renderKnowledgeMessage = ({ content, defaultNode }: AIButtonRenderMessageOptions) => {
    const message = content as KnowledgeMessage;
    const res = getRes().articleEdit.knowledge;
    return (
        <>
            {!message.failed && message.role === "assistant" && (
                <ArticleAiReasoning content={message.reasoningContent} thinking={message.thinking} />
            )}
            {message.failed ? <Alert type="error" title={content.content} /> : defaultNode}
            {!!message.sources?.length && (
                <Space orientation="vertical" style={{ width: "100%" }}>
                    <Typography.Text type="secondary">{res.sources}</Typography.Text>
                    {message.sources.map((source) => (
                        <Typography.Link
                            key={source.id}
                            href={/^https?:\/\//.test(source.url) ? source.url : undefined}
                            target="_blank"
                            rel="noopener noreferrer"
                        >
                            {source.title} {(source.draft || source.privateArticle) && `(${res.restricted})`}
                        </Typography.Link>
                    ))}
                </Space>
            )}
        </>
    );
};

export const useKnowledgeAssistant = (
    api: AxiosInstance,
    disabled: boolean,
    scope: string,
    onMessagesChange?: (messages: AIContent[], articleId?: number) => void
) => {
    const [messages, setMessages] = useState<KnowledgeMessage[]>([]);
    const [busy, setBusy] = useState(false);
    const [status, setStatus] = useState("");
    const pending = useRef<AbortController>();
    const generation = useRef(0);
    const restorePending = useRef<() => void>();
    const stop = () => {
        generation.current++;
        pending.current?.abort();
        pending.current = undefined;
        restorePending.current?.();
        restorePending.current = undefined;
        setBusy(false);
        setStatus("");
    };
    const clear = () => {
        stop();
        setMessages([]);
    };
    useEffect(() => {
        setMessages([]);
        setBusy(false);
        setStatus("");
        return () => {
            generation.current++;
            pending.current?.abort();
            pending.current = undefined;
            restorePending.current?.();
            restorePending.current = undefined;
        };
    }, [scope]);

    const send = async (input: string, context: AIContent[], articleId: number, includeArticleContext = true) => {
        const prompt = input.trim();
        if (!prompt || disabled || pending.current) return;
        const res = getRes().articleEdit.knowledge;
        const controller = new AbortController();
        pending.current = controller;
        const run = ++generation.current;
        setBusy(true);
        setStatus(res.thinking);
        const publish = (next: KnowledgeMessage[]) => {
            setMessages(next);
            onMessagesChange?.(next, articleId);
        };
        restorePending.current = () => publish(context);
        const question: KnowledgeMessage = { role: "user", content: prompt, thinking: false, messageType: "knowledge" };
        const base: KnowledgeMessage[] = [...context, question];
        publish([...base, { role: "assistant", content: "", thinking: true, messageType: "knowledge" }]);
        let lastReasoning = "";
        let lastContent = "";
        const consume = (text: string, final: boolean) => {
            if (run !== generation.current) return;
            const events = parseKnowledgeEvents(text);
            const error = events.find((e) => e.type === "error");
            if (error)
                throw new Error(
                    error.error === "permission"
                        ? res.permission
                        : error.error === "saveFailed"
                        ? res.saveFailed
                        : res.requestFailed
                );
            const progress = [...events].reverse().find((e) => e.type === "tool" || e.type === "thinking");
            if (progress)
                setStatus(
                    progress.tool === "search_articles"
                        ? res.searching
                        : progress.tool === "read_article"
                        ? res.reading
                        : res.thinking
                );
            const completedReasoning: string[] = [];
            let partialReasoning = "";
            let content = "";
            for (const event of events) {
                if (event.type === "thinking") content = "";
                if (event.type === "delta") content += event.content || "";
                if (event.type === "reasoning_delta") partialReasoning += event.reasoningContent || "";
                if (event.type === "reasoning" && event.reasoningContent) {
                    completedReasoning.push(event.reasoningContent);
                    partialReasoning = "";
                }
            }
            const reasoningContent = [...completedReasoning, partialReasoning].filter(Boolean).join("\n\n");
            if (!final && (reasoningContent !== lastReasoning || content !== lastContent)) {
                lastReasoning = reasoningContent;
                lastContent = content;
                publish([
                    ...base,
                    { role: "assistant", content, thinking: !content, messageType: "knowledge", reasoningContent },
                ]);
            }
            const answer = events.find((e) => e.type === "answer");
            if (final && (!answer || !events.some((e) => e.type === "done"))) throw new Error(res.requestFailed);
            if (answer && final) {
                if (
                    !answer.messages ||
                    answer.messages.length !== 2 ||
                    answer.messages.some((entry) => !entry.messageId)
                )
                    throw new Error(res.saveFailed);
                publish([...context, ...answer.messages.map((entry) => ({ ...entry, thinking: false }))]);
            }
        };
        try {
            const requestConfig: AxiosRequestConfig & { showError: boolean } = {
                showError: false,
                signal: controller.signal,
                adapter: "xhr",
                responseType: "text",
                headers: { accept: "text/event-stream" },
                onDownloadProgress: (progress) => {
                    const xhr = progress.event?.target as XMLHttpRequest | undefined;
                    try {
                        consume(xhr?.responseText || "", false);
                    } catch {
                        /* Final response owns error rendering. */
                    }
                },
            };
            const response = await api.post(
                "/api/admin/knowledge/chat",
                { input: prompt, articleId, includeArticleContext },
                requestConfig
            );
            consume(typeof response.data === "string" ? response.data : "", true);
        } catch (error) {
            if (run === generation.current)
                publish([
                    ...base,
                    {
                        role: "assistant",
                        content:
                            error instanceof Error &&
                            [res.permission, res.requestFailed, res.saveFailed].includes(error.message)
                                ? error.message
                                : res.requestFailed,
                        thinking: false,
                        failed: true,
                        messageType: "knowledge",
                    },
                ]);
        } finally {
            if (run === generation.current) {
                pending.current = undefined;
                restorePending.current = undefined;
                setBusy(false);
                setStatus("");
            }
        }
    };
    return { messages, busy, status, send, clear, stop };
};
