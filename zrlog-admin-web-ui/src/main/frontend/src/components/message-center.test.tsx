import { act } from "react";
import { createRoot } from "react-dom/client";
import { MemoryRouter } from "react-router-dom";
import { Grid } from "antd";
import { afterEach, beforeEach, expect, it, jest } from "@jest/globals";
import MessageCenter from "./message-center";
import { useAssistantNotices } from "../layout/use-assistant-notices";
import { getRes } from "../utils/constants";
import { getMessageCenterStatus } from "../utils/message-center-status";

const mockApi = {
    get: jest.fn<Promise<any>, any[]>(),
    post: jest.fn<Promise<any>, any[]>(),
};
jest.mock("../base/AppBase", () => ({ useAxiosBaseInstance: () => mockApi }));
jest.mock("../base/ConfigProviderApp", () => ({ getAppState: () => ({ colorPrimary: "#123456" }) }));
jest.mock("antd-style", () => ({ useTheme: () => ({}) }));
jest.mock("@zrlog/editor/dist/editor/TimeAgo", () => () => null);

function Inbox() {
    const { refresh, loading } = useAssistantNotices("test", true);
    return <MessageCenter compact loading={loading} onRefresh={refresh} />;
}

beforeEach(() => {
    jest.useFakeTimers();
    (globalThis as any).IS_REACT_ACT_ENVIRONMENT = true;
    global.ResizeObserver = class {
        observe = jest.fn();
        unobserve = jest.fn();
        disconnect = jest.fn();
    };
    const computedStyle = window.getComputedStyle;
    jest.spyOn(window, "getComputedStyle").mockImplementation((element) => computedStyle(element));
    window.matchMedia = (() => ({ matches: false, addListener: jest.fn(), removeListener: jest.fn() })) as any;
    window.__SS_DATA__ = {
        key: "test",
        user: { key: "test", userId: 1, header: "", userName: "reader" },
        systemNotification: "",
        pageBuildId: "",
    };
    mockApi.get.mockReset().mockResolvedValue({
        data: {
            error: 0,
            data: [
                {
                    taskKey: "server.assistant.example",
                    type: "operationTask",
                    status: "notice",
                    updatedAt: 1000,
                    payload: { title: "Test notice", closable: true },
                },
            ],
        },
    });
    mockApi.post.mockReset().mockResolvedValue({ data: { error: 0, data: true, hasUnread: true } });
});

afterEach(() => {
    jest.useRealTimers();
    jest.restoreAllMocks();
    delete window.__SS_DATA__;
});

it.each([false, true])("loads details only on open and explicit refresh (mobile=%s)", async (mobile) => {
    jest.spyOn(Grid, "useBreakpoint").mockReturnValue({ xs: mobile });
    const host = document.body.appendChild(document.createElement("div"));
    const root = createRoot(host);
    const idle = () =>
        act(() => {
            jest.advanceTimersByTime(120000);
            document.dispatchEvent(new Event("visibilitychange"));
            window.dispatchEvent(new Event("focus"));
        });
    const clickButton = async (label: string) => {
        const button = Array.from(document.querySelectorAll("button")).find(
            (node) => node.getAttribute("aria-label") === label || node.textContent === label
        );
        expect(button).toBeDefined();
        await act(async () => button!.click());
    };
    try {
        await act(async () =>
            root.render(
                <MemoryRouter>
                    <Inbox />
                </MemoryRouter>
            )
        );
        idle();
        expect(mockApi.get).toHaveBeenCalledTimes(0);
        await clickButton(getRes().backgroundTask.title);
        expect(mockApi.get.mock.calls.map(([path]) => path)).toEqual(["/api/admin/assistant/notices"]);
        idle();
        expect(mockApi.get).toHaveBeenCalledTimes(1);
        await clickButton(getRes().common.refresh);
        expect(mockApi.get).toHaveBeenCalledTimes(2);
        await clickButton(getRes().common.close);
        expect(mockApi.post.mock.calls[0][0]).toBe("/api/admin/assistant/readNotice");
        expect(getMessageCenterStatus().assistantUnreadUserId).toBe(1);
        expect(mockApi.get).toHaveBeenCalledTimes(2);
    } finally {
        act(() => root.unmount());
        host.remove();
    }
});
