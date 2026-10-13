import { act } from "react";
import { createRoot, Root } from "react-dom/client";
import { afterEach, beforeEach, describe, expect, it, jest } from "@jest/globals";
import { UpgradeData } from "../type";
import Upgrade, { UpgradeProps } from "./upgrade";
import { getVersion } from "../api";
import { DockerUpgradeJob, DockerUpgradeRequest, readDockerUpgrade, saveDockerUpgrade } from "../utils/docker-upgrade";

const mockGet = jest.fn<Promise<any>, [string, unknown]>();
const mockUpgrade = jest.fn<Promise<any>, [string, unknown]>();
const mockSubscribe = jest.fn<Promise<any>, [string, any]>();
const mockPost = jest.fn<Promise<any>, [string, any, unknown?]>();
const mockMessage = { error: jest.fn() };
const mockModal = { success: jest.fn(), warning: jest.fn() };
const mockAxios = { get: mockGet, post: mockPost };

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
    getRefreshCacheSse: (path: string, options: unknown) => mockSubscribe(path, options),
    postRefreshCacheSse: (path: string, options: unknown) => mockUpgrade(path, options),
}));
jest.mock("../utils/background-task-store", () => ({
    createBackgroundTask: () => "test-upgrade-task",
    updateBackgroundTask: require("@jest/globals").jest.fn(),
    finishBackgroundTask: require("@jest/globals").jest.fn(),
}));
jest.mock("@zrlog/editor/dist/editor/utils/marked-utils", () => ({ markdownToHtml: async (text: string) => text }));
jest.mock("@zrlog/editor/core/editor/html-preview-panel", () => () => null);
jest.mock("./upgrade-content", () => ({
    __esModule: true,
    default: ({ data }: { data: UpgradeData }) =>
        require("react").createElement("div", { "data-version": data.version?.buildId }),
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
                "Refresh Check"
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
    version: { ...initialData().version!, buildId: "new" },
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
    const check = () => button("Refresh Check");
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
        expect(mockGet).toHaveBeenCalledWith("/api/admin/upgrade", {
            params: { fetch: true },
            showError: false,
            timeout: 30000,
        });
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

    it("shows only the result and recheck action when no update is available", async () => {
        await render({ data: { ...initialData(), upgrade: false } });
        expect(container.querySelector("[data-backup]")).toBeNull();
        expect(checkbox()).toBeNull();
        expect(container.textContent).not.toContain("Update Now");
        expect(check().disabled).toBe(false);

        mockGet.mockResolvedValueOnce({ data: { error: 0, data: refreshedData() } });
        await click(check());
        expect(container.querySelector("[data-backup]")).not.toBeNull();
        expect(checkbox().checked).toBe(false);
        expect(execute().disabled).toBe(true);
        expect(mockUpgrade).not.toHaveBeenCalled();
    });

    it("hides upgrade warnings and actions when a recheck finds no update", async () => {
        await render();
        await click(checkbox());
        mockGet.mockResolvedValueOnce({ data: { error: 0, data: { ...refreshedData(), upgrade: false } } });
        await click(check());
        expect(container.querySelector("[data-backup]")).toBeNull();
        expect(checkbox()).toBeNull();
        expect(container.textContent).not.toContain("Update Now");
        expect(mockUpgrade).not.toHaveBeenCalled();
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
            await render({ data: { ...initialData(), version: { ...initialData().version!, buildId: "route-data" } } });
        }
        await act(async () => pending.resolve({ data: { error: 0, data: refreshedData() } }));
        expect(container.querySelector("[data-checked-at]")).toBeNull();
        expect(container.querySelector('[data-version="new"]')).toBeNull();
        expect(mockUpgrade).not.toHaveBeenCalled();
    });
});

