import { act } from "react";
import { createRoot, Root } from "react-dom/client";
import { afterEach, beforeEach, describe, expect, it, jest } from "@jest/globals";
import { UpgradeData } from "../type";
import Upgrade, { UpgradeProps } from "./upgrade";

const mockGet = jest.fn<Promise<any>, [string, unknown]>();
const mockUpgrade = jest.fn<Promise<any>, [string, unknown]>();
const mockMessage = { error: jest.fn() };
const mockModal = { success: jest.fn(), warning: jest.fn() };
const mockAxios = { get: mockGet, post: jest.fn() };

jest.mock("../base/AppBase", () => ({ useAxiosBaseInstance: () => mockAxios }));
jest.mock("../base/ConfigProviderApp", () => ({ getAppState: () => ({ dark: false }) }));
jest.mock("../utils/constants", () => ({
    getRes: () => require("../i18n/admin").getAdminI18n("en_US"),
    getRealRouteUrl: (path: string) => path,
}));
jest.mock("../utils/helpers", () => ({ getContextPath: () => "/blog/" }));
jest.mock("../api", () => ({
    API_DO_UPGRADE_PATH: "/api/admin/upgrade/doUpgrade",
    API_ADMIN_STATIC_SITE_SYNC_PATH: "/api/admin/static-site/sync",
    getVersion: require("@jest/globals").jest.fn(),
}));
jest.mock("../utils/sse-utils", () => ({
    postRefreshCacheSse: (path: string, options: unknown) => mockUpgrade(path, options),
}));
jest.mock("../utils/background-task-store", () => ({
    createBackgroundTask: () => "test-upgrade-task",
    updateBackgroundTask: require("@jest/globals").jest.fn(),
    finishBackgroundTask: require("@jest/globals").jest.fn(),
}));
jest.mock("@editor/dist/editor/utils/marked-utils", () => ({ markdownToHtml: async (text: string) => text }));
jest.mock("@editor/dist/editor/html-preview-panel", () => () => null);
jest.mock("./upgrade-content", () => ({
    __esModule: true,
    default: ({ data }: { data: UpgradeData }) =>
        require("react").createElement("div", { "data-version": data.version.buildId }),
}));
jest.mock("./upgrade-readiness", () => ({
    __esModule: true,
    default: ({ data, onRefresh, refreshing, refreshDisabled, refreshError, checkedAt }: any) => {
        const React = require("react");
        return React.createElement(
            "section",
            { "data-backup": data.backupProtection?.status, "data-checked-at": checkedAt },
            React.createElement(
                "button",
                { onClick: onRefresh, disabled: refreshing || refreshDisabled },
                "Check again"
            ),
            refreshError && React.createElement("p", { role: "alert" }, refreshError)
        );
    },
}));
jest.mock("@ant-design/icons", () => ({
    CheckCircleOutlined: () => null,
    CloseCircleOutlined: () => null,
    InfoCircleOutlined: () => null,
    LoadingOutlined: () => null,
}));
jest.mock("antd/es/typography/Title", () => ({
    __esModule: true,
    default: ({ children }: any) => require("react").createElement("h2", null, children),
}));
jest.mock("antd", () => {
    const React = require("react");
    const Container = ({ children }: any) => React.createElement("div", null, children);
    return {
        App: { useApp: () => ({ modal: mockModal }) },
        message: { useMessage: () => [mockMessage, null] },
        theme: { useToken: () => ({ token: {} }) },
        Grid: { useBreakpoint: () => ({ xs: false, sm: true, md: true }) },
        Row: Container,
        Col: Container,
        Card: Container,
        Steps: () => null,
        Alert: ({ message, description }: any) => React.createElement("div", null, message, description),
        Button: ({ children, onClick, disabled, loading }: any) =>
            React.createElement("button", { onClick, disabled: disabled || loading }, children),
        Checkbox: ({ children, checked, disabled, onChange }: any) =>
            React.createElement(
                "label",
                null,
                React.createElement("input", { type: "checkbox", checked, disabled, onChange }),
                children
            ),
    };
});

const reactActEnvironment = globalThis as typeof globalThis & { IS_REACT_ACT_ENVIRONMENT?: boolean };
const initialData = (): UpgradeData => ({
    upgrade: true,
    onlineUpgradable: true,
    disableUpgradeReason: "",
    version: { buildId: "old", version: "4.0.0", type: "standard", changeLog: "" },
    backupProtection: {
        ready: false,
        requiresRiskAcceptance: true,
        status: "MISSING_BACKUP",
        backupMaxAgeMillis: 0,
        verificationMaxAgeMillis: 0,
    },
});
const refreshedData = (): UpgradeData => ({
    ...initialData(),
    version: { ...initialData().version, buildId: "new" },
    backupProtection: { ...initialData().backupProtection!, status: "READY", ready: true },
});
const deferred = () => {
    let resolve!: (value: unknown) => void;
    let reject!: (error: Error) => void;
    const promise = new Promise((onResolve, onReject) => {
        resolve = onResolve;
        reject = onReject;
    });
    return { promise, resolve, reject };
};

