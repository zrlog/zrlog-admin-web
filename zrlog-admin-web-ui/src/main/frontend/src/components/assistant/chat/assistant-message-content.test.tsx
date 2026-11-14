import { act } from "react";
import { createRoot, Root } from "react-dom/client";
import { afterEach, beforeEach, expect, it, jest } from "@jest/globals";
import { markdownToHtmlSyncWithCallback } from "@zrlog/editor/dist/editor/utils/marked-utils";
import AssistantMessageContent from "./assistant-message-content";

jest.mock("@zrlog/editor/core/editor/html-preview-panel", () => ({
    __esModule: true,
    default: ({ htmlContent }: { htmlContent: string }) => <div data-preview>{htmlContent}</div>,
}));
jest.mock("@zrlog/editor/dist/editor/utils/marked-utils", () => ({
    markdownToHtmlSyncWithCallback: require("@jest/globals").jest.fn(),
}));
jest.mock("../../../base/ConfigProviderApp", () => ({ getAppState: () => ({ dark: false }) }));
jest.mock("../../../utils/helpers", () => ({ getEditorUser: () => ({ nickname: "You" }) }));
jest.mock("antd-style", () => ({ useTheme: () => require("antd").theme.getDesignToken() }));

let container: HTMLDivElement;
let root: Root;
const renderMarkdown = jest.mocked(markdownToHtmlSyncWithCallback);
beforeEach(() => {
    (globalThis as any).IS_REACT_ACT_ENVIRONMENT = true;
    container = document.createElement("div");
    root = createRoot(container);
    renderMarkdown.mockReset().mockImplementation((value) => `Rendered: ${value}`);
});
afterEach(() => {
    act(() => root.unmount());
    delete (globalThis as any).IS_REACT_ACT_ENVIRONMENT;
});

it("keeps user prompts literal, including line breaks and HTML examples", () => {
    const content = "第一行\n<strong>保留这个示例</strong>";
    act(() => root.render(<AssistantMessageContent content={{ role: "user", content, thinking: false }} />));
    expect(container.textContent).toBe(content);
    expect(container.querySelector("strong")).toBeNull();
    expect(renderMarkdown).not.toHaveBeenCalled();
});

it("shows streamed Markdown immediately and ignores late rendering from an older response", () => {
    const render = (content: string) =>
        act(() => root.render(<AssistantMessageContent content={{ role: "assistant", content, thinking: false }} />));
    render("First fragment");
    expect(container.querySelector("[data-preview]")?.textContent).toBe("Rendered: First fragment");
    const oldCallback = renderMarkdown.mock.calls[0][1];
    render("Complete answer");
    expect(container.querySelector("[data-preview]")?.textContent).toBe("Rendered: Complete answer");
    act(() => oldCallback("Outdated fragment"));
    expect(container.querySelector("[data-preview]")?.textContent).toBe("Rendered: Complete answer");
    act(() => renderMarkdown.mock.calls[1][1]("Complete answer with rendered diagram"));
    expect(container.querySelector("[data-preview]")?.textContent).toBe("Complete answer with rendered diagram");
});
