import { act } from "react";
import { createRoot, Root } from "react-dom/client";
import { MemoryRouter } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, jest } from "@jest/globals";
import { BackupProtectionStatus, UpgradeData } from "../type";
import UpgradeReadiness from "./upgrade-readiness";

jest.mock("@ant-design/icons", () => ({
    SafetyCertificateOutlined: () => null,
}));

jest.mock("antd", () => {
    const React = require("react") as typeof import("react");

    const Space = ({ children }: { children?: import("react").ReactNode }) =>
        React.createElement("div", null, children);
    const Tag = ({ children }: { children?: import("react").ReactNode }) => React.createElement("span", null, children);
    const Text = ({ children }: { children?: import("react").ReactNode }) =>
        React.createElement("span", null, children);
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
            type,
        }: {
            description?: import("react").ReactNode;
            message?: import("react").ReactNode;
            type?: string;
        }) => React.createElement("div", { "data-alert-type": type }, message, description),
        Descriptions,
        Space,
        Tag,
        Typography: { Text },
    };
});

jest.mock("antd-style", () => ({
    useTheme: () => ({ fontSizeSM: 12, marginLG: 24, marginSM: 12 }),
}));

jest.mock("../utils/constants", () => ({
    getRealRouteUrl: (path: string) => path,
    getRes: () => require("../i18n/admin").getAdminI18n("zh_CN"),
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

    const render = (data: UpgradeData) => {
        act(() => {
            root.render(
                <MemoryRouter future={{ v7_relativeSplatPath: true, v7_startTransition: true }}>
                    <UpgradeReadiness data={data} />
                </MemoryRouter>
            );
        });
    };

    beforeEach(() => {
        reactActEnvironment.IS_REACT_ACT_ENVIRONMENT = true;
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
});
