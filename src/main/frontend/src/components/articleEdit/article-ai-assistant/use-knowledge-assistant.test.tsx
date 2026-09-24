import { act } from "react";
import { afterEach, beforeEach, describe, expect, it, jest } from "@jest/globals";
import { createRoot, Root } from "react-dom/client";
import { Simulate } from "react-dom/test-utils";
import { AxiosInstance } from "axios";
import { getRes } from "../../../utils/constants";
import { BasicUserInfo } from "../../../type";
import { parseKnowledgeEvents, useKnowledgeAssistant } from "./use-knowledge-assistant";

describe("knowledge assistant", () => {
    let root: Root;
    let container: HTMLDivElement;
    const post = jest.fn<Promise<{ data: string }>, any[]>();
    const api = { post } as unknown as AxiosInstance;
    function Harness() {
        const chat = useKnowledgeAssistant(api, false);
        return (
            <>
                {chat.messages.map((m, index) => (
                    <div key={index}>{chat.renderMessage({ content: m, index, defaultNode: <p>{m.content}</p> })}</div>
                ))}
                {chat.renderFooter()}
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
        act(() => root.render(<Harness />));
    });
    afterEach(() => {
        act(() => root.unmount());
        container.remove();
        delete (globalThis as any).IS_REACT_ACT_ENVIRONMENT;
    });
    async function send() {
        act(() =>
            Simulate.change(container.querySelector("textarea")!, {
                target: { value: "Find deployment articles" },
            } as any)
        );
        await act(async () => {
            Array.from(container.querySelectorAll("button"))
                .find((b) => b.getAttribute("aria-label") === getRes().articleEdit.knowledge.send)!
                .click();
        });
    }
    it("uses narrow scopes, renders returned sources, and clears history when scope changes", async () => {
        post.mockResolvedValue({
            data: 'data: {"type":"answer","content":"Deployment answer","sources":[{"id":1,"title":"Deploy guide","url":"https://example.com/1","draft":false,"privateArticle":false}]}\n\ndata: {"type":"done"}\n\n',
        });
        await send();
        expect(post.mock.calls[0][1].options).toEqual({ allArticles: false, drafts: false, privateArticles: false });
        expect(container.textContent).toContain("Deployment answer");
        expect(container.querySelector("a")?.getAttribute("href")).toBe("https://example.com/1");
        act(() => container.querySelector<HTMLInputElement>('input[type="checkbox"]')!.click());
        expect(container.textContent).not.toContain("Deployment answer");
        await send();
        expect(post.mock.calls[1][1].history).toEqual([]);
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
