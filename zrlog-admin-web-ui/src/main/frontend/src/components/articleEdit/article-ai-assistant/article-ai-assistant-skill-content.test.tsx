import { act, ComponentProps } from "react";
import { createRoot, Root } from "react-dom/client";
import { Simulate } from "react-dom/test-utils";
import { afterEach, beforeEach, expect, it, jest } from "@jest/globals";
import { ConfigProvider, theme } from "antd";
import ArticleAiAssistantSkillContent from "./article-ai-assistant-skill-content";
import ArticleAiReasoning from "./article-ai-reasoning";
import { getRes } from "../../../utils/constants";

jest.mock("@zrlog/editor/dist/ai/AIIcon", () => ({ __esModule: true, default: () => null }));
jest.mock("@zrlog/editor/dist/editor/html-preview-panel", () => ({
    __esModule: true,
    default: ({ htmlContent }: { htmlContent: string }) => <div>{htmlContent}</div>,
}));
jest.mock("@zrlog/editor/dist/editor/utils/marked-utils", () => ({
    markdownToHtmlSyncWithCallback: (markdown: string) => markdown,
}));
jest.mock("../../../base/ConfigProviderApp", () => ({ getAppState: () => ({ dark: false }) }));
jest.mock("@zrlog/editor/dist/editor/lang/editor-lang", () => ({
    getEditorRes: () => ({ contentTips: "Check AI output" }),
}));

let root: Root;
let container: HTMLDivElement;
const submit = jest.fn(() => true);
const stop = jest.fn();
const remove = jest.fn();
const resume = jest.fn();
const res = () => getRes().articleEdit.assistant;

beforeEach(() => {
    (globalThis as any).IS_REACT_ACT_ENVIRONMENT = true;
    window.matchMedia = jest.fn(() => ({ matches: false, addListener: jest.fn(), removeListener: jest.fn() })) as any;
    global.ResizeObserver = class {
        observe = jest.fn();
        unobserve = jest.fn();
        disconnect = jest.fn();
    };
    submit.mockReset().mockReturnValue(true);
    stop.mockClear();
    remove.mockClear();
    resume.mockClear();
    container = document.createElement("div");
    document.body.appendChild(container);
    root = createRoot(container);
});
afterEach(() => {
    act(() => root.unmount());
    container.remove();
    delete (globalThis as any).IS_REACT_ACT_ENVIRONMENT;
});

const render = (props: Partial<ComponentProps<typeof ArticleAiAssistantSkillContent>> = {}) =>
    act(() =>
        root.render(
            <ConfigProvider theme={{ token: { motion: false } }}>
                <ArticleAiAssistantSkillContent
                    aiProvider="OPEN_AI"
                    disabled={false}
                    busy
                    waiting={false}
                    onStop={stop}
                    queuedMessages={[]}
                    queuePaused={false}
                    onRemoveQueued={remove}
                    onResumeQueue={resume}
                    theme={theme.getDesignToken()}
                    markdownLength={150}
                    onSubmit={submit}
                    {...props}
                />
            </ConfigProvider>
        )
    );
const input = () => container.querySelector("textarea")!;
const type = (value: string) =>
    act(() => {
        Simulate.change(input(), { target: { value } } as any);
    });
const enter = (options: { shiftKey?: boolean; composing?: boolean; keyCode?: number } = {}) =>
    act(() => {
        Simulate.keyDown(input(), {
            key: "Enter",
            keyCode: options.keyCode || 13,
            shiftKey: options.shiftKey,
            nativeEvent: { isComposing: options.composing },
        } as any);
    });
const click = (label: string) =>
    act(() => {
        const button = Array.from(container.querySelectorAll("button")).find(
            (b) => b.getAttribute("aria-label") === label || b.textContent === label
        );
        expect(button).toBeDefined();
        button!.click();
    });

it("keeps typing and queueing available during generation, with stop in the send position", () => {
    render();
    expect(input().disabled).toBe(false);
    type("Next question");
    click(getRes().articleEdit.knowledge.stop);
    expect(stop).toHaveBeenCalledTimes(1);
    expect(input().value).toBe("Next question");
    expect(submit).not.toHaveBeenCalled();
    enter();
    expect(submit).toHaveBeenCalledWith("Next question");
    expect(input().value).toBe("");
    type("Queued by pointer");
    click(res().addToQueue);
    expect(submit).toHaveBeenLastCalledWith("Queued by pointer");
    expect(container.querySelector('[role="status"]')).toBeNull();
});

it("preserves Chinese composition, Shift+Enter, rejected input and disabled drafts", () => {
    render();
    type("输入中的中文");
    enter({ composing: true });
    enter({ keyCode: 229 });
    enter({ shiftKey: true });
    expect(submit).not.toHaveBeenCalled();
    expect(input().value).toBe("输入中的中文");
    submit.mockReturnValue(false);
    enter();
    expect(input().value).toBe("输入中的中文");
    render({ disabled: true });
    submit.mockClear();
    enter();
    expect(submit).not.toHaveBeenCalled();
    render({ busy: false, onStop: undefined });
    submit.mockReturnValue(true);
    click(res().send);
    expect(submit).toHaveBeenCalledWith("输入中的中文");
});

it("exposes removal and explicit resume for paused queued messages", () => {
    render({
        busy: false,
        onStop: undefined,
        queuePaused: true,
        queuedMessages: [{ id: 1, input: "Keep this draft" }],
    });
    expect(container.textContent).toContain(res().queuePaused);
    expect(container.textContent).toContain("Keep this draft");
    click(res().removeQueued);
    expect(remove).toHaveBeenCalledWith(1);
    click(res().resumeQueue);
    expect(resume).toHaveBeenCalledTimes(1);
});

it("opens reasoning as it arrives after an initial status and even after answer text starts", () => {
    act(() => root.render(<ArticleAiReasoning status="Thinking" />));
    expect(container.querySelectorAll('[role="status"]')).toHaveLength(1);
    act(() => root.render(<ArticleAiReasoning status="Generating" content="Provider reasoning text" />));
    expect(container.textContent).toContain("Provider reasoning text");
    expect(container.querySelector('[aria-expanded="true"]')).not.toBeNull();
    expect(container.querySelectorAll('[role="status"]')).toHaveLength(1);
});
