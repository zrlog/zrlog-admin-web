import { act } from "react";
import { createRoot, Root } from "react-dom/client";
import { afterEach, beforeEach, expect, it, jest } from "@jest/globals";
import type { AxiosInstance } from "axios";
import useWritingConversation from "./use-writing-conversation";

const get = jest.fn<Promise<any>, any[]>();
const api = { get } as unknown as AxiosInstance;
let root: Root;
let container: HTMLDivElement;
let session: ReturnType<typeof useWritingConversation>;
function Harness({ articleId = 0, open = true }: { articleId?: number; open?: boolean }) {
    session = useWritingConversation(api, articleId, open);
    return <div>{session.messages.map((m) => m.content).join("|")}</div>;
}
beforeEach(() => {
    (globalThis as any).IS_REACT_ACT_ENVIRONMENT = true;
    window.localStorage.clear();
    window.history.replaceState(null, "", "/");
    get.mockReset().mockResolvedValue({ data: { error: 0, data: { aiMessages: [] } } });
    container = document.createElement("div");
    root = createRoot(container);
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
    await act(async () => session.select(session.sessionId));
    expect(container.textContent).toContain("Still writing");
    expect(get).toHaveBeenCalledTimes(1);
    await act(async () => root.render(<Harness articleId={7} />));
    expect(get).toHaveBeenCalledTimes(1);
    expect(container.textContent).toContain("Still writing");
});
it("discards an old session response and keeps the chosen session isolated", async () => {
    let resolveOld!: (value: any) => void;
    get.mockReturnValueOnce(
        new Promise((resolve) => {
            resolveOld = resolve;
        })
    );
    await act(async () => root.render(<Harness articleId={7} />));
    await act(async () => session.select("43e80774-3f26-4b28-9fb0-5b48b0aab647"));
    expect(get.mock.calls[1][1].params).toEqual({ id: 7, sessionId: session.sessionId });
    await act(async () =>
        resolveOld({ data: { error: 0, data: { aiMessages: [{ role: "user", content: "Old history" }] } } })
    );
    expect(container.textContent).not.toContain("Old history");
    act(() => session.change([{ role: "user", content: "New history", thinking: false }]));
    expect(container.textContent).toContain("New history");
});
