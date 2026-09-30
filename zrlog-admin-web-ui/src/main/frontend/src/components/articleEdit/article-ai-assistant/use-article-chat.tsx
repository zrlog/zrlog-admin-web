import { useCallback, useEffect, useRef, useState } from "react";
import { Alert, Space, Typography } from "antd";
import { AxiosInstance, AxiosRequestConfig } from "axios";
import { AIContent } from "@zrlog/editor/dist/ai/AIContentItem";
import { AIButtonRenderMessageOptions } from "@zrlog/editor/dist/ai/AIButton";
import ArticleAiReasoning from "./article-ai-reasoning";
import { getRes } from "../../../utils/constants";
import { ArticleUpdatedEvent } from "./article-ai-assistant.types";

type Source = { id: number; title: string; url: string; draft: boolean; privateArticle: boolean };
export type ChatMessage = AIContent & {
    messageType?: string;
    messageId?: string;
    sources?: Source[];
    reasoningContent?: string;
    failed?: boolean;
    runId?: string;
    run?: ChatRun;
};
export type ChatRun = {
    runId: string;
    articleId: number;
    input: string;
    status:
        | "awaiting_approval"
        | "running"
        | "executing"
        | "completed"
        | "failed"
        | "uncertain"
        | "expired"
        | "cancelled";
    error?: string;
    approval?: {
        id: string;
        tool: string;
        title?: string;
        articleId?: number;
        version?: number;
        publicImpact: boolean;
        expiresAt: number;
        changes: { field: string; before: string; after: string; truncated: boolean }[];
    };
    answer?: Event;
    articleUpdates?: ArticleUpdatedEvent[];
};

type Event = {
    runId?: string;
    run?: ChatRun;
    type: string;
    reasoningContent?: string;
    content?: string;
    tool?: string;
    sources?: Source[];
    error?: string;
    messages?: ChatMessage[];
    articleId?: number;
    version?: number;
    created?: boolean;
};

const activeRun = (view: ChatRun) => ["awaiting_approval", "running", "executing"].includes(view.status);
const createdArticleUpdate = (updates: ArticleUpdatedEvent[]) => {
    const valid = updates.filter(
        (update) =>
            Number.isInteger(update.articleId) &&
            update.articleId > 0 &&
            Number.isInteger(update.version) &&
            update.version >= 0
    );
    const created = valid.find((update) => update.created);
    if (!created) return undefined;
    return valid
        .filter((update) => update.articleId === created.articleId)
        .reduce((latest, update) => (update.version > latest.version ? { ...update, created: true } : latest), created);
};

export const parseChatEvents = (text: string): Event[] =>
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

export const isChatMessage = (message: AIContent): message is ChatMessage =>
    (message as ChatMessage).messageType === "knowledge";

