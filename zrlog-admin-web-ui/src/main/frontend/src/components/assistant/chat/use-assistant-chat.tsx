import { getErrorMessage } from "../../../utils/error-message";
import {
    AiFileAttachment,
    AiFileInput,
    AiFileList,
    AiFilePreviewContext,
    rememberAiFilePreviews,
} from "./assistant-files";
import { Alert, Space, Typography } from "antd";
import { useCallback, useEffect, useRef, useState } from "react";

import { AxiosInstance, AxiosRequestConfig } from "axios";
import { AIContent } from "@zrlog/editor/core/ai/AIContentItem";
import { AIButtonRenderMessageOptions } from "@zrlog/editor/core/ai/AIButton";
import AssistantReasoning from "./assistant-reasoning";
import { getRes } from "../../../utils/constants";
import { AssistantMessage } from "./assistant-chat.types";
import { ArticleUpdatedEvent, EditorSnapshot } from "../writing/writing-assistant.types";
import type { AiMentionInput } from "./assistant-mentions";

type Source = { id: number; title: string; url: string; draft: boolean; privateArticle: boolean };
export type ChatMessage = AssistantMessage & {
    createdAt?: number;
    messageType?: string;
    messageId?: string;
    sources?: Source[];
    attachments?: AiFileAttachment[];
    reasoningContent?: string;
    failed?: boolean;
    runId?: string;
    run?: ChatRun;
};
export type ChatRun = {
    createdAt?: number;
    runId: string;
    articleId?: number;
    taskId?: string;
    sessionId?: string;
    input: string;
    attachments?: AiFileAttachment[];
    status:
        | "awaiting_approval"
        | "awaiting_input"
        | "running"
        | "executing"
        | "completed"
        | "failed"
        | "uncertain"
        | "expired"
        | "cancelled";
    error?: string;
    message?: string;
    interaction?: {
        id: string;
        kind: "select" | "text";
        question: string;
        options: string[];
        resultId?: string;
        contextRevision: string;
        expiresAt: number;
    };
    skillMessages?: ChatMessage[];
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
    message?: string;
    messages?: ChatMessage[];
    articleId?: number;
    version?: number;
    created?: boolean;
};

const activeRun = (view: ChatRun) =>
    ["awaiting_approval", "awaiting_input", "running", "executing"].includes(view.status);
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

