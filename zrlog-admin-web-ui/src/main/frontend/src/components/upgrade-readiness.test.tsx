import { act, ComponentProps } from "react";
import { createRoot, Root } from "react-dom/client";
import { MemoryRouter, useLocation } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, jest } from "@jest/globals";
import { BackupProtectionStatus, UpgradeData } from "../type";
import UpgradeReadiness from "./upgrade-readiness";
import { getAdminI18n } from "../i18n/admin";

let mockSsData: {
    pageBuildId: string;
    resourceInfo: { lang: "zh_CN" | "en_US"; staticPage: boolean };
};

jest.mock("@ant-design/icons", () => ({
    DatabaseOutlined: () => null,
    ReloadOutlined: () => null,
    SafetyCertificateOutlined: () => null,
}));

jest.mock("antd", () => {
    const React = require("react") as typeof import("react");

    const Space = ({ children }: { children?: import("react").ReactNode }) =>
        React.createElement("div", null, children);
    const Tag = ({ children }: { children?: import("react").ReactNode }) => React.createElement("span", null, children);
    const Text = ({
        children,
        role,
        tabIndex,
        "aria-disabled": ariaDisabled,
    }: import("react").HTMLAttributes<HTMLSpanElement>) =>
        React.createElement("span", { role, tabIndex, "aria-disabled": ariaDisabled }, children);
    const Descriptions = ({ children }: { children?: import("react").ReactNode }) =>
        React.createElement("dl", null, children);
    Descriptions.Item = ({
        children,
        label,
    }: {
        children?: import("react").ReactNode;
        label?: import("react").ReactNode;
    }) =>
        React.createElement(
            "div",
            null,
            React.createElement("dt", null, label),
            React.createElement("dd", null, children)
        );

    return {
        Alert: ({
            description,
            message,
            role,
            type,
        }: {
            description?: import("react").ReactNode;
            message?: import("react").ReactNode;
            role?: string;
            type?: string;
        }) => React.createElement("div", { "data-alert-type": type, role }, message, description),
        Button: ({ children, icon, loading, disabled, onClick, "aria-label": ariaLabel }: import("antd").ButtonProps) =>
            React.createElement(
                "button",
                { disabled, onClick, "aria-label": ariaLabel, type: "button", "data-loading": String(loading) },
                icon,
                children
            ),
        Descriptions,
        Space,
        Tag,
        Tooltip: ({ children, title }: { children?: import("react").ReactNode; title?: string }) =>
            React.createElement("span", { "data-tooltip": title }, children),
        Typography: { Text, Paragraph: Text },
    };
});

jest.mock("antd-style", () => ({
    useTheme: () => ({ fontSizeSM: 12, marginLG: 24, marginSM: 12, marginXS: 8 }),
}));

jest.mock("../utils/helpers", () => ({
    getContextPath: () => new URL(globalThis.document.baseURI).pathname,
}));

jest.mock("../base/SsData", () => ({
    getSsDate: () => mockSsData,
}));

const reactActEnvironment = globalThis as typeof globalThis & {
    IS_REACT_ACT_ENVIRONMENT?: boolean;
};

const readyBackup: BackupProtectionStatus = {
    ready: true,
    requiresRiskAcceptance: false,
    status: "READY",
    lastBackupAt: Date.UTC(2026, 8, 5, 1, 0),
    lastBackupFile: "backup-20260905.sql",
    lastBackupSha256: "a".repeat(64),
    lastVerifiedAt: Date.UTC(2026, 8, 5, 1, 5),
    lastVerifiedFile: "backup-20260905.sql",
    lastVerifiedSha256: "a".repeat(64),
    verificationSuccess: true,
    verificationMessage: "SQLite isolated restore completed",
    backupMaxAgeMillis: 36 * 60 * 60 * 1000,
    verificationMaxAgeMillis: 8 * 24 * 60 * 60 * 1000,
};

const upgradeData = (overrides: Partial<UpgradeData> = {}): UpgradeData => ({
    upgrade: true,
    onlineUpgradable: true,
    disableUpgradeReason: "",
    version: { buildId: "400", changeLog: "", type: "standard", version: "4.0.0" },
    dockerMode: false,
    faasMode: false,
    systemServiceMode: false,
    warMode: false,
    nativeImageMode: false,
    backupProtection: readyBackup,
    ...overrides,
});

