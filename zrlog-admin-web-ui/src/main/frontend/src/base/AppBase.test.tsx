import { beforeEach, expect, it, jest } from "@jest/globals";
import { act } from "react";
import { createRoot } from "react-dom/client";
import type { AxiosInstance } from "axios";
import { useAxiosBaseInstance } from "./AppBase";

const mockModal = { error: jest.fn<void, [options: any]>() };
const mockMessage = { error: jest.fn() };
const mockNavigate = jest.fn();
let mockLang: "zh_CN" | "en_US" = "en_US";
jest.mock("@zrlog/ui/feedback", () => ({ useUiApp: () => ({ modal: mockModal, message: mockMessage }) }));
jest.mock("react-router-dom", () => ({ useNavigate: () => mockNavigate }));
jest.mock("../components/admin-dashboard-page", () => ({ __esModule: true, default: () => null }));
jest.mock("../utils/constants", () => ({
    getRes: () => require("../i18n/admin").getAdminI18n(mockLang),
    isStaticPage: () => false,
    getBackendServerUrl: () => "http://localhost/",
    getRealRouteUrl: (url: string) => url,
}));
jest.mock("../utils/message-center-status", () => ({ syncMessageCenterStatus: require("@jest/globals").jest.fn() }));

let instance: AxiosInstance;
function Probe() {
    instance = useAxiosBaseInstance();
    return null;
}

beforeEach(() => {
    jest.clearAllMocks();
    Object.defineProperty(navigator, "onLine", { configurable: true, value: true });
    (globalThis as any).IS_REACT_ACT_ENVIRONMENT = true;
});

async function mount() {
    const element = document.createElement("div");
    const root = createRoot(element);
    await act(async () => root.render(<Probe />));
    return () => act(() => root.unmount());
}

it.each(["zh_CN", "en_US"] as const)("keeps HTTP and network diagnostics out of %s messages", async (lang) => {
    mockLang = lang;
    const unmount = await mount();
    try {
        instance.defaults.adapter = async (config) => {
            throw { config, response: { status: 500, data: "<html>private stack trace</html>" } };
        };
        await expect(instance.get("/private/path")).rejects.toBeDefined();
        const dialog = mockModal.error.mock.calls[0][0];
        expect(dialog.content).toBe(require("../i18n/admin").getAdminI18n(lang).error.requestFailed);
        expect(JSON.stringify(dialog)).not.toContain("private");
        Object.defineProperty(navigator, "onLine", { configurable: true, value: false });
        instance.defaults.adapter = async (config) => {
            throw { config, message: "Network Error: private" };
        };
        await expect(instance.get("/private/path")).rejects.toBeDefined();
        expect(mockMessage.error).toHaveBeenCalledWith(
            require("../i18n/admin").getAdminI18n(lang).error.networkOffline
        );
    } finally {
        unmount();
    }
});

it("keeps caller-handled failures out of global dialogs", async () => {
    const unmount = await mount();
    try {
        instance.defaults.adapter = async (config) => {
            throw { config, message: "private" };
        };
        await expect(instance.get("/api/admin/assistant", { showError: false } as any)).rejects.toBeDefined();
        expect(mockModal.error).not.toHaveBeenCalled();
    } finally {
        unmount();
    }
});

it("uses a sign-in action for an expired session", async () => {
    mockLang = "en_US";
    const unmount = await mount();
    try {
        instance.defaults.adapter = async (config) => ({
            config,
            data: { error: 9001, message: "private backend text" },
            status: 200,
            statusText: "OK",
            headers: {},
        });
        await expect(instance.get("/api/admin/assistant")).rejects.toBeDefined();
        const dialog = mockModal.error.mock.calls[0][0];
        expect(dialog.title).toBe("Session expired");
        expect(dialog.okText).toBe("Sign in again");
        expect(JSON.stringify(dialog)).not.toContain("private backend text");
        dialog.onOk();
        expect(mockNavigate).toHaveBeenCalled();
    } finally {
        unmount();
    }
});
