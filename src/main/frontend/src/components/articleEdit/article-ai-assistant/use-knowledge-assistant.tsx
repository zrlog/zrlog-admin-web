import { useEffect, useRef, useState } from "react";
import { Alert, Button, Checkbox, Input, Space, Typography } from "antd";
import { AxiosInstance, AxiosRequestConfig } from "axios";
import { AIContent } from "@editor/dist/ai/AIContentItem";
import { AIButtonRenderMessageOptions } from "@editor/dist/ai/AIButton";
import { getRes } from "../../../utils/constants";
import { getSsDate } from "../../../base/SsData";

type Source = { id: number; title: string; url: string; draft: boolean; privateArticle: boolean };
type KnowledgeMessage = AIContent & { sources?: Source[]; failed?: boolean };
type Options = { allArticles: boolean; drafts: boolean; privateArticles: boolean };
type Event = { type: string; content?: string; tool?: string; sources?: Source[]; error?: string };

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

export const useKnowledgeAssistant = (api: AxiosInstance, disabled: boolean) => {
    const res = getRes().articleEdit.knowledge;
    const [messages, setMessages] = useState<KnowledgeMessage[]>([]);
    const [input, setInput] = useState("");
    const [options, setOptions] = useState<Options>({ allArticles: false, drafts: false, privateArticles: false });
    const [busy, setBusy] = useState(false);
    const [status, setStatus] = useState("");
    const pending = useRef<AbortController>();
    const generation = useRef(0);
    useEffect(
        () => () => {
            generation.current++;
            pending.current?.abort();
        },
        []
    );
    const clear = () => {
        generation.current++;
        pending.current?.abort();
        pending.current = undefined;
        setMessages([]);
        setBusy(false);
        setStatus("");
    };
    const changeOptions = (next: Options) => {
        clear();
        setOptions(next);
    };
    const send = async () => {
        if (!input.trim() || disabled || pending.current) return;
        const controller = new AbortController();
        pending.current = controller;
        const run = ++generation.current;
        const prompt = input.trim();
        setInput("");
        setBusy(true);
        setStatus(res.thinking);
        const base = [...messages, { role: "user", content: prompt, thinking: false } as KnowledgeMessage];
        setMessages([...base, { role: "assistant", content: "", thinking: true }]);
        let history = messages
            .filter((m) => !m.failed && m.content)
            .slice(-8)
            .map(({ role, content }) => ({ role, content }));
        while (history.reduce((total, m) => total + m.content.length, 0) > 32000) history = history.slice(1);
        const consume = (text: string, final: boolean) => {
            if (run !== generation.current) return;
            const events = parseKnowledgeEvents(text);
            const error = events.find((e) => e.type === "error");
            if (error) throw new Error(error.error === "permission" ? res.permission : res.requestFailed);
            const progress = [...events].reverse().find((e) => e.type === "tool" || e.type === "thinking");
            if (progress)
                setStatus(
                    progress.tool === "search_articles"
                        ? res.searching
                        : progress.tool === "read_article"
                        ? res.reading
                        : res.thinking
                );
            const answer = events.find((e) => e.type === "answer");
            if (final && (!answer || !events.some((e) => e.type === "done"))) throw new Error(res.requestFailed);
            if (answer && final)
                setMessages([
                    ...base,
                    {
                        role: "assistant",
                        content: answer.content || "",
                        sources: answer.sources || [],
                        thinking: false,
                    },
                ]);
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
                { input: prompt, options, history },
                requestConfig
            );
            consume(typeof response.data === "string" ? response.data : "", true);
        } catch (error) {
            if (run === generation.current)
                setMessages([
                    ...base,
                    {
                        role: "assistant",
                        content:
                            error instanceof Error && [res.permission, res.requestFailed].includes(error.message)
                                ? error.message
                                : res.requestFailed,
                        thinking: false,
                        failed: true,
                    },
                ]);
        } finally {
            if (run === generation.current) {
                pending.current = undefined;
                setBusy(false);
                setStatus("");
            }
        }
    };
    const role = getSsDate().user?.role;
    const renderFooter = () => (
        <Space orientation="vertical" style={{ width: "100%", padding: 12 }}>
            <Typography.Text type="secondary">{res.description}</Typography.Text>
            <Space wrap>
                {["owner", "admin", "editor"].includes(role || "") && (
                    <Checkbox
                        disabled={busy}
                        checked={options.allArticles}
                        onChange={(e) => changeOptions({ ...options, allArticles: e.target.checked })}
                    >
                        {res.allArticles}
                    </Checkbox>
                )}
                <Checkbox
                    disabled={busy}
                    checked={options.drafts}
                    onChange={(e) => changeOptions({ ...options, drafts: e.target.checked })}
                >
                    {res.drafts}
                </Checkbox>
                <Checkbox
                    disabled={busy}
                    checked={options.privateArticles}
                    onChange={(e) => changeOptions({ ...options, privateArticles: e.target.checked })}
                >
                    {res.privateArticles}
                </Checkbox>
            </Space>
            <Typography.Text type="secondary">{res.sessionHint}</Typography.Text>
            {busy && <Typography.Text role="status">{status}</Typography.Text>}
            <Input.TextArea
                aria-label={res.input}
                placeholder={res.input}
                value={input}
                maxLength={8000}
                disabled={busy || disabled}
                autoSize={{ minRows: 2, maxRows: 5 }}
                onChange={(e) => setInput(e.target.value)}
            />
            <Space>
                <Button
                    aria-label={res.send}
                    type="primary"
                    loading={busy}
                    disabled={disabled || !input.trim()}
                    onClick={() => void send()}
                >
                    {res.send}
                </Button>
                <Button disabled={!messages.length && !busy} onClick={clear}>
                    {busy ? res.stop : res.clear}
                </Button>
            </Space>
        </Space>
    );
    const renderMessage = ({ content, index, defaultNode }: AIButtonRenderMessageOptions) => {
        const message = messages[index];
        return (
            <>
                {message?.failed ? <Alert type="error" title={content.content} /> : defaultNode}
                {!!message?.sources?.length && (
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
    return { messages, busy, renderFooter, renderMessage };
};