describe("UpgradeReadiness", () => {
    let container: HTMLDivElement;
    let root: Root;
    const originalNodeEnv = process.env.NODE_ENV;
    const res = getAdminI18n("zh_CN").upgrade.maintenance;
    const Location = () => {
        const location = useLocation();
        return <span data-router-location={location.pathname + location.search} />;
    };

    const render = (
        data: UpgradeData,
        props: Omit<ComponentProps<typeof UpgradeReadiness>, "data"> = {},
        basename = "/"
    ) => {
        act(() => {
            root.render(
                <MemoryRouter
                    basename={basename}
                    initialEntries={[basename === "/" ? "/upgrade" : basename + "/upgrade"]}
                    future={{ v7_relativeSplatPath: true, v7_startTransition: true }}
                >
                    <UpgradeReadiness data={data} {...props} />
                    <Location />
                </MemoryRouter>
            );
        });
    };

    beforeEach(() => {
        reactActEnvironment.IS_REACT_ACT_ENVIRONMENT = true;
        (process.env as Record<string, string | undefined>).NODE_ENV = "production";
        mockSsData = { pageBuildId: "", resourceInfo: { lang: "zh_CN", staticPage: false } };
        container = document.createElement("div");
        document.body.appendChild(container);
        root = createRoot(container);
    });

    afterEach(() => {
        act(() => {
            root.unmount();
        });
        container.remove();
        reactActEnvironment.IS_REACT_ACT_ENVIRONMENT = false;
        (process.env as Record<string, string | undefined>).NODE_ENV = originalNodeEnv;
    });

    it("shows runtime, update method, and complete backup and restore evidence", () => {
        render(upgradeData());

        const text = container.textContent || "";
        expect(text).toContain("当前运行环境");
        expect(text).toContain("独立运行包");
        expect(text).toContain("JVM");
        expect(text).toContain("在线更新");
        expect(text).toContain("最近备份已通过恢复验证");
        expect(text).toContain("backup-20260905.sql");
        expect(text).toContain("a".repeat(64));
        expect(text).toContain("验证结果");
        expect(text).toContain("通过");
        expect(text).toContain("SQLite isolated restore completed");
        expect(text).toContain("保留该备份文件和校验值");
        expect(container.querySelector('a[href="/system"]')).not.toBeNull();
        expect(container.querySelector("time")).toBeNull();
        expect(container.textContent).not.toContain(res.lastChecked);
        expect(container.querySelector<HTMLButtonElement>("button")?.disabled).toBe(true);
    });

    it("makes missing evidence and unreported runtime explicit", () => {
        render({
            upgrade: true,
            onlineUpgradable: false,
            disableUpgradeReason: "manual update",
            version: { buildId: "400", changeLog: "", type: "standard", version: "4.0.0" },
        });

        const text = container.textContent || "";
        expect(text).toContain("未报告");
        expect(text).toContain("按部署方式手动更新");
        expect(text).toContain("备份保护记录格式无效");
        expect(text).toContain("暂无记录");
        expect(text).toContain("重新创建备份并完成恢复验证");
    });

    it("shows a failed restore result and its recorded detail", () => {
        render(
            upgradeData({
                backupProtection: {
                    ...readyBackup,
                    ready: false,
                    status: "VERIFICATION_FAILED",
                    verificationSuccess: false,
                    verificationMessage: "restore exited with code 1",
                },
            })
        );

        const text = container.textContent || "";
        expect(text).toContain("最近一次恢复验证失败");
        expect(text).toContain("失败");
        expect(text).toContain("restore exited with code 1");
        expect(text).toContain("停止更新，排查恢复失败原因后重新备份并验证");
    });

    it("refreshes only when the user requests a check", () => {
        const onRefresh = jest.fn();
        render(upgradeData(), { onRefresh });

        const refresh = container.querySelector<HTMLButtonElement>(`button[aria-label="${res.refresh}"]`);
        expect(refresh?.disabled).toBe(false);
        expect(container.querySelector(`[data-tooltip="${res.refresh}"]`)).not.toBeNull();
        expect(onRefresh).not.toHaveBeenCalled();
        act(() => refresh?.click());
        expect(onRefresh).toHaveBeenCalledTimes(1);
    });

    it.each(["refreshing", "refreshDisabled", "offline"] as const)("blocks refresh while %s", (disabledState) => {
        const onRefresh = jest.fn();
        render(upgradeData(), { onRefresh, [disabledState]: true });

        const refresh = container.querySelector<HTMLButtonElement>(`button[aria-label="${res.refresh}"]`);
        expect(refresh?.disabled).toBe(true);
        expect(refresh?.getAttribute("data-loading")).toBe(String(disabledState === "refreshing"));
        act(() => refresh?.click());
        expect(onRefresh).not.toHaveBeenCalled();
    });

    it("retains the previous evidence and successful-check time on failure, and permits retry", () => {
        const onRefresh = jest.fn();
        const checkedAt = Date.UTC(2026, 8, 8, 1, 0);
        render(upgradeData(), { onRefresh, checkedAt, refreshError: res.refreshFailed });

        expect(container.querySelector('[role="alert"]')?.textContent).toBe(res.refreshFailed);
        expect(container.textContent).toContain("backup-20260905.sql");
        expect(container.textContent).toContain("最近备份已通过恢复验证");
        expect(container.querySelector("time")?.dateTime).toBe(new Date(checkedAt).toISOString());
        act(() => container.querySelector<HTMLButtonElement>(`button[aria-label="${res.refresh}"]`)?.click());
        expect(onRefresh).toHaveBeenCalledTimes(1);

        const refreshedAt = checkedAt + 60_000;
        render(upgradeData(), { onRefresh, checkedAt: refreshedAt });
        expect(container.querySelector('[role="alert"]')).toBeNull();
        expect(container.querySelector("time")?.dateTime).toBe(new Date(refreshedAt).toISOString());
    });

    it.each([undefined, NaN, Infinity, 1e20])("does not invent a successful-check time for %s", (checkedAt) => {
        render(upgradeData(), { checkedAt, refreshError: res.refreshFailed });

        expect(container.querySelector("time")).toBeNull();
        expect(container.textContent).not.toContain(res.lastChecked);
        expect(container.textContent).toContain("backup-20260905.sql");
    });

    it.each([
        { basename: "/admin", staticPage: false, expectedPath: "/admin/plugin" },
        { basename: "/blog/admin", staticPage: false, expectedPath: "/blog/admin/plugin" },
        { basename: "/blog/admin", staticPage: true, expectedPath: "/blog/admin/plugin.html" },
    ])(
        "preserves the backup-management route with $basename and staticPage=$staticPage",
        ({ basename, staticPage, expectedPath }) => {
            mockSsData.pageBuildId = "400-test";
            mockSsData.resourceInfo.staticPage = staticPage;
            render(upgradeData(), {}, basename);

            const management = Array.from(container.querySelectorAll("a")).find((link) =>
                link.textContent?.includes(res.manageBackups)
            );
            expect(management).toBeDefined();
            const url = new URL(management!.href);
            expect(url.pathname).toBe(expectedPath);
            expect(url.searchParams.get("page")).toBe("backup-sql-file/files");
            expect(url.searchParams.get("v")).toBe("400-test");

            act(() => management!.click());
            const route = new URL(
                container.querySelector("[data-router-location]")!.getAttribute("data-router-location")!,
                window.location.origin
            );
            expect(route.pathname).toBe(staticPage ? "/plugin.html" : "/plugin");
            expect(route.searchParams.get("page")).toBe("backup-sql-file/files");
            expect(route.searchParams.get("v")).toBe("400-test");
        }
    );

    it("keeps backup management non-navigable offline", () => {
        render(upgradeData(), { offline: true }, "/blog/admin");

        expect(
            Array.from(container.querySelectorAll("a")).some((link) => link.textContent?.includes(res.manageBackups))
        ).toBe(false);
        const management = container.querySelector<HTMLElement>('[role="link"][aria-disabled="true"]');
        expect(management?.textContent).toContain(res.manageBackups);
        expect(management?.getAttribute("href")).toBeNull();
        expect(management?.tabIndex).toBe(-1);
        act(() => management?.click());
        expect(container.querySelector("[data-router-location]")?.getAttribute("data-router-location")).toBe(
            "/upgrade"
        );
        expect(container.querySelector(`[data-tooltip="${res.offlineUnavailable}"]`)).not.toBeNull();
    });

    it("uses English for maintenance actions, timestamps, and failed-check status", () => {
        mockSsData.resourceInfo.lang = "en_US";
        const english = getAdminI18n("en_US").upgrade.maintenance;
        render(upgradeData(), { checkedAt: Date.UTC(2026, 8, 8), refreshError: english.refreshFailed });

        expect(container.querySelector(`button[aria-label="${english.refresh}"]`)).not.toBeNull();
        expect(container.textContent).toContain(english.manageBackups);
        expect(container.textContent).toContain(english.lastChecked);
        expect(container.querySelector('[role="alert"]')?.textContent).toBe(english.refreshFailed);
    });
});