describe("Docker upgrade interaction and recovery", () => {
    let root: Root;
    let container: HTMLDivElement;
    const originalCrypto = Object.getOwnPropertyDescriptor(globalThis, "crypto");
    const request: DockerUpgradeRequest = {
        requestId: "docker-request-1234567890",
        version: "4.0.0-SNAPSHOT",
        buildId: "adminweb-1234567",
        channel: "preview",
    };
    const page = (): UpgradeData => ({
        ...initialData(),
        dockerMode: true,
        dockerUpdaterAvailable: true,
        version: {
            ...initialData().version!,
            buildId: request.buildId,
            artifactVersion: request.version,
            channel: request.channel,
        },
    });
    const job = (phase: DockerUpgradeJob["phase"], changes: Partial<DockerUpgradeJob> = {}) => ({
        error: 0,
        data: { ...request, id: request.requestId, phase, recoveryRequired: false, ...changes },
    });
    const render = async (changes: Partial<UpgradeProps> = {}) => {
        await act(async () => root.render(<Upgrade data={page()} offline={false} offlineData={false} {...changes} />));
    };
    const click = async (label: string) => {
        const element = Array.from(container.querySelectorAll("button")).find((item) => item.textContent === label);
        expect(element).toBeDefined();
        await act(async () => element!.click());
    };
    const tick = async () => {
        await act(async () => {
            jest.advanceTimersByTime(3000);
        });
    };

    beforeEach(() => {
        reactActEnvironment.IS_REACT_ACT_ENVIRONMENT = true;
        jest.useFakeTimers();
        jest.clearAllMocks();
        mockGet.mockReset();
        mockPost.mockReset();
        mockSubscribe.mockReset();
        mockUpgrade.mockReset();
        jest.mocked(getVersion).mockReset();
        Object.defineProperty(globalThis, "crypto", { configurable: true, value: require("crypto").webcrypto });
        localStorage.clear();
        container = document.createElement("div");
        document.body.appendChild(container);
        root = createRoot(container);
    });
    afterEach(() => {
        act(() => root.unmount());
        container.remove();
        localStorage.clear();
        jest.restoreAllMocks();
        jest.useRealTimers();
        if (originalCrypto) {
            Object.defineProperty(globalThis, "crypto", originalCrypto);
        } else {
            Reflect.deleteProperty(globalThis, "crypto");
        }
        reactActEnvironment.IS_REACT_ACT_ENVIRONMENT = false;
    });

    it("saves before the SSE submission and uses the common restart check after a lost stream", async () => {
        let savedAtSubmission: DockerUpgradeRequest | undefined;
        mockUpgrade.mockImplementation(async () => {
            savedAtSubmission = readDockerUpgrade();
            throw new Error("response lost during replacement");
        });
        jest.mocked(getVersion).mockResolvedValue({ data: { buildId: "old-build" } } as any);
        await render();
        await act(async () => (container.querySelector('input[type="checkbox"]') as HTMLInputElement).click());
        await click("Update Now");
        const saved = readDockerUpgrade();
        expect(savedAtSubmission).toEqual(saved);
        expect(saved).toEqual({ ...request, requestId: expect.stringMatching(/^[0-9a-f]{32}$/) });
        expect(mockUpgrade).toHaveBeenCalledWith(
            "/api/admin/upgrade/doUpgrade",
            expect.objectContaining({
                body: { upgradeRiskAccepted: true, docker: saved },
                waitForComplete: true,
            })
        );
        await tick();
        expect(getVersion).toHaveBeenCalledTimes(2);
        expect(mockUpgrade).toHaveBeenCalledTimes(1);
        expect(mockGet).not.toHaveBeenCalled();
        expect(mockSubscribe).not.toHaveBeenCalled();
        expect(mockModal.success).not.toHaveBeenCalled();
    });

    it.each([true, false])("recovers using one SSE subscription, with version metadata=%s", async (metadata) => {
        saveDockerUpgrade(request);
        mockSubscribe.mockResolvedValue({ error: 0, data: { finish: true, message: "" } });
        jest.mocked(getVersion).mockResolvedValueOnce({ data: { buildId: "old-build" } } as any);
        jest.mocked(getVersion).mockResolvedValueOnce({ data: { buildId: request.buildId } } as any);
        mockUpgrade.mockResolvedValue({ error: 0, data: { synced: true } });
        mockPost.mockResolvedValue({ data: { error: 0 } });
        await render({ data: { ...page(), upgrade: false, version: metadata ? page().version : undefined } });
        expect(mockSubscribe).toHaveBeenCalledWith(
            "/api/admin/upgrade/docker-status?requestId=" + request.requestId,
            expect.objectContaining({ waitForComplete: true, responseEvents: ["response"] })
        );
        expect(mockModal.success).not.toHaveBeenCalled();
        expect(readDockerUpgrade()).toEqual(request);
        await tick();
        expect(mockUpgrade).toHaveBeenCalledWith("/api/admin/static-site/sync", expect.anything());
        expect(mockModal.success).toHaveBeenCalledTimes(1);
        expect(readDockerUpgrade()).toBeUndefined();
        await tick();
        expect(mockSubscribe).toHaveBeenCalledTimes(1);
        expect(mockGet).not.toHaveBeenCalled();
    });

    it.each(["failed", "interrupted"] as const)("renders the standard terminal error for %s", async (phase) => {
        saveDockerUpgrade(request);
        mockSubscribe.mockImplementation(async (_path, options) => {
            options.onEvent({
                event: "upgrade-progress",
                data: {
                    stage: "error",
                    status: "error",
                    message: phase,
                    detail: "Inspect the updater task before recovery",
                },
            });
            return { error: 1, message: phase, data: { finish: false } };
        });
        await render();
        expect(readDockerUpgrade()).toBeUndefined();
        expect(container.textContent).toContain("Inspect the updater task before recovery");
        expect(getVersion).not.toHaveBeenCalled();
        expect(mockModal.success).not.toHaveBeenCalled();
        await tick();
        expect(mockSubscribe).toHaveBeenCalledTimes(1);
    });

    it("does not mistake a terminal error followed by disconnect for replacement", async () => {
        saveDockerUpgrade(request);
        mockSubscribe.mockImplementation(async (_path, options) => {
            options.onEvent({
                event: "upgrade-progress",
                data: { stage: "error", status: "error", message: "failed" },
            });
            throw new Error("connection closed");
        });
        await render();
        expect(getVersion).not.toHaveBeenCalled();
        expect(readDockerUpgrade()).toBeUndefined();
    });

    it.each([null, undefined])("retries the same missing task only on an explicit click (%s)", async (data) => {
        saveDockerUpgrade(request);
        mockSubscribe.mockResolvedValue({ error: 0, data });
        mockUpgrade.mockReturnValue(deferred().promise);
        await render();
        await tick();
        expect(mockUpgrade).not.toHaveBeenCalled();
        expect(mockSubscribe).toHaveBeenCalledTimes(1);
        await click("Retry the same task");
        expect(mockUpgrade).toHaveBeenCalledTimes(1);
        expect(mockUpgrade).toHaveBeenCalledWith(
            "/api/admin/upgrade/doUpgrade",
            expect.objectContaining({
                body: { upgradeRiskAccepted: true, docker: request },
            })
        );
        expect(readDockerUpgrade()).toEqual(request);
    });

    it("recovers a rejected submission by ID without another POST", async () => {
        mockUpgrade.mockResolvedValue({ error: 1, message: "Rejected" });
        mockSubscribe.mockResolvedValue({ error: 0 });
        await render();
        await act(async () => (container.querySelector('input[type="checkbox"]') as HTMLInputElement).click());
        await click("Update Now");
        await tick();
        expect(mockUpgrade).toHaveBeenCalledTimes(1);
        expect(mockSubscribe).toHaveBeenCalledTimes(1);
        expect(readDockerUpgrade()).toBeDefined();
        expect(container.textContent).toContain("Retry the same task");
        expect(getVersion).not.toHaveBeenCalled();
    });

    it("retains the request and rejects a mismatched JSON fallback", async () => {
        saveDockerUpgrade(request);
        mockSubscribe.mockResolvedValue(job("succeeded", { id: "another-request-1234567890" }));
        await render();
        expect(readDockerUpgrade()).toEqual(request);
        expect(getVersion).not.toHaveBeenCalled();
        expect(mockModal.success).not.toHaveBeenCalled();
    });

    it.each(["failed", "interrupted"] as const)("rejects %s in the JSON fallback", async (phase) => {
        saveDockerUpgrade(request);
        mockSubscribe.mockResolvedValue(job(phase, { recoveryRequired: true }));
        await render();
        expect(readDockerUpgrade()).toBeUndefined();
        expect(getVersion).not.toHaveBeenCalled();
        expect(container.textContent).toContain("recovery");
    });

    it.each(["offline", "unmount"])("aborts the stream and ignores late events after %s", async (transition) => {
        saveDockerUpgrade(request);
        const pending = deferred();
        mockSubscribe.mockReturnValueOnce(pending.promise);
        await render();
        const options = mockSubscribe.mock.calls[0][1];
        if (transition === "offline") {
            await render({ offline: true });
        } else {
            await act(async () => root.render(null));
        }
        expect(options.signal.aborted).toBe(true);
        await act(async () => {
            options.onEvent({ event: "upgrade-progress", data: { stage: "error", status: "error", message: "late" } });
            pending.resolve({ error: 0, data: { finish: true } });
        });
        await tick();
        expect(getVersion).not.toHaveBeenCalled();
        expect(readDockerUpgrade()).toEqual(request);
    });

    it("opens one new subscription on reconnect and ignores the previous stream", async () => {
        saveDockerUpgrade(request);
        const previous = deferred();
        mockSubscribe.mockReturnValueOnce(previous.promise);
        mockSubscribe.mockReturnValue(deferred().promise);
        await render();
        await render({ offline: true });
        await render();
        await act(async () => previous.resolve({ error: 1, message: "old rejection" }));
        await tick();
        expect(mockSubscribe).toHaveBeenCalledTimes(2);
        expect(readDockerUpgrade()).toEqual(request);
        expect(mockUpgrade).not.toHaveBeenCalled();
        expect(mockMessage.error).not.toHaveBeenCalled();
    });
});