export const renderChatMessage = (
    { content, defaultNode }: AIButtonRenderMessageOptions,
    status?: string,
    previewContext?: AiFilePreviewContext
) => {
    const message = content as ChatMessage;
    const res = getRes().articleEdit.knowledge;
    const attachments = (
        <AiFileList
            files={message.attachments}
            previewContext={previewContext}
            align={message.role === "user" ? "end" : "start"}
        />
    );
    if (message.role === "user" && message.attachments?.length && !message.failed) {
        return (
            <Space orientation="vertical" size="small" style={{ width: "100%", minWidth: 0 }}>
                {defaultNode}
                {attachments}
            </Space>
        );
    }
    return (
        <>
            {attachments}
            {!message.failed && message.role === "assistant" && (
                <AssistantReasoning content={message.reasoningContent} thinking={message.thinking} status={status} />
            )}
            {message.failed ? (
                <Alert type="error" title={content.content} />
            ) : content.content || !status ? (
                defaultNode
            ) : null}
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

export const useAssistantChat = (
    api: AxiosInstance,
    disabled: boolean,
    scope: string,
    onMessagesChange?: (messages: AIContent[], articleId?: number) => void,
    onArticleUpdated?: (event: ArticleUpdatedEvent) => void,
    session?: { articleId?: number; taskId?: string; sessionId?: string; messages: AIContent[] }
) => {
    const [messages, setMessages] = useState<ChatMessage[]>([]);
    const [busy, setBusy] = useState(false);
    const [status, setStatus] = useState("");
    const pending = useRef<AbortController>();
    const generation = useRef(0);
    const restorePending = useRef<() => void>();
    const outcome = useRef<boolean | undefined>(false);
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
                          attachments: view.attachments,
                          messageType: "knowledge",
                          createdAt: view.createdAt,
                          runId: view.runId,
                          thinking: false,
                      },
                      ...(view.skillMessages || []),
                      {
                          role: "assistant",
                          content: "",
                          messageType: "knowledge",
                          createdAt: view.createdAt,
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
            if (articleId === undefined && !contextRef.current.session) return false;
            const revision = generation.current;
            try {
                const response = await contextRef.current.api.get<{ error: number; data?: ChatRun }>(
                    "/api/admin/assistant/chat/run",
                    {
                        params: {
                            id: articleId,
                            taskId: contextRef.current.session?.taskId,
                            sessionId: contextRef.current.session?.sessionId,
                        },
                    }
                );
                if (revision !== generation.current || response?.data?.error || !response?.data?.data) return false;
                const view = response.data.data;
                if (
                    view.articleId !== articleId ||
                    view.taskId !== contextRef.current.session?.taskId ||
                    view.sessionId !== contextRef.current.session?.sessionId
                )
                    return false;
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
        outcome.current = false;
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
    const restoreTaskId = session?.taskId;
    const restoreEnabled = Boolean(session);
    useEffect(() => {
        if (!disabled && restoreEnabled) void refreshRun(restoreArticleId);
        // Read once when entering an editor. Confirmations use a new POST/SSE, never polling.
    }, [scope, restoreArticleId, restoreTaskId, restoreEnabled, disabled, refreshRun]);

    const send = async (
        input: string,
        context: AIContent[],
        articleId: number | undefined,
        approval?: {
            run: ChatRun;
            decision: "approve" | "reject" | "submit" | "cancel";
            interaction?: { value?: string; contextRevision: string };
        },
        snapshot?: EditorSnapshot,
        files?: AiFileInput[],
        localFiles?: File[],
        references?: AiMentionInput[]
    ) => {
        if (approval)
            context = (context as ChatMessage[]).filter(
                (message) =>
                    message.runId !== approval.run.runId && !message.messageId?.startsWith(`${approval.run.runId}:`)
            );
        const prompt = input.trim();
        if (!prompt || disabled || pending.current) return;
        outcome.current = false;
        const res = getRes().articleEdit.knowledge;
        const controller = new AbortController();
        pending.current = controller;
        const run = ++generation.current;
        setBusy(true);
        setStatus(res.waitingForResponse);
        const publish = (next: ChatMessage[]) => {
            setMessages(next);
            onMessagesChange?.(next, articleId);
        };
        restorePending.current = () => (approval ? mergeRun(approval.run, context) : publish(context));
        const question: ChatMessage = {
            role: "user",
            createdAt: approval?.run.createdAt || Date.now(),
            content: prompt,
            attachments:
                approval?.run.attachments ||
                rememberAiFilePreviews(
                    files?.map((file) => ({
                        name: file.name,
                        size:
                            Math.floor((file.data.length * 3) / 4) -
                            (file.data.endsWith("==") ? 2 : file.data.endsWith("=") ? 1 : 0),
                    })),
                    localFiles
                ),
            thinking: false,
            messageType: "knowledge",
            runId: approval?.run.runId,
        };
        const base: ChatMessage[] = [...context, question];
        publish([
            ...base,
            { role: "assistant", createdAt: question.createdAt, content: "", thinking: true, messageType: "knowledge" },
        ]);
        let lastReasoning = "";
        let lastContent = "";
        let lastSkillCount = 0;
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
            if (final && text.trimStart().startsWith("{")) {
                const error: unknown = JSON.parse(text);
                throw new Error(getErrorMessage(error, res.requestFailed));
            }
            const events = parseChatEvents(text);
            question.runId = events.find((event) => event.type === "run-start")?.runId || question.runId;
            // Text progress and completion both supply the accumulated response.
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
                .find((event) => ["approval-required", "interaction-required", "run-state"].includes(event.type));
            if (
                checkpoint?.run &&
                checkpoint.run.articleId === articleId &&
                checkpoint.run.taskId === contextRef.current.session?.taskId &&
                checkpoint.run.sessionId === contextRef.current.session?.sessionId
            ) {
                const view = checkpoint.run;
                latestCheckpoint = view;
                // A server-side run may still be active after a client disconnect. Its state
                // response did not accept this new prompt; keep that prompt in the local queue.
                outcome.current =
                    !approval && !events.some((event) => event.type === "run-start")
                        ? undefined
                        : activeRun(view) || view.status === "completed";
                for (const updated of view.articleUpdates || []) receiveUpdate(updated);
                mergeRun(view, context);
                restorePending.current = () => mergeRun(view, context);
                return;
            }
            const error = events.find((e) => e.type === "error");
            if (error) throw new Error(error.message || res.requestFailed);
            const progress = [...events]
                .reverse()
                .find((e) => ["tool", "thinking", "delta", "reasoning_delta", "reasoning"].includes(e.type));
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
                setStatus(
                    progress.type === "delta"
                        ? res.generating
                        : progress.type === "reasoning_delta" || progress.type === "reasoning"
                        ? res.thinking
                        : toolStatus[progress.tool || ""] || res.waitingForResponse
                );
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
            const skillMessages = [
                ...(approval?.run.skillMessages || []),
                ...events.filter((event) => event.type === "skill-result").flatMap((event) => event.messages || []),
            ].filter((entry, index, all) => all.findIndex((other) => other.messageId === entry.messageId) === index);
            if (
                !final &&
                (reasoningContent !== lastReasoning ||
                    content !== lastContent ||
                    skillMessages.length !== lastSkillCount)
            ) {
                lastReasoning = reasoningContent;
                lastContent = content;
                lastSkillCount = skillMessages.length;
                publish([
                    ...base,
                    ...skillMessages,
                    {
                        role: "assistant",
                        createdAt: question.createdAt,
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
                if (!answer.messages || answer.messages.length < 2 || answer.messages.some((entry) => !entry.messageId))
                    throw new Error(res.saveFailed);
                const sentQuestion = answer.messages.find((entry) => entry.role === "user");
                if (sentQuestion) rememberAiFilePreviews(sentQuestion.attachments, localFiles);
                publish([...context, ...answer.messages.map((entry) => ({ ...entry, thinking: false }))]);
                outcome.current = true;
            }
        };
        try {
            const requestConfig: AxiosRequestConfig & { showError: boolean } = {
                showError: false,
                signal: controller.signal,
                responseType: "text",
                headers: { accept: "text/event-stream" },
                onTextProgress: (text) => {
                    try {
                        consume(text, false);
                    } catch {
                        /* Final response owns error rendering. */
                    }
                },
            };
            const response = await api.post(
                approval?.interaction
                    ? "/api/admin/assistant/chat/input"
                    : approval
                    ? "/api/admin/assistant/chat/approval"
                    : "/api/admin/assistant/chat",
                approval
                    ? {
                          articleId,
                          taskId: contextRef.current.session?.taskId,
                          sessionId: contextRef.current.session?.sessionId,
                          runId: approval.run.runId,
                          decision: approval.decision,
                          ...(approval.interaction
                              ? { interactionId: approval.run.interaction?.id, ...approval.interaction }
                              : { approvalId: approval.run.approval?.id }),
                      }
                    : {
                          input: prompt,
                          articleId,
                          taskId: contextRef.current.session?.taskId,
                          sessionId: contextRef.current.session?.sessionId,
                          ...snapshot,
                          ...(files?.length ? { files } : {}),
                          ...(references?.length ? { references } : {}),
                      },
                requestConfig
            );
            consume(typeof response.data === "string" ? response.data : "", true);
        } catch (error) {
            if (run === generation.current) outcome.current = false;
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
                        createdAt: question.createdAt,
                        content: getErrorMessage(error, res.requestFailed),
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
    const respond = (
        view: ChatRun,
        decision: "submit" | "cancel",
        value: string | undefined,
        contextRevision: string,
        context: AIContent[]
    ) => send(view.input, context, view.articleId, { run: view, decision, interaction: { value, contextRevision } });
    return { messages, busy, status, send, clear, stop, decide, respond, refreshRun, outcome };
};
