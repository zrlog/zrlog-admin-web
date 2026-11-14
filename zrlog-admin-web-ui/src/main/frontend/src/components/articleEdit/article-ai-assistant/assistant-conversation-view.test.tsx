import { it, expect, jest } from "@jest/globals";
import { act } from "react";
import { createRoot } from "react-dom/client";
import AssistantConversationView from "./assistant-conversation-view";

jest.mock("@zrlog/editor/core/ai/AIContentItem", () => ({ __esModule: true, default: () => null }));
jest.mock("../../../base/ConfigProviderApp", () => ({ getAppState: () => ({ dark: false }) }));
jest.mock("../../../utils/helpers", () => ({ getEditorUser: () => ({}) }));
jest.mock("antd-style", () => ({ useTheme: () => ({}) }));

it("interleaves successive executions in the same conversation without duplicating their data", () => {
    (globalThis as any).IS_REACT_ACT_ENVIRONMENT = true;
    const container = document.createElement("div");
    const root = createRoot(container);
    const footer = jest.fn(() => <textarea aria-label="Message" />);
    const config = {
        contentMaxWidth: 768,
        messages: [
            { content: "Question", createdAt: 20 },
            { content: "Answer", createdAt: 40 },
        ],
        renderMessage: ({ content }: any) => <p data-activity>{content.content}</p>,
        renderFooter: footer,
    };
    try {
        act(() =>
            root.render(
                <AssistantConversationView
                    config={config as never}
                    aiProvider={"OPEN_AI" as never}
                    selectedText="Selected text"
                    activities={[
                        { id: "run-1", createdAt: 10, content: <p data-activity>First execution</p> },
                        { id: "run-2", createdAt: 30, content: <p data-activity>Second execution</p> },
                    ]}
                />
            )
        );
        expect(Array.from(container.querySelectorAll("[data-activity]")).map((node) => node.textContent)).toEqual([
            "First execution",
            "Question",
            "Second execution",
            "Answer",
        ]);
        expect(footer).toHaveBeenCalledWith("Selected text");
        expect(container.querySelectorAll("textarea")).toHaveLength(1);
    } finally {
        act(() => root.unmount());
        delete (globalThis as any).IS_REACT_ACT_ENVIRONMENT;
    }
});
