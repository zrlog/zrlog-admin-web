import { it, expect, jest } from "@jest/globals";
import { act } from "react";
import { createRoot } from "react-dom/client";
import AssistantConversationView from "./assistant-conversation-view";

jest.mock("./assistant-message-content", () => ({ __esModule: true, default: () => null }));
jest.mock("../../../base/ConfigProviderApp", () => ({ getAppState: () => ({ dark: false }) }));
jest.mock("antd-style", () => ({ useTheme: () => ({}) }));

it("renders stored task feedback and chat through the same message renderer", () => {
    (globalThis as any).IS_REACT_ACT_ENVIRONMENT = true;
    const container = document.createElement("div");
    const root = createRoot(container);
    const footer = jest.fn(() => <textarea aria-label="Message" />);
    const config = {
        contentMaxWidth: 768,
        messages: [
            { content: "First execution", createdAt: 10, messageType: "taskResult" },
            { content: "Question", createdAt: 20 },
            { content: "Second execution", createdAt: 30, messageType: "taskResult" },
            { content: "Answer", createdAt: 40 },
        ],
        renderMessage: ({ content }: any) => <p data-activity>{content.content}</p>,
        renderFooter: footer,
    };
    try {
        act(() => root.render(<AssistantConversationView config={config as never} selectedText="Selected text" />));
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
