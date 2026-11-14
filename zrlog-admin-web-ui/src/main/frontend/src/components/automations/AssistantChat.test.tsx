import { expect, it, jest } from "@jest/globals";
import { act } from "react";
import { createRoot } from "react-dom/client";
import AssistantChat from "./AssistantChat";

const mockGet = jest.fn<Promise<any>, any[]>();
jest.mock("../../base/AppBase", () => ({ useAxiosBaseInstance: () => ({ get: mockGet }) }));
jest.mock("../../utils/background-task-store", () => ({
    getBackgroundTasks: () => [],
    subscribeBackgroundTasks: () => () => undefined,
}));
jest.mock("../../utils/constants", () => ({
    getRealRouteUrl: (url: string) => url,
    getRes: () => ({
        common: { retry: "Retry" },
        error: { unknown: "Error" },
        assistant: { feedbackEmpty: "No updates" },
    }),
}));
jest.mock("./AssistantConversationActions", () => () => null);
jest.mock("../articleEdit/article-ai-assistant/article-ai-assistant-button", () => ({
    useArticleAiAssistantConfig: ({ data }: any) => ({ messages: data.aiMessages, conversationActions: {} }),
}));
jest.mock("../articleEdit/article-ai-assistant/assistant-conversation-view", () => () => (
    <textarea aria-label="Message" />
));
jest.mock("antd", () => ({
    Alert: ({ title, action }: any) => (
        <div role="alert">
            {title}
            {action}
        </div>
    ),
    Button: ({ children, onClick }: any) => <button onClick={onClick}>{children}</button>,
    Space: ({ children }: any) => <div>{children}</div>,
    Spin: () => <span>Loading</span>,
    Typography: { Paragraph: ({ children }: any) => <p>{children}</p> },
}));

it("keeps an unsent message when automatic synchronization fails and retries inline", async () => {
    (globalThis as any).IS_REACT_ACT_ENVIRONMENT = true;
    const container = document.createElement("div");
    const root = createRoot(container);
    const pending: { resolve: (data: unknown) => void; reject: (error: Error) => void }[] = [];
    const api = { get: mockGet };
    // A stable API instance matches the application's context provider.
    jest.spyOn(require("../../base/AppBase"), "useAxiosBaseInstance").mockReturnValue(api);
    mockGet.mockImplementation(() => new Promise((resolve, reject) => pending.push({ resolve, reject })));
    const info = { data: { error: 0, data: { aiConfigured: true, aiMessages: [] } } };
    try {
        await act(async () => root.render(<AssistantChat taskId="task-one" offline={false} />));
        await act(async () => pending[0].resolve(info));
        const input = container.querySelector("textarea")!;
        input.value = "Unsent question";
        await act(async () => root.render(<AssistantChat taskId="task-one" offline={false} revision={1} />));
        expect(mockGet.mock.calls[0][1].signal.aborted).toBe(true);
        expect(mockGet.mock.calls[0][1].showError).toBe(false);
        await act(async () => pending[1].reject(new Error("Synchronization failed")));
        expect(container.querySelector("textarea")).toBe(input);
        expect(input.value).toBe("Unsent question");
        expect(container.querySelector('[role="alert"]')?.textContent).toContain("Synchronization failed");
        await act(async () => container.querySelector("button")!.click());
        await act(async () => pending[2].resolve(info));
        expect(container.querySelector("textarea")).toBe(input);
        expect(input.value).toBe("Unsent question");
        expect(container.querySelector('[role="alert"]')).toBeNull();
    } finally {
        act(() => root.unmount());
        delete (globalThis as any).IS_REACT_ACT_ENVIRONMENT;
    }
});
