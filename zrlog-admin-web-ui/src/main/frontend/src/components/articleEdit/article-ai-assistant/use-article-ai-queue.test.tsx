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

it("retains the original File after failure and sends it again on explicit resume", async () => {
    send.mockResolvedValueOnce(false).mockResolvedValue(true);
    const file = new File(["private report"], "report.txt");
    render("user/7");
    await act(async () => queue.add("Read report", undefined, undefined, [file]));
    expect(queue.paused).toBe(true);
    expect(queue.messages[0].files?.[0]).toBe(file);
    await act(async () => queue.resume());
    expect(send.mock.calls[1][0].files[0]).toBe(file);
    expect(queue.messages).toHaveLength(0);
});

it("preserves reference IDs and attachment indexes through queueing and explicit resume", async () => {
    send.mockResolvedValueOnce(false).mockResolvedValue(true);
    const file = new File(["report"], "report.txt");
    const references = [
        { type: "article" as const, articleId: 42 },
        { type: "file" as const, fileIndex: 0 },
    ];
    render("user/7", true);
    act(() => queue.add("Compare", undefined, undefined, [file], references));
    references[0].articleId = 99;
    await act(async () => render("user/7"));
    expect(queue.messages[0].references).toEqual([
        { type: "article", articleId: 42 },
        { type: "file", fileIndex: 0 },
    ]);
    await act(async () => queue.resume());
    expect(send.mock.calls[1][0].references).toEqual(send.mock.calls[0][0].references);
    expect(send.mock.calls[1][0].files[0]).toBe(file);
});

it("preserves files on draft adoption and discards them when switching to another article", () => {
    render("user/draft", true);
    const file = new File(["draft"], "draft.txt");
    act(() => queue.add("Analyze", undefined, undefined, [file]));
    act(() => queue.migrate("user/7"));
    render("user/7", true);
    expect(queue.messages[0].files?.[0]).toBe(file);
    render("user/8", true);
    expect(queue.messages).toHaveLength(0);
    expect(send).not.toHaveBeenCalled();
});

it("retains attachments after an unexpected send rejection for explicit retry", async () => {
    send.mockRejectedValueOnce(new Error("Connection lost")).mockResolvedValue(true);
    const file = new File(["image"], "screenshot.png");
    render("user/7");
    await act(async () => queue.add("Describe screenshot", undefined, undefined, [file]));
    expect(queue.paused).toBe(true);
    expect(queue.messages[0].files?.[0]).toBe(file);
    await act(async () => queue.resume());
    expect(send.mock.calls[1][0].files[0]).toBe(file);
    expect(queue.messages).toHaveLength(0);
});
