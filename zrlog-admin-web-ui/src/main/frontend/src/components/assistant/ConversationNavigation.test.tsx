import { act } from "react";
import { createRoot, Root } from "react-dom/client";
import { Simulate } from "react-dom/test-utils";
import { afterEach, beforeEach, expect, it, jest } from "@jest/globals";
import { ConfigProvider, theme as mockTheme } from "antd";
import ConversationNavigation from "./ConversationNavigation";
import { getRes } from "../../utils/constants";

const mockGet = jest.fn<Promise<any>, any[]>();
const mockPost = jest.fn<Promise<any>, any[]>();
const mockApi = { get: mockGet, post: mockPost };
const mockConfirm = jest.fn();
jest.mock("../../base/AppBase", () => ({ useAxiosBaseInstance: () => mockApi }));
jest.mock("@zrlog/ui/feedback", () => ({ useUiApp: () => ({ modal: { confirm: mockConfirm } }) }));
jest.mock("antd-style", () => ({ useTheme: () => mockTheme.getDesignToken() }));
jest.mock("../articleEdit/use-article-editor-screens", () => ({ __esModule: true, default: () => ({ lg: true }) }));
let root: Root, container: HTMLDivElement;
const onNew = jest.fn(),
    onSelect = jest.fn(),
    onDeleted = jest.fn();
const item = { sessionId: "session-one", title: "A first conversation", createdAt: Date.now(), updatedAt: Date.now() };
const res = () => getRes().assistant;
const render = (props: any = {}) =>
    act(() =>
        root.render(
            <ConfigProvider theme={{ token: { motion: false } }}>
                <ConversationNavigation
                    selection={{ sessionId: item.sessionId }}
                    onNew={onNew}
                    onSelect={onSelect}
                    onDeleted={onDeleted}
                    {...props}
                >
                    {(toolbar) => (
                        <>
                            {toolbar}
                            <textarea aria-label="Composer" defaultValue="Unsent message" />
                        </>
                    )}
                </ConversationNavigation>
            </ConfigProvider>
        )
    );
const tick = () =>
    act(async () => {
        jest.advanceTimersByTime(300);
    });
const click = async (label: string) => {
    await act(async () => {
        const button = Array.from(document.querySelectorAll("button")).find(
            (b) =>
                b.getAttribute("aria-label") === label || b.textContent?.replace(/\s/g, "") === label.replace(/\s/g, "")
        );
        expect(button).toBeDefined();
        button!.click();
    });
};
beforeEach(() => {
    (globalThis as any).IS_REACT_ACT_ENVIRONMENT = true;
    jest.useFakeTimers();
    window.matchMedia = jest.fn(() => ({ matches: false, addListener: jest.fn(), removeListener: jest.fn() })) as any;
    global.ResizeObserver = class {
        observe = jest.fn();
        unobserve = jest.fn();
        disconnect = jest.fn();
    };
    window.localStorage.clear();
    mockGet.mockReset().mockResolvedValue({
        data: { error: 0, data: { items: [item], hasMore: true, before: item.updatedAt, beforeKey: "cursor-one" } },
    });
    mockPost.mockReset().mockResolvedValue({ data: { error: 0, data: true } });
    onNew.mockClear();
    onSelect.mockClear();
    onDeleted.mockClear();
    mockConfirm.mockClear();
    container = document.createElement("div");
    document.body.appendChild(container);
    root = createRoot(container);
});
afterEach(() => {
    act(() => root.unmount());
    container.remove();
    jest.useRealTimers();
    delete (globalThis as any).IS_REACT_ACT_ENVIRONMENT;
});
it("loads history on demand, marks selection and searches without reading messages", async () => {
    render();
    await tick();
    expect(mockGet).not.toHaveBeenCalled();
    await click(res().historyConversations);
    await tick();
    expect(mockGet).toHaveBeenCalledTimes(1);
    expect(mockGet.mock.calls[0][0]).toBe("/api/admin/assistant/chat/sessions");
    expect(container.querySelector('[aria-current="page"]')?.textContent).toContain(item.title);
    await click(res().moreConversations);
    await tick();
    expect(mockGet.mock.calls[1][1].params.beforeKey).toBe("cursor-one");
    await act(async () => Simulate.change(container.querySelector("input")!, { target: { value: "first" } } as any));
    await tick();
    expect(mockGet.mock.calls[mockGet.mock.calls.length - 1]?.[1].params).toMatchObject({ query: "first" });
    expect(mockGet.mock.calls[mockGet.mock.calls.length - 1]?.[1].params.before).toBeUndefined();
    await click(res().newConversation);
    expect(onNew).toHaveBeenCalledTimes(1);
});
it("keeps the composer mounted in compact history and restores focus after selecting", async () => {
    render({ compact: true, articleFilter: 7 });
    const composer = container.querySelector("textarea");
    await click(res().historyConversations);
    await tick();
    expect(mockGet.mock.calls[0][1].params.id).toBe(7);
    await click(item.title);
    await tick();
    expect(onSelect).toHaveBeenCalledWith(item);
    expect(container.querySelector("textarea")).toBe(composer);
    expect(container.querySelector("textarea")?.value).toBe("Unsent message");
    expect(document.activeElement?.getAttribute("aria-label")).toBe(res().historyConversations);
});
it("blocks switching during a run and displays a retryable list error without losing the composer", async () => {
    mockGet.mockRejectedValueOnce(new Error("Directory unavailable"));
    render({ disabled: true });
    await click(res().newConversation);
    expect(onNew).not.toHaveBeenCalled();
    await click(res().historyConversations);
    await tick();
    expect(container.textContent).toContain("Directory unavailable");
    await click(getRes().common.retry);
    await tick();
    await click(item.title);
    expect(onSelect).not.toHaveBeenCalled();
    expect(container.querySelector("textarea")?.value).toBe("Unsent message");
});