export const renderChatMessage = ({ content, defaultNode }: AIButtonRenderMessageOptions) => {
    const message = content as ChatMessage;
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

export const useArticleChat = (
    api: AxiosInstance,
    disabled: boolean,
    scope: string,
    onMessagesChange?: (messages: AIContent[], articleId?: number) => void,
    onArticleUpdated?: (event: ArticleUpdatedEvent) => void,
    session?: { articleId: number; messages: AIContent[] }
) => {
    const [messages, setMessages] = useState<ChatMessage[]>([]);
    const [busy, setBusy] = useState(false);
    const [status, setStatus] = useState("");
    const pending = useRef<AbortController>();
    const generation = useRef(0);
    const restorePending = useRef<() => void>();
    const onArticleUpdatedRef = useRef(onArticleUpdated);
    onArticleUpdatedRef.current = onArticleUpdated;
    const contextRef = useRef({ api, session, onMessagesChange });
    contextRef.current = { api, session, onMessagesChange };
    const mergeRun = useCallback((view: ChatRun, context: AIContent[]) => {
        const current = context as ChatMessage[];
        const belongsToRun = (message: ChatMessage) =>
            message.runId === view.runId || message.messageId?.startsWith(`${view.runId}:`);
        let index = current.findIndex(belongsToRun);
        // A connection can fail before run-start arrives. Replace only an unfinished tail with the same prompt.
        if (index < 0 && current.length >= 2) {
            const question = current[current.length - 2],
                reply = current[current.length - 1];
            if (
                question.role === "user" &&
                question.content === view.input &&
                !question.messageId &&
                !question.runId &&
                reply.role === "assistant" &&
                !reply.messageId &&
                (reply.thinking || reply.failed)
            )
                index = current.length - 2;
        }
        const history = current.filter(
            (message, position) =>
                !belongsToRun(message) &&
                !(
                    index === current.length - 2 &&
                    !current[index]?.runId &&
                    !current[index]?.messageId &&
                    position >= index
                )
        );
        const restored: ChatMessage[] =
            view.status === "cancelled"
                ? []
                : view.status === "completed" && view.answer?.messages
                ? view.answer.messages.map((message) => ({ ...message, thinking: false }))
                : [
                      {
                          role: "user",
                          content: view.input,
                          messageType: "knowledge",
                          runId: view.runId,
                          thinking: false,
                      },
                      {
                          role: "assistant",
                          content: "",
                          messageType: "knowledge",
                          runId: view.runId,
                          run: view,
                          thinking: false,
                      },
                  ];
        const next = [...history];
        next.splice(index < 0 ? history.length : index, 0, ...restored);
        setMessages(next);
        contextRef.current.onMessagesChange?.(next, view.articleId);
    }, []);
    const refreshRun = useCallback(
        async (articleId = contextRef.current.session?.articleId) => {
            if (articleId === undefined) return false;
            const revision = generation.current;
            try {
                const response = await contextRef.current.api.get<{ error: number; data?: ChatRun }>(
                    "/api/admin/article/ai/run",
                    { params: { id: articleId } }
                );
                if (revision !== generation.current || response?.data?.error || !response?.data?.data) return false;
                const view = response.data.data;
                if (view.articleId !== articleId) return false;
                const created = articleId === 0 ? createdArticleUpdate(view.articleUpdates || []) : undefined;
                // A completed draft run has moved its conversation to the created article. A fresh
                // new-article page must not reopen it; an interrupted local turn can still recover it.
                if (
                    created &&
                    !activeRun(view) &&
                    !(contextRef.current.session?.messages as ChatMessage[] | undefined)?.some(
                        (message) => message.runId === view.runId || message.messageId?.startsWith(`${view.runId}:`)
                    )
                )
                    return false;
                mergeRun(view, contextRef.current.session?.messages || []);
                if (created) {
                    if (!activeRun(view)) onArticleUpdatedRef.current?.(created);
                } else {
                    for (const updated of view.articleUpdates || []) onArticleUpdatedRef.current?.(updated);
                }
                return true;
            } catch {
                return false;
            }
        },
        [mergeRun]
    );

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

    const restoreArticleId = session?.articleId;
    useEffect(() => {
        if (!disabled && restoreArticleId !== undefined) void refreshRun(restoreArticleId);
        // Read once when entering an editor. Confirmations use a new POST/SSE, never polling.
    }, [scope, restoreArticleId, disabled, refreshRun]);

    const send = async (
        input: string,
        context: AIContent[],
        articleId: number,
        approval?: { run: ChatRun; decision: "approve" | "reject" }
    ) => {
        if (approval)
            context = (context as ChatMessage[]).filter(
                (message) =>
                    message.runId !== approval.run.runId && !message.messageId?.startsWith(`${approval.run.runId}:`)
            );
        const prompt = input.trim();
        if (!prompt || disabled || pending.current) return;
        const res = getRes().articleEdit.knowledge;
        const errors: Readonly<Record<string, string>> = {
            permission: res.permission,
            saveFailed: res.saveFailed,
            requestTimeout: res.requestTimeout,
            responseIncomplete: res.responseIncomplete,
            providerRequestFailed: res.providerRequestFailed,
            providerResponseInvalid: res.providerResponseInvalid,
            requestFailed: res.requestFailed,
        };
        const controller = new AbortController();
        pending.current = controller;
        const run = ++generation.current;
        setBusy(true);
        setStatus(res.thinking);
        const publish = (next: ChatMessage[]) => {
            setMessages(next);
            onMessagesChange?.(next, articleId);
        };
        restorePending.current = () => (approval ? mergeRun(approval.run, context) : publish(context));
        const question: ChatMessage = {
            role: "user",
            content: prompt,
            thinking: false,
            messageType: "knowledge",
            runId: approval?.run.runId,
        };
        const base: ChatMessage[] = [...context, question];
        publish([...base, { role: "assistant", content: "", thinking: true, messageType: "knowledge" }]);
        let lastReasoning = "";
        let lastContent = "";
        let latestCheckpoint: ChatRun | undefined;
        let restoredRun = false;
        const updatedVersions = new Set<string>();
        const draftUpdates: ArticleUpdatedEvent[] = [...(approval?.run.articleUpdates || [])];
        const receiveUpdate = (event: ArticleUpdatedEvent) => {
            if (
                !Number.isInteger(event.articleId) ||
                event.articleId <= 0 ||
                !Number.isInteger(event.version) ||
                event.version < 0
            )
                return;
            const key = `${event.articleId}/${event.version}`;
            if (updatedVersions.has(key)) return;
            updatedVersions.add(key);
            if (articleId === 0) draftUpdates.push(event);
            else if (event.articleId === articleId) onArticleUpdatedRef.current?.(event);
        };
        const consume = (text: string, final: boolean) => {
            if (run !== generation.current) return;
            const events = parseChatEvents(text);
            question.runId = events.find((event) => event.type === "run-start")?.runId || question.runId;
            // XHR supplies the entire response again on each progress callback and at completion.
            // A committed write still needs refreshing if a later model/conversation step fails.
            for (const event of events) {
                if (event.type === "article-updated")
                    receiveUpdate({
                        articleId: event.articleId!,
                        version: event.version!,
                        ...(event.created ? { created: true } : {}),
                    });
            }
            const checkpoint = [...events]
                .reverse()
                .find((event) => event.type === "approval-required" || event.type === "run-state");
            if (checkpoint?.run && checkpoint.run.articleId === articleId) {
                const view = checkpoint.run;
                latestCheckpoint = view;
                for (const updated of view.articleUpdates || []) receiveUpdate(updated);
                mergeRun(view, context);
                restorePending.current = () => mergeRun(view, context);
                return;
            }
            const error = events.find((e) => e.type === "error");
            if (error) throw new Error(errors[error.error || ""] || res.requestFailed);
            const progress = [...events].reverse().find((e) => e.type === "tool" || e.type === "thinking");
            if (progress) {
                const toolStatus: Record<string, string> = {
                    search_articles: res.searching,
                    read_article: res.reading,
                    get_article: res.reading,
                    list_categories: res.listingCategories,
                    list_tags: res.listingTags,
                    create_article: res.creating,
                    update_article: res.updating,
                    publish_article: res.publishing,
                    upload_attachment: res.uploading,
                };
                setStatus(toolStatus[progress.tool || ""] || res.thinking);
            }
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
                    {
                        role: "assistant",
                        content,
                        thinking: !content,
                        messageType: "knowledge",
                        reasoningContent,
                        runId: question.runId,
                    },
                ]);
            }
            const answer = events.find((e) => e.type === "answer");
            if (final && (!answer || !events.some((e) => e.type === "done"))) throw new Error(res.responseIncomplete);
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
                approval ? "/api/admin/article/ai/approval" : "/api/admin/article/ai",
                approval
                    ? {
                          articleId,
                          runId: approval.run.runId,
                          approvalId: approval.run.approval?.id,
                          decision: approval.decision,
                      }
                    : { input: prompt, articleId },
                requestConfig
            );
            consume(typeof response.data === "string" ? response.data : "", true);
        } catch (error) {
            if (approval && run === generation.current && (restoredRun = await refreshRun(articleId))) return;
            if (latestCheckpoint && run === generation.current) {
                mergeRun(latestCheckpoint, context);
                return;
            }
            if (run === generation.current)
                publish([
                    ...base,
                    {
                        role: "assistant",
                        content:
                            error instanceof Error && Object.values(errors).includes(error.message)
                                ? error.message
                                : res.requestFailed,
                        thinking: false,
                        failed: true,
                        messageType: "knowledge",
                        runId: question.runId,
                    },
                ]);
        } finally {
            if (run === generation.current) {
                pending.current = undefined;
                restorePending.current = undefined;
                setBusy(false);
                setStatus("");
                // Switching from draft to article changes the hook scope and aborts its stream.
                // Bind only after the answer is saved or a terminal checkpoint is received.
                const created = articleId === 0 ? createdArticleUpdate(draftUpdates) : undefined;
                if (created && !restoredRun && (!latestCheckpoint || !activeRun(latestCheckpoint)))
                    onArticleUpdatedRef.current?.(created);
            }
        }
    };
    const decide = (view: ChatRun, decision: "approve" | "reject", context: AIContent[]) =>
        send(view.input, context, view.articleId, { run: view, decision });
    return { messages, busy, status, send, clear, stop, decide, refreshRun };
};
