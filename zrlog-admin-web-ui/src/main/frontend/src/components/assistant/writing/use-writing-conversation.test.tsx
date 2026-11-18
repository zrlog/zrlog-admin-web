import { act } from "react";
import { createRoot, Root } from "react-dom/client";
import { afterEach, beforeEach, expect, it, jest } from "@jest/globals";
import type { AxiosInstance } from "axios";
import useWritingConversation from "./use-writing-conversation";

const get = jest.fn<Promise<any>, any[]>();
const post = jest.fn<Promise<any>, any[]>();
const api = { get, post } as unknown as AxiosInstance;
let root: Root;
let container: HTMLDivElement;
let session: ReturnType<typeof useWritingConversation>;
function Harness({
    articleId = 0,
    open = true,
    client = api,
}: {
    articleId?: number;
    open?: boolean;
    client?: AxiosInstance;
}) {
    session = useWritingConversation(client, articleId, open);
    return <div>{session.messages.map((m) => m.content).join("|")}</div>;
}
beforeEach(() => {
    (globalThis as any).IS_REACT_ACT_ENVIRONMENT = true;
    window.localStorage.clear();
    window.history.replaceState(null, "", "/");
    get.mockReset().mockResolvedValue({ data: { error: 0, data: { aiMessages: [] } } });
    post.mockReset();
    container = document.createElement("div");
    root = createRoot(container);
});

it("retries a failed history read only when requested after the API client changes", async () => {
    get.mockResolvedValueOnce({ data: { error: 9016, message: "Conversation unavailable" } });
    await act(async () => root.render(<Harness />));
    expect(session.error).toBe("Conversation unavailable");
    expect(session.loading).toBe(false);
    const history = [{ role: "assistant", content: "Restored history" }];
    const latestGet = jest.fn<Promise<any>, any[]>().mockResolvedValue({
        data: { error: 0, data: { aiMessages: history } },
    });
    await act(async () => root.render(<Harness client={{ get: latestGet } as unknown as AxiosInstance} />));
    expect(get).toHaveBeenCalledTimes(1);
    expect(latestGet).not.toHaveBeenCalled();
    expect(session.error).toBe("Conversation unavailable");

    await act(async () => session.retry());
    expect(latestGet).toHaveBeenCalledTimes(1);
    expect(latestGet.mock.calls[0][1].params).toEqual({ id: 0, sessionId: undefined });
    expect(session.error).toBeUndefined();
    expect(container.textContent).toBe("Restored history");
});
afterEach(() => {
    act(() => root.unmount());
    delete (globalThis as any).IS_REACT_ACT_ENVIRONMENT;
});
it("loads only on opening and preserves a running turn when reopening or saving the article", async () => {
    await act(async () => root.render(<Harness open={false} />));
    expect(get).not.toHaveBeenCalled();
    await act(async () => root.render(<Harness />));
    expect(get).toHaveBeenCalledTimes(1);
    act(() => session.change([{ role: "assistant", content: "Still writing", thinking: true }]));
    await act(async () => root.render(<Harness open={false} />));
    await act(async () => root.render(<Harness />));
    expect(get).toHaveBeenCalledTimes(1);
    expect(container.textContent).toContain("Still writing");
    await act(async () => root.render(<Harness articleId={7} />));
    expect(get).toHaveBeenCalledTimes(1);
    expect(container.textContent).toContain("Still writing");
});
it("discards an old article response and keeps the current article conversation isolated", async () => {
    let resolveOld!: (value: any) => void;
    get.mockReturnValueOnce(
        new Promise((resolve) => {
            resolveOld = resolve;
        })
    );
    await act(async () => root.render(<Harness articleId={7} />));
    await act(async () => root.render(<Harness articleId={8} />));
    expect(get.mock.calls[1][1].params).toEqual({ id: 8, sessionId: undefined });
    await act(async () =>
        resolveOld({ data: { error: 0, data: { aiMessages: [{ role: "user", content: "Old history" }] } } })
    );
    expect(container.textContent).not.toContain("Old history");
    act(() => session.change([{ role: "user", content: "New history", thinking: false }]));
    expect(container.textContent).toContain("New history");
});

it("opens one writing conversation on send and follows it to the saved article", async () => {
    const id = "aaabbbcc-1111-4222-8333-aaaabbbbcccc";
    const previous = { role: "user", content: "Existing draft history" };
    post.mockResolvedValue({ data: { error: 0, data: { conversationId: id } } });
    await act(async () => root.render(<Harness />));
    expect(post).not.toHaveBeenCalled();
    get.mockResolvedValueOnce({ data: { error: 0, data: { aiMessages: [previous] } } });
    await act(async () => {
        const first = session.ensureDraft();
        expect(session.ensureDraft()).toBe(first);
        expect(await first).toEqual({ sessionId: id, messages: [previous] });
    });
    expect(post).toHaveBeenCalledTimes(1);
    expect(post).toHaveBeenCalledWith("/api/admin/assistant/chat/draft", {});
    expect(get.mock.calls[1][1].params).toEqual({ id: 0, sessionId: id });
    expect(session.sessionId).toBe(id);
    await act(async () => root.render(<Harness articleId={7} />));
    expect(session.sessionId).toBe(id);
    expect(container.textContent).toContain(previous.content);
    expect(get).toHaveBeenCalledTimes(2);
});
