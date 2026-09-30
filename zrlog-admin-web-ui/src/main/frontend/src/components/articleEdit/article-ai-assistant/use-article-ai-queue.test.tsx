import { act, StrictMode } from "react";
import { createRoot, Root } from "react-dom/client";
import { afterEach, beforeEach, expect, it, jest } from "@jest/globals";
import { useArticleAiQueue } from "./use-article-ai-queue";

let root: Root;
let container: HTMLDivElement;
let queue: ReturnType<typeof useArticleAiQueue>;
const send = jest.fn<Promise<boolean | undefined>, any[]>();
function Harness({ scope, blocked = false }: { scope: string; blocked?: boolean }) {
    queue = useArticleAiQueue(scope, blocked, send);
    return null;
}
const render = (scope: string, blocked = false) =>
    act(() =>
        root.render(
            <StrictMode>
                <Harness scope={scope} blocked={blocked} />
            </StrictMode>
        )
    );
beforeEach(() => {
    (globalThis as any).IS_REACT_ACT_ENVIRONMENT = true;
    container = document.createElement("div");
    root = createRoot(container);
    send.mockReset();
});
afterEach(() => {
    act(() => root.unmount());
    delete (globalThis as any).IS_REACT_ACT_ENVIRONMENT;
});

it("preserves queued prompts and the composer when a draft is adopted during a running turn", async () => {
    let finish!: (success: boolean) => void;
    send.mockReturnValueOnce(
        new Promise((resolve) => {
            finish = resolve;
        })
    ).mockResolvedValue(true);
    render("user/draft");
    act(() => {
        queue.add("Create article");
        queue.add("Refine it", "rewrite", "selected paragraph");
    });
    expect(send).toHaveBeenCalledTimes(1);
    act(() => queue.migrate("user/18"));
    render("user/18");
    expect(queue.key).toBe("user/draft");
    expect(send).toHaveBeenCalledTimes(1);
    await act(async () => {
        finish(true);
    });
    expect(send).toHaveBeenCalledTimes(2);
    expect(send.mock.calls[1][0]).toMatchObject({
        input: "Refine it",
        tool: "rewrite",
        selectedText: "selected paragraph",
    });
    expect(queue.messages).toHaveLength(0);
});

it("retains a rejected dispatch and resumes only after the blocker is gone", async () => {
    send.mockResolvedValueOnce(undefined).mockResolvedValue(true);
    render("user/7");
    await act(async () => queue.add("Keep my question"));
    expect(queue.messages[0].input).toBe("Keep my question");
    expect(queue.paused).toBe(true);
    render("user/7", true);
    act(() => queue.resume());
    expect(send).toHaveBeenCalledTimes(1);
    await act(async () => render("user/7"));
    expect(send).toHaveBeenCalledTimes(2);
    expect(queue.messages).toHaveLength(0);
});

it("lets a new explicit submission start after stopping with an empty queue", async () => {
    send.mockResolvedValue(true);
    render("user/7");
    act(() => queue.pause());
    await act(async () => queue.add("Start again"));
    expect(send).toHaveBeenCalledTimes(1);
    expect(queue.paused).toBe(false);
});
