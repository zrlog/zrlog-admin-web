import { act } from "react";
import { createRoot, Root } from "react-dom/client";
import { afterEach, beforeEach, expect, it, jest } from "@jest/globals";
import { App, ConfigProvider } from "antd";
import ArticleAiAssistantDrawer from "./article-ai-assistant-drawer";

jest.mock("@zrlog/editor/dist/ai/AIContentItem", () => ({ __esModule: true, default: () => null }));
jest.mock("@zrlog/editor/dist/ai/AIIcon", () => ({ __esModule: true, default: () => null }));
jest.mock("@zrlog/editor/dist/ai/AIStateCache", () => ({
    getAIStateCacheKey: (cache: { key: string }, name: string) => `${cache.key}/${name}`,
}));
jest.mock("../../../base/ConfigProviderApp", () => ({ getAppState: () => ({ dark: false }) }));
jest.mock("../../../utils/helpers", () => ({ getEditorUser: () => ({}) }));
jest.mock("../use-article-editor-screens", () => ({ __esModule: true, default: () => ({ sm: true }) }));
jest.mock("antd-style", () => ({
    useTheme: () => ({ marginXS: 8, lineWidth: 1, lineType: "solid", colorBorderSecondary: "#ddd" }),
}));
jest.mock("../../../utils/constants", () => ({
    getRes: () => ({
        websiteAi: { label: "AI assistant" },
        confirm: "Confirm",
        cancel: "Cancel",
        articleEdit: {
            assistant: {
                exportAiMessages: "Export conversation",
                clearAiMessages: "Clear conversation",
                conversationActions: "Conversation actions",
                clearAiMessagesConfirmTitle: "Clear this conversation?",
                clearAiMessagesConfirmDescription: "The saved conversation will be removed.",
            },
        },
    }),
}));

let root: Root;
let container: HTMLDivElement;
const fullscreenElementDescriptor = Object.getOwnPropertyDescriptor(document, "fullscreenElement");
const onExport = jest.fn(async () => undefined);
const onClear = jest.fn(async () => undefined);
const onClose = jest.fn();

beforeEach(() => {
    (globalThis as any).IS_REACT_ACT_ENVIRONMENT = true;
    window.matchMedia = jest.fn(() => ({ matches: false, addListener: jest.fn(), removeListener: jest.fn() })) as any;
    global.ResizeObserver = class {
        observe = jest.fn();
        unobserve = jest.fn();
        disconnect = jest.fn();
    };
    onExport.mockClear();
    onClear.mockClear();
    onClose.mockClear();
    container = document.createElement("div");
    container.style.overflow = "hidden";
    document.body.appendChild(container);
    Object.defineProperty(document, "fullscreenElement", { configurable: true, value: null });
    root = createRoot(container);
});
afterEach(() => {
    act(() => root.unmount());
    container.remove();
    if (fullscreenElementDescriptor) {
        Object.defineProperty(document, "fullscreenElement", fullscreenElementDescriptor);
    } else {
        Reflect.deleteProperty(document, "fullscreenElement");
    }
    delete (globalThis as any).IS_REACT_ACT_ENVIRONMENT;
});

const render = async (disabled = false) => {
    await act(async () =>
        root.render(
            <ConfigProvider theme={{ token: { motion: false } }}>
                <App>
                    <ArticleAiAssistantDrawer
                        open
                        onClose={onClose}
                        getContainer={() => container}
                        data={{ article: { title: "Article" } } as never}
                        stateCache={{ key: "test", read: () => undefined, write: jest.fn() }}
                        config={
                            {
                                messages: [],
                                renderMessage: () => null,
                                renderFooter: () => <textarea aria-label="Message" />,
                                contentMaxWidth: 768,
                                conversationActions: { disabled, exporting: false, clearing: false, onExport, onClear },
                            } as never
                        }
                    />
                </App>
            </ConfigProvider>
        )
    );
};
const click = async (element: Element | null) => {
    expect(element).not.toBeNull();
    await act(async () => element!.dispatchEvent(new MouseEvent("click", { bubbles: true })));
};
const byText = (selector: string, text: string) =>
    Array.from(document.querySelectorAll(selector)).find((element) => element.textContent?.trim() === text) || null;

it.each([false, true])("opens conversation actions and confirms clearing (fullscreen: %s)", async (fullscreen) => {
    if (fullscreen) {
        Object.defineProperty(document, "fullscreenElement", { configurable: true, value: container });
    }
    await render();
    const more = document.querySelector('button[aria-label="AI assistant"]');
    const close = document.querySelector(".ant-drawer-close");
    expect(document.querySelector(".ant-drawer-title")!.contains(more)).toBe(true);
    expect(more!.textContent).toContain("AI assistant");
    expect(document.querySelector(".ant-drawer-extra")).toBeNull();
    await click(more);
    expect(document.querySelectorAll('[role="menuitem"]')).toHaveLength(2);
    const menu = document.querySelector('[role="menu"]');
    expect(menu).not.toBeNull();
    expect(container.contains(menu)).toBe(fullscreen);
    expect(container.querySelector(".ant-drawer")).not.toBeNull();
    await click(byText('[role="menuitem"]', "Export conversation"));
    expect(onExport).toHaveBeenCalledTimes(1);
    expect(onClose).not.toHaveBeenCalled();
    await click(more);
    await click(byText('[role="menuitem"]', "Clear conversation"));
    expect(onClear).not.toHaveBeenCalled();
    expect(document.body.textContent).toContain("Clear this conversation?");
    expect(container.querySelector(".ant-modal")).not.toBeNull();
    await click(byText(".ant-modal button", "Confirm"));
    expect(onClear).toHaveBeenCalledTimes(1);
    expect(onClose).not.toHaveBeenCalled();
    await click(close);
    expect(onClose).toHaveBeenCalledTimes(1);
});

it("disables session operations while a response is running or the conversation is empty", async () => {
    await render(true);
    await click(document.querySelector('button[aria-label="AI assistant"]'));
    const items = document.querySelectorAll('[role="menuitem"]');
    expect(items).toHaveLength(2);
    items.forEach((item) => expect(item.getAttribute("aria-disabled")).toBe("true"));
    await click(items[0]);
    await click(items[1]);
    expect(onExport).not.toHaveBeenCalled();
    expect(onClear).not.toHaveBeenCalled();
});