describe("upgrade readiness actions", () => {
    let root: Root;
    let container: HTMLDivElement;
    let props: UpgradeProps;
    const render = async (changes: Partial<UpgradeProps> = {}) => {
        props = { ...props, ...changes };
        await act(async () => root.render(<Upgrade {...props} />));
    };
    const button = (label: string) => {
        const element = Array.from(container.querySelectorAll("button")).find((item) => item.textContent === label);
        expect(element).toBeDefined();
        return element!;
    };
    const checkbox = () => container.querySelector('input[type="checkbox"]') as HTMLInputElement;
    const click = async (element: HTMLElement) => {
        await act(async () => element.click());
    };
    const check = () => button("Check again");
    const execute = () => button("Update Now");

    beforeEach(() => {
        reactActEnvironment.IS_REACT_ACT_ENVIRONMENT = true;
        mockGet.mockReset();
        mockUpgrade.mockReset();
        props = { data: initialData(), offline: false, offlineData: false };
        container = document.createElement("div");
        document.body.appendChild(container);
        root = createRoot(container);
    });
    afterEach(() => {
        act(() => root.unmount());
        container.remove();
        jest.restoreAllMocks();
        reactActEnvironment.IS_REACT_ACT_ENVIRONMENT = false;
    });

    it("refreshes evidence without upgrading and requires renewed risk acceptance", async () => {
        const pending = deferred();
        mockGet.mockReturnValue(pending.promise);
        jest.spyOn(Date, "now").mockReturnValue(1788829200000);
        await render();
        expect(container.querySelector("[data-checked-at]")).toBeNull();
        await click(checkbox());
        expect(execute().disabled).toBe(false);
        await click(check());
        await click(check());
        expect(mockGet).toHaveBeenCalledTimes(1);
        expect(mockGet).toHaveBeenCalledWith("/api/admin/upgrade", { showError: false, timeout: 30000 });
        expect(checkbox().checked).toBe(false);
        expect(checkbox().disabled).toBe(true);
        expect(execute().disabled).toBe(true);
        expect(mockUpgrade).not.toHaveBeenCalled();

        await act(async () => pending.resolve({ data: { error: 0, data: refreshedData() } }));
        expect(container.querySelector("[data-backup]")?.getAttribute("data-backup")).toBe("READY");
        expect(container.querySelector("[data-version]")?.getAttribute("data-version")).toBe("new");
        expect(container.querySelector("[data-checked-at]")?.getAttribute("data-checked-at")).toBe("1788829200000");
        expect(execute().disabled).toBe(true);
        expect(mockUpgrade).not.toHaveBeenCalled();
        await click(checkbox());
        mockUpgrade.mockResolvedValue({ error: 0, data: { finish: false, message: "" } });
        await click(execute());
        expect(mockUpgrade).toHaveBeenCalledTimes(1);
        expect(mockUpgrade).toHaveBeenCalledWith(
            "/api/admin/upgrade/doUpgrade",
            expect.objectContaining({ body: { upgradeRiskAccepted: true } })
        );
        expect(container.querySelector("[data-backup]")).toBeNull();
    });

    it.each(["business", "transport", "invalid"])(
        "keeps old evidence on %s failure and supports retry",
        async (failure) => {
            await render();
            if (failure === "transport") {
                mockGet.mockRejectedValueOnce(new Error("Temporary connection failure"));
            } else {
                mockGet.mockResolvedValueOnce({ data: { error: failure === "business" ? 1 : 0, data: {} } });
            }
            await click(check());
            expect(container.querySelector("[data-backup]")?.getAttribute("data-backup")).toBe("MISSING_BACKUP");
            expect(container.querySelector("[data-checked-at]")).toBeNull();
            expect(container.querySelector('[role="alert"]')).not.toBeNull();
            expect(checkbox().disabled).toBe(true);
            expect(execute().disabled).toBe(true);
            expect(check().disabled).toBe(false);
            mockGet.mockResolvedValueOnce({ data: { error: 0, data: refreshedData() } });
            await click(check());
            expect(container.querySelector('[role="alert"]')).toBeNull();
            expect(container.querySelector("[data-backup]")?.getAttribute("data-backup")).toBe("READY");
            expect(mockUpgrade).not.toHaveBeenCalled();
        }
    );

    it("allows cached page data to be rechecked online, but not while offline", async () => {
        await render({ offlineData: true, offline: true });
        expect(check().disabled).toBe(true);
        await click(check());
        expect(mockGet).not.toHaveBeenCalled();
        await render({ offline: false });
        expect(check().disabled).toBe(false);
        mockGet.mockResolvedValueOnce({ data: { error: 0, data: refreshedData() } });
        await click(check());
        await click(checkbox());
        expect(execute().disabled).toBe(false);
    });

    it.each(["offline", "new-page-data", "unmount"])("ignores a late result after %s", async (transition) => {
        const pending = deferred();
        mockGet.mockReturnValueOnce(pending.promise);
        await render();
        await click(check());
        if (transition === "unmount") {
            await act(async () => root.render(null));
        } else if (transition === "offline") {
            await render({ offline: true });
        } else {
            await render({ data: { ...initialData(), version: { ...initialData().version, buildId: "route-data" } } });
        }
        await act(async () => pending.resolve({ data: { error: 0, data: refreshedData() } }));
        expect(container.querySelector("[data-checked-at]")).toBeNull();
        expect(container.querySelector('[data-version="new"]')).toBeNull();
        expect(mockUpgrade).not.toHaveBeenCalled();
    });
});
