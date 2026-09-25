import { act } from "react";
import { afterEach, beforeEach, describe, expect, it, jest } from "@jest/globals";
import { createRoot, Root } from "react-dom/client";
import { AxiosInstance } from "axios";
import { getRes } from "../../../utils/constants";
import { BasicUserInfo } from "../../../type";
import {
    KnowledgeMessage,
    parseKnowledgeEvents,
    renderKnowledgeMessage,
    useKnowledgeAssistant,
} from "./use-knowledge-assistant";

describe("knowledge assistant", () => {
    let root: Root;
    let container: HTMLDivElement;
    const post = jest.fn<Promise<{ data: string }>, any[]>();
    const api = { post } as unknown as AxiosInstance;
    const onMessagesChange = jest.fn();
    const completed = (
        content: string,
        question = "Find deployment articles",
        reasoningContent?: string,
        sources: KnowledgeMessage["sources"] = []
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
    let chat: ReturnType<typeof useKnowledgeAssistant>;
    function Harness({ scope = "1" }: { scope?: string }) {
        chat = useKnowledgeAssistant(api, false, scope, onMessagesChange);
        return (
            <>
                {chat.messages.map((m, index) => (
                    <div key={index}>
                        {renderKnowledgeMessage({ content: m, index, defaultNode: <p>{m.content}</p> })}
                    </div>
                ))}
            </>
        );
    }
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
        onMessagesChange.mockClear();
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
    it("publishes saved messages with stable IDs and sources to the article cache", async () => {
        const sources = [
            { id: 1, title: "Deploy guide", url: "https://example.com/1", draft: false, privateArticle: false },
        ];
        post.mockResolvedValue({ data: completed("Deployment answer", undefined, undefined, sources) });
        await send();
        expect(post.mock.calls[0][1]).toEqual({
            input: "Find deployment articles",
            articleId: 0,
            includeArticleContext: true,
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
            void chat.send("Next question", saved, 7, false);
        });
        expect(post.mock.calls[0][1]).toEqual({ input: "Next question", articleId: 7, includeArticleContext: false });
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
            pending = chat.send("Private question", [], 0);
        });
        act(() => root.render(<Harness scope="2" />));
        await act(async () => {
            resolve({ data: completed("Old private answer", "Private question") });
            await pending;
        });
        expect(chat.messages).toEqual([]);
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
        expect(chat.status).toBe(getRes().articleEdit.knowledge.thinking);
        expect(chat.status).not.toBe(getRes().articleEdit.knowledge.searching);
        act(() =>
            post.mock.calls[0][2].onDownloadProgress({
                event: { target: { responseText: 'data: {"type":"thinking"}\n\n' } },
            })
        );
        expect(chat.status).toBe(getRes().articleEdit.knowledge.thinking);
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
        expect(container.textContent).toContain(getRes().articleEdit.knowledge.requestFailed);
    });
    it("ignores unfinished SSE frames", () => {
        expect(parseKnowledgeEvents('data: {"type":"thinking"}\n\ndata: {"type":"ans')).toEqual([{ type: "thinking" }]);
    });
});
