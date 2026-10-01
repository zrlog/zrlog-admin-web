import { act } from "react";
import { afterEach, beforeEach, describe, expect, it, jest } from "@jest/globals";
import { createRoot, Root } from "react-dom/client";
import { AxiosInstance } from "axios";
import { getRes } from "../../../utils/constants";
import { BasicUserInfo } from "../../../type";
import { ChatMessage, ChatRun, parseChatEvents, renderChatMessage, useArticleChat } from "./use-article-chat";

describe("knowledge assistant", () => {
    let root: Root;
    let container: HTMLDivElement;
    const post = jest.fn<Promise<{ data: string }>, any[]>();
    const get = jest.fn<Promise<{ data: { error: number; data?: ChatRun } }>, any[]>();
    const api = { post, get } as unknown as AxiosInstance;
    const onMessagesChange = jest.fn();
    const onArticleUpdated = jest.fn();
    const completed = (
        content: string,
        question = "Find deployment articles",
        reasoningContent?: string,
        sources: ChatMessage["sources"] = []
    ) =>
        `data: ${JSON.stringify({
            type: "answer",
            content,
            sources,
            messages: [
                { role: "user", content: question, messageType: "knowledge", messageId: "question-id" },
                {
                    role: "assistant",
                    content,
                    messageType: "knowledge",
                    messageId: "answer-id",
                    reasoningContent,
                    sources,
                },
            ],
        })}\n\ndata: {"type":"done"}\n\n`;
    let chat: ReturnType<typeof useArticleChat>;
    function Harness({
        scope = "1",
        restore = false,
        history = [],
        articleId = 7,
    }: {
        scope?: string;
        restore?: boolean;
        history?: ChatMessage[];
        articleId?: number;
    }) {
        chat = useArticleChat(
            api,
            false,
            scope,
            onMessagesChange,
            onArticleUpdated,
            restore ? { articleId, messages: history } : undefined
        );
        return (
            <>
                {chat.messages.map((m, index) => (
                    <div key={index}>{renderChatMessage({ content: m, index, defaultNode: <p>{m.content}</p> })}</div>
                ))}
            </>
        );
    }
    it("streams validated skill cards, restores title selection, and resumes the same task", async () => {
        const frame = (event: object) => `data: ${JSON.stringify(event)}\n\n`;
        const revision = "0123456789abcdef0123456789abcdef";
        const skill: ChatMessage = {
            role: "assistant",
            thinking: false,
            content: "Title candidates",
            messageId: "run:skill:1",
            messageType: "writingSkill",
            tool: "title",
            payload: { titles: ["First", "Second"] },
            skillContract: { version: 1, contextRevision: revision, applicableFields: ["title"] },
        };
        const view: ChatRun = {
            runId: "run",
            articleId: 7,
            input: "Titles then summary",
            status: "awaiting_input",
            skillMessages: [skill],
            interaction: {
                id: "input",
                kind: "select",
                question: "Choose a title",
                options: ["First", "Second"],
                contextRevision: revision,
                expiresAt: Date.now() + 60000,
            },
        };
        const progress =
            frame({ type: "run-start", runId: "run" }) + frame({ type: "skill-result", messages: [skill] });
        post.mockImplementationOnce(async (_url: unknown, _body: unknown, config: any) => {
            config.onDownloadProgress({ event: { target: { responseText: progress } } });
            config.onDownloadProgress({ event: { target: { responseText: progress } } });
            return { data: progress + frame({ type: "interaction-required", run: view }) };
        });
        await act(async () => root.render(<Harness />));
        await act(async () =>
            chat.send(view.input, [], 7, undefined, {
                editorContext: {
                    title: "Local title",
                    alias: "",
                    markdown: "Unsaved body",
                    digest: "",
                    keywords: "",
                    thumbnail: "",
                    selectedText: "",
                },
                contextRevision: revision,
            })
        );
        expect(chat.messages.filter((entry) => entry.messageType === "writingSkill")).toHaveLength(1);
        expect(chat.messages[chat.messages.length - 1].run?.status).toBe("awaiting_input");
        expect(post.mock.calls[0][1]).toMatchObject({
            editorContext: { markdown: "Unsaved body" },
            contextRevision: revision,
        });
        const digest: ChatMessage = {
            ...skill,
            messageId: "run:skill:2",
            tool: "digest",
            payload: { digest: "Summary" },
            skillContract: { version: 1, contextRevision: revision, applicableFields: ["digest"] },
        };
        post.mockResolvedValueOnce({
            data:
                frame({
                    type: "answer",
                    messages: [
                        { role: "user", content: view.input, messageId: "run:user", messageType: "knowledge" },
                        skill,
                        digest,
                        { role: "assistant", content: "Done", messageId: "run:assistant", messageType: "knowledge" },
                    ],
                }) + frame({ type: "done" }),
        });
        await act(async () => chat.respond(view, "submit", "Second", revision, chat.messages));
        expect(post.mock.calls[1][0]).toBe("/api/admin/article/ai/input");
        expect(post.mock.calls[1][1]).toMatchObject({
            runId: "run",
            interactionId: "input",
            value: "Second",
            decision: "submit",
            contextRevision: revision,
        });
        expect(chat.messages).toHaveLength(4);
        expect(chat.outcome.current).toBe(true);
    });

    beforeEach(() => {
        (globalThis as any).IS_REACT_ACT_ENVIRONMENT = true;
        global.ResizeObserver = class {
            observe() {
                return undefined;
            }
            unobserve() {
                return undefined;
            }
            disconnect() {
                return undefined;
            }
        };
        window.__SS_DATA__ = {
            key: "knowledge",
            pageBuildId: "test",
            systemNotification: "",
            user: {
                userId: 1,
                userName: "owner",
                header: "",
                key: "test",
                role: "owner",
                actions: [],
            } as BasicUserInfo,
        };
        window.matchMedia = (() => ({
            matches: false,
            addListener() {
                return undefined;
            },
            removeListener() {
                return undefined;
            },
            addEventListener() {
                return undefined;
            },
            removeEventListener() {
                return undefined;
            },
        })) as any;
        container = document.createElement("div");
        document.body.appendChild(container);
        root = createRoot(container);
        post.mockReset();
        get.mockReset();
        onMessagesChange.mockClear();
        onArticleUpdated.mockClear();
        act(() => root.render(<Harness />));
    });
    afterEach(() => {
        act(() => root.unmount());
        container.remove();
        delete (globalThis as any).IS_REACT_ACT_ENVIRONMENT;
    });
    async function send() {
        await act(async () => {
            await chat.send("Find deployment articles", chat.messages, 0);
        });
    }
    const pausedRun = (): ChatRun => ({
        runId: "run-1",
        articleId: 7,
        input: "Update title",
        status: "awaiting_approval",
        approval: {
            id: "approval-1",
            tool: "update_article",
            articleId: 7,
            title: "Before",
            version: 2,
            expiresAt: Date.now() + 60000,
            publicImpact: false,
            changes: [{ field: "title", before: "Before", after: "After", truncated: false }],
        },
    });
    it("accepts a paused SSE and continues the same question through a new POST", async () => {
        const view = pausedRun();
        post.mockResolvedValueOnce({ data: `data: ${JSON.stringify({ type: "approval-required", run: view })}\n\n` });
        await act(async () => {
            await chat.send(view.input, [], 7);
        });
        expect(chat.busy).toBe(false);
        expect(chat.messages).toHaveLength(2);
        expect(chat.messages[1].run).toEqual(view);
        expect(chat.messages[1].failed).toBeUndefined();
        expect(get).not.toHaveBeenCalled();
        post.mockResolvedValueOnce({
            data: 'data: {"type":"article-updated","articleId":7,"version":3}\n\n' + completed("Saved", view.input),
        });
        await act(async () => {
            await chat.decide(view, "approve", chat.messages);
        });
        expect(post.mock.calls[1][0]).toBe("/api/admin/article/ai/approval");
        expect(post.mock.calls[1][1]).toEqual({
            articleId: 7,
            runId: "run-1",
            approvalId: "approval-1",
            decision: "approve",
        });
        expect(chat.messages).toHaveLength(2);
        expect(chat.messages[0].content).toBe(view.input);
        expect(chat.messages[1].content).toBe("Saved");
        expect(onArticleUpdated).toHaveBeenCalledWith({ articleId: 7, version: 3 });
        expect(get).not.toHaveBeenCalled();
    });
    it("keeps a received approval card if the first connection closes with a transport error", async () => {
        const view = pausedRun();
        post.mockImplementationOnce(async (_url, _body, config) => {
            config.onDownloadProgress({
                event: {
                    target: { responseText: `data: ${JSON.stringify({ type: "approval-required", run: view })}\n\n` },
                },
            });
            throw new Error("connection closed");
        });
        await act(async () => {
            await chat.send(view.input, [], 7);
        });
        expect(chat.messages).toHaveLength(2);
        expect(chat.messages[1].run).toEqual(view);
        expect(chat.messages[1].failed).toBeUndefined();
    });
    it("restores once on entry without polling and ignores a previous editor response", async () => {
        let resolve!: (value: { data: { error: number; data: ChatRun } }) => void;
        get.mockReturnValueOnce(
            new Promise((done) => {
                resolve = done;
            })
        );
        await act(async () => root.render(<Harness scope="restore" restore />));
        expect(get).toHaveBeenCalledTimes(1);
        await act(async () => root.render(<Harness scope="restore" restore />));
        expect(get).toHaveBeenCalledTimes(1);
        await act(async () => {
            resolve({ data: { error: 0, data: pausedRun() } });
        });
        expect(chat.messages[1].run?.approval?.id).toBe("approval-1");
        get.mockReturnValueOnce(
            new Promise((done) => {
                resolve = done;
            })
        );
        let refreshing!: Promise<boolean>;
        act(() => {
            refreshing = chat.refreshRun(7);
        });
        act(() => root.render(<Harness scope="another" />));
        await act(async () => {
            resolve({ data: { error: 0, data: pausedRun() } });
            await refreshing;
        });
        expect(chat.messages).toHaveLength(0);
    });
    it("reconciles a disconnected confirmation once and keeps the persisted task state", async () => {
        const view = pausedRun();
        post.mockRejectedValue(new Error("connection lost"));
        get.mockResolvedValue({ data: { error: 0, data: { ...view, status: "uncertain" } } });
        await act(async () => {
            await chat.decide(view, "approve", []);
        });
        expect(get).toHaveBeenCalledTimes(1);
        expect(post).toHaveBeenCalledTimes(1);
        expect(chat.messages[1].run?.status).toBe("uncertain");
        expect(chat.busy).toBe(false);
    });
    it("keeps a recovered completed turn in its original place and removes cancelled cards", async () => {
        const view = pausedRun();
        const pair: ChatMessage[] = [
            { role: "user", thinking: false, content: view.input, messageId: `${view.runId}:user` },
            { role: "assistant", thinking: false, content: "Saved", messageId: `${view.runId}:assistant` },
        ];
        const later: ChatMessage = { role: "assistant", thinking: false, content: "Later answer", messageId: "later" };
        get.mockResolvedValue({
            data: { error: 0, data: { ...view, status: "completed", answer: { type: "answer", messages: pair } } },
        });
        await act(async () => root.render(<Harness restore history={[...pair, later]} />));
        expect(chat.messages.map((message) => message.messageId)).toEqual([
            `${view.runId}:user`,
            `${view.runId}:assistant`,
            "later",
        ]);
        const pending: ChatMessage[] = [
            later,
            { role: "user", thinking: false, content: view.input, runId: view.runId },
            { role: "assistant", thinking: false, content: "", runId: view.runId, run: view },
        ];
        get.mockResolvedValue({ data: { error: 0, data: { ...view, status: "cancelled" } } });
        await act(async () => root.render(<Harness scope="cancelled" restore history={pending} />));
        expect(chat.messages).toEqual([later]);
    });
    it("replaces an unfinished question after losing the initial run-start event", async () => {
        const view = pausedRun();
        const unfinished: ChatMessage[] = [
            { role: "user", thinking: false, content: view.input },
            { role: "assistant", thinking: false, content: "Connection lost", failed: true },
        ];
        get.mockResolvedValue({ data: { error: 0, data: view } });
        await act(async () => root.render(<Harness restore history={unfinished} />));
        expect(chat.messages).toHaveLength(2);
        expect(chat.messages[1].run).toEqual(view);
    });
    it("publishes saved messages with stable IDs and sources to the article cache", async () => {
        const sources = [
            { id: 1, title: "Deploy guide", url: "https://example.com/1", draft: false, privateArticle: false },
        ];
        post.mockResolvedValue({ data: completed("Deployment answer", undefined, undefined, sources) });
        await send();
        expect(post.mock.calls[0][1]).toEqual({
            input: "Find deployment articles",
            articleId: 0,
        });
        expect(container.textContent).toContain("Deployment answer");
        expect(container.querySelector("a")?.getAttribute("href")).toBe("https://example.com/1");
        expect(onMessagesChange).toHaveBeenLastCalledWith(chat.messages, 0);
        expect(chat.messages[1].messageId).toBe("answer-id");
        const calls = onMessagesChange.mock.calls.length;
        act(() => root.render(<Harness scope="2" />));
        expect(container.textContent).not.toContain("Deployment answer");
        // Leaving a completed conversation must not clear its saved article cache.
        expect(onMessagesChange).toHaveBeenCalledTimes(calls);
    });
    it("preserves existing messages when stopping an unfinished answer", async () => {
        const saved = [{ role: "assistant" as const, content: "Saved answer", thinking: false }];
        post.mockReturnValue(new Promise(() => undefined));
        act(() => {
            void chat.send("Next question", saved, 7);
        });
        expect(post.mock.calls[0][1]).toEqual({ input: "Next question", articleId: 7 });
        act(() => chat.stop());
        expect(chat.messages).toEqual(saved);
        expect(onMessagesChange).toHaveBeenLastCalledWith(saved, 7);
        expect(chat.busy).toBe(false);
    });
    it("renders text and reasoning chunks before completion and replaces them with saved messages", async () => {
        let resolve!: (value: { data: string }) => void;
        post.mockReturnValue(
            new Promise((done) => {
                resolve = done;
            })
        );
        let pending!: Promise<void>;
        act(() => {
            pending = chat.send("Hello", [], 7);
        });
        const reasoning =
            'data: {"type":"reasoning_delta","reasoningContent":"Think "}\n\ndata: {"type":"reasoning_delta","reasoningContent":"first"}\n\n';
        const first =
            reasoning +
            'data: {"type":"reasoning","reasoningContent":"Think first"}\n\ndata: {"type":"delta","content":"你"}\n\n';
        act(() => post.mock.calls[0][2].onDownloadProgress({ event: { target: { responseText: first } } }));
        expect(chat.busy).toBe(true);
        expect(chat.messages[1].content).toBe("你");
        expect(chat.messages[1].reasoningContent).toBe("Think first");
        expect(chat.messages[1].messageId).toBeUndefined();
        const second = first + 'data: {"type":"delta","content":"好🙂"}\n\n';
        act(() => post.mock.calls[0][2].onDownloadProgress({ event: { target: { responseText: second } } }));
        // XHR progress carries the accumulated response, so repeated callbacks must not duplicate text.
        act(() => post.mock.calls[0][2].onDownloadProgress({ event: { target: { responseText: second } } }));
        expect(container.textContent).toContain("你好🙂");
        expect(chat.messages[1].content).toBe("你好🙂");
        await act(async () => {
            resolve({ data: second + completed("你好🙂", "Hello", "Think first") });
            await pending;
        });
        expect(chat.messages[1].messageId).toBe("answer-id");
        expect(chat.messages[1].reasoningContent).toBe("Think first");
        expect(chat.busy).toBe(false);
    });
    it("refreshes each saved version once during streaming, even if the answer later fails", async () => {
        let resolve!: (value: { data: string }) => void;
        post.mockReturnValue(
            new Promise((done) => {
                resolve = done;
            })
        );
        let pending!: Promise<void>;
        act(() => {
            pending = chat.send("Update this article", [], 7);
        });
        const first = 'data: {"type":"article-updated","articleId":7,"version":4}\n\n';
        const progress = (text: string) =>
            act(() =>
                post.mock.calls[0][2].onDownloadProgress({
                    event: { target: { responseText: text } },
                })
            );
        progress(first.slice(0, -1));
        expect(onArticleUpdated).not.toHaveBeenCalled();
        progress(first);
        progress(first);
        expect(onArticleUpdated.mock.calls).toEqual([[{ articleId: 7, version: 4 }]]);
        expect(chat.busy).toBe(true);
        const final =
            first +
            'data: {"type":"article-updated","articleId":8,"version":2}\n\n' +
            'data: {"type":"article-updated","articleId":7,"version":5}\n\n' +
            'data: {"type":"article-updated","articleId":7,"version":-1}\n\n' +
            'data: {"type":"article-updated","articleId":7}\n\n' +
            'data: {"type":"error","error":"saveFailed"}\n\n';
        await act(async () => {
            resolve({ data: final });
            await pending;
        });
        expect(onArticleUpdated.mock.calls).toEqual([[{ articleId: 7, version: 4 }], [{ articleId: 7, version: 5 }]]);
        expect(chat.messages[1].failed).toBe(true);
    });

    it("binds the first created draft only after the complete reply, including version zero", async () => {
        let resolve!: (value: { data: string }) => void;
        post.mockReturnValue(
            new Promise((done) => {
                resolve = done;
            })
        );
        let pending!: Promise<void>;
        act(() => {
            pending = chat.send("Write a draft", [], 0);
        });
        const wire = 'data: {"type":"article-updated","articleId":18,"version":0,"created":true}\n\n';
        act(() => post.mock.calls[0][2].onDownloadProgress({ event: { target: { responseText: wire } } }));
        expect(onArticleUpdated).not.toHaveBeenCalled();
        expect(chat.busy).toBe(true);
        await act(async () => {
            resolve({
                data:
                    wire +
                    'data: {"type":"article-updated","articleId":19,"version":0,"created":true}\n\n' +
                    completed("Draft saved", "Write a draft"),
            });
            await pending;
        });
        expect(onArticleUpdated.mock.calls).toEqual([[{ articleId: 18, version: 0, created: true }]]);
        expect(chat.messages[1].content).toBe("Draft saved");
        expect(chat.busy).toBe(false);
        expect(
            onMessagesChange.mock.invocationCallOrder[onMessagesChange.mock.invocationCallOrder.length - 1]
        ).toBeLessThan(onArticleUpdated.mock.invocationCallOrder[0]);
    });

    it("keeps the draft scope through another approval and binds the latest saved version on completion", async () => {
        const view: ChatRun = {
            ...pausedRun(),
            articleId: 0,
            articleUpdates: [{ articleId: 18, version: 0, created: true }],
        };
        post.mockResolvedValueOnce({ data: `data: ${JSON.stringify({ type: "approval-required", run: view })}\n\n` });
        await act(async () => {
            await chat.send("Write and refine", [], 0);
        });
        expect(onArticleUpdated).not.toHaveBeenCalled();
        post.mockResolvedValueOnce({
            data: 'data: {"type":"article-updated","articleId":18,"version":1}\n\n' + completed("Refined", view.input),
        });
        await act(async () => {
            await chat.decide(view, "approve", chat.messages);
        });
        expect(post.mock.calls[1][1].articleId).toBe(0);
        expect(onArticleUpdated.mock.calls).toEqual([[{ articleId: 18, version: 1, created: true }]]);
    });

    it("does not bind unrelated writes, but still loads a created draft if the model later fails", async () => {
        post.mockResolvedValueOnce({
            data: 'data: {"type":"article-updated","articleId":7,"version":4}\n\n' + completed("Saved"),
        });
        await send();
        expect(onArticleUpdated).not.toHaveBeenCalled();
        post.mockResolvedValueOnce({
            data:
                'data: {"type":"article-updated","articleId":18,"version":0,"created":true}\n\n' +
                'data: {"type":"error","error":"providerRequestFailed"}\n\n',
        });
        await send();
        expect(onArticleUpdated).toHaveBeenCalledWith({ articleId: 18, version: 0, created: true });
    });

    it("does not reopen a completed created article on a fresh draft page, but recovers an interrupted turn", async () => {
        const view: ChatRun = {
            ...pausedRun(),
            articleId: 0,
            status: "completed",
            articleUpdates: [{ articleId: 18, version: 0, created: true }],
        };
        get.mockResolvedValue({ data: { error: 0, data: view } });
        await act(async () => root.render(<Harness restore articleId={0} />));
        expect(onArticleUpdated).not.toHaveBeenCalled();
        expect(chat.messages).toEqual([]);
        await act(async () =>
            root.render(
                <Harness
                    scope="recover"
                    restore
                    articleId={0}
                    history={[{ role: "user", content: view.input, runId: view.runId, thinking: false }]}
                />
            )
        );
        expect(onArticleUpdated).toHaveBeenCalledWith({ articleId: 18, version: 0, created: true });
    });

    it("discards partial streamed text when saving fails", async () => {
        let resolve!: (value: { data: string }) => void;
        post.mockReturnValue(
            new Promise((done) => {
                resolve = done;
            })
        );
        let pending!: Promise<void>;
        act(() => {
            pending = chat.send("Hello", [], 7);
        });
        const partial = 'data: {"type":"delta","content":"Unfinished answer"}\n\n';
        act(() => post.mock.calls[0][2].onDownloadProgress({ event: { target: { responseText: partial } } }));
        expect(container.textContent).toContain("Unfinished answer");
        await act(async () => {
            resolve({ data: partial + 'data: {"type":"error","error":"saveFailed"}\n\n' });
            await pending;
        });
        expect(container.textContent).not.toContain("Unfinished answer");
        expect(chat.messages[1].failed).toBe(true);
        expect(container.textContent).toContain(getRes().articleEdit.knowledge.saveFailed);
    });
    it("does not claim success if the server has not confirmed persistence", async () => {
        post.mockResolvedValue({
            data: 'data: {"type":"answer","content":"Unsaved answer"}\n\ndata: {"type":"done"}\n\n',
        });
        await send();
        expect(container.textContent).not.toContain("Unsaved answer");
        expect(container.textContent).toContain(getRes().articleEdit.knowledge.saveFailed);
    });
    it("ignores late responses after scope changes", async () => {
        let resolve!: (value: { data: string }) => void;
        post.mockReturnValue(
            new Promise((done) => {
                resolve = done;
            })
        );
        let pending!: Promise<void>;
        act(() => {
            pending = chat.send("Private question", [], 7);
        });
        act(() => root.render(<Harness scope="2" />));
        await act(async () => {
            resolve({
                data:
                    'data: {"type":"article-updated","articleId":7,"version":4}\n\n' +
                    completed("Old private answer", "Private question"),
            });
            await pending;
        });
        expect(chat.messages).toEqual([]);
        expect(onArticleUpdated).not.toHaveBeenCalled();
    });
    it("keeps ordinary waiting neutral and shows retrieval status only for actual tool events", async () => {
        let resolve!: (value: { data: string }) => void;
        post.mockReturnValueOnce(
            new Promise((done) => {
                resolve = done;
            })
        );
        let pending!: Promise<void>;
        act(() => {
            pending = chat.send("Hello", [], 0);
        });
        expect(chat.status).toBe(getRes().articleEdit.knowledge.waitingForResponse);
        expect(chat.status).not.toBe(getRes().articleEdit.knowledge.searching);
        act(() =>
            post.mock.calls[0][2].onDownloadProgress({
                event: { target: { responseText: 'data: {"type":"thinking"}\n\n' } },
            })
        );
        expect(chat.status).toBe(getRes().articleEdit.knowledge.waitingForResponse);
        await act(async () => {
            resolve({ data: completed("Hello", "Hello") });
            await pending;
        });
        expect(chat.messages[1].sources).toEqual([]);
        expect(container.textContent).not.toContain(getRes().articleEdit.knowledge.sources);
    });
    it("shows reasoning during tool progress, keeps it with the answer, and excludes it from history", async () => {
        let resolve!: (value: { data: string }) => void;
        post.mockReturnValueOnce(
            new Promise((done) => {
                resolve = done;
            })
        );
        let pending!: Promise<void>;
        act(() => {
            pending = chat.send("Find sources", [], 0);
        });
        const firstRound =
            'data: {"type":"reasoning","reasoningContent":"Find relevant sources first."}\n\ndata: {"type":"tool","tool":"search_articles"}\n\n';
        act(() => {
            post.mock.calls[0][2].onDownloadProgress({ event: { target: { responseText: firstRound } } });
        });
        expect(container.textContent).toContain(getRes().articleEdit.assistant.reasoningProcess);
        expect(container.textContent).toContain("Find relevant sources first.");
        expect(chat.status).toBe(getRes().articleEdit.knowledge.searching);
        const final =
            firstRound +
            'data: {"type":"reasoning","reasoningContent":"Summarize the findings."}\n\n' +
            completed("Answer", "Find sources", "Find relevant sources first.\n\nSummarize the findings.");
        await act(async () => {
            resolve({ data: final });
            await pending;
        });
        expect(chat.messages[1].reasoningContent).toBe("Find relevant sources first.\n\nSummarize the findings.");
        expect(container.textContent).toContain("Answer");
        post.mockResolvedValueOnce({
            data: completed("Follow up"),
        });
        await send();
        expect(post.mock.calls[1][1].history).toBeUndefined();
    });
    it("does not add an empty reasoning panel and clears partial reasoning on failure", async () => {
        let resolve!: (value: { data: string }) => void;
        post.mockReturnValueOnce(
            new Promise((done) => {
                resolve = done;
            })
        );
        let pending!: Promise<void>;
        act(() => {
            pending = chat.send("Question", [], 0);
        });
        expect(container.textContent).not.toContain(getRes().articleEdit.assistant.reasoningProcess);
        const reasoning = 'data: {"type":"reasoning","reasoningContent":"Partial thinking"}\n\n';
        act(() => post.mock.calls[0][2].onDownloadProgress({ event: { target: { responseText: reasoning } } }));
        expect(container.textContent).toContain("Partial thinking");
        await act(async () => {
            resolve({ data: reasoning + 'data: {"type":"error","error":"permission"}\n\n' });
            await pending;
        });
        expect(container.textContent).not.toContain("Partial thinking");
        expect(container.textContent).toContain(getRes().articleEdit.knowledge.permission);
    });
    it("does not present an incomplete or failed response as an answer", async () => {
        post.mockResolvedValue({ data: 'data: {"type":"answer","content":"partial private response"}\n\n' });
        await send();
        expect(container.textContent).not.toContain("partial private response");
        expect(container.textContent).toContain(getRes().articleEdit.knowledge.responseIncomplete);
    });
    it.each(["zh_CN", "en_US"])(
        "distinguishes timeout and provider failures in %s without showing raw details",
        async (lang) => {
            window.__SS_DATA__!.resourceInfo = { lang: lang as "zh_CN" | "en_US" };
            const res = getRes().articleEdit.knowledge;
            for (const [code, message] of Object.entries({
                requestTimeout: res.requestTimeout,
                responseIncomplete: res.responseIncomplete,
                providerRequestFailed: res.providerRequestFailed,
                providerResponseInvalid: res.providerResponseInvalid,
                requestFailed: res.requestFailed,
                unknown: res.requestFailed,
            })) {
                act(() => chat.clear());
                post.mockResolvedValueOnce({
                    data: `data: ${JSON.stringify({
                        type: "error",
                        error: code,
                        message: "private provider detail",
                    })}\n\n`,
                });
                await send();
                expect(chat.busy).toBe(false);
                expect(chat.messages[1].failed).toBe(true);
                expect(container.textContent).toContain(message);
                expect(container.textContent).not.toContain("private provider detail");
            }
        }
    );
    it("ignores unfinished SSE frames", () => {
        expect(parseChatEvents('data: {"type":"thinking"}\n\ndata: {"type":"ans')).toEqual([{ type: "thinking" }]);
    });
});
