import { describe, expect, it } from "@jest/globals";
import { UpgradeData } from "../type";
import { resolveBackupProtectionStatus, resolveUpgradeRuntimeModes } from "./upgrade-readiness-utils";

const data = (overrides: Partial<UpgradeData> = {}): UpgradeData => ({
    upgrade: true,
    onlineUpgradable: true,
    disableUpgradeReason: "",
    version: { buildId: "400", changeLog: "", type: "standard", version: "4.0.0" },
    dockerMode: false,
    faasMode: false,
    systemServiceMode: false,
    warMode: false,
    nativeImageMode: false,
    ...overrides,
});

describe("upgrade readiness", () => {
    it("reports deployment and execution runtime modes", () => {
        expect(resolveUpgradeRuntimeModes(data())).toEqual(["standalone", "jvm"]);
        expect(resolveUpgradeRuntimeModes(data({ dockerMode: true, nativeImageMode: true }))).toEqual([
            "docker",
            "native",
        ]);
        expect(resolveUpgradeRuntimeModes(data({ faasMode: true }))).toEqual(["faas", "jvm"]);
        expect(resolveUpgradeRuntimeModes(data({ systemServiceMode: true }))).toEqual(["systemService", "jvm"]);
        expect(resolveUpgradeRuntimeModes(data({ warMode: true }))).toEqual(["war", "jvm"]);
    });

    it("does not guess runtime modes omitted by a legacy response", () => {
        expect(
            resolveUpgradeRuntimeModes(
                data({
                    dockerMode: undefined,
                    faasMode: undefined,
                    systemServiceMode: undefined,
                    warMode: undefined,
                    nativeImageMode: undefined,
                })
            )
        ).toEqual(["unknown"]);
        expect(resolveUpgradeRuntimeModes(data({ nativeImageMode: undefined }))).toEqual(["standalone", "unknown"]);
    });

    it("falls back safely for unknown or inconsistent backup status values", () => {
        expect(resolveBackupProtectionStatus({ ready: true, status: "READY" })).toBe("READY");
        expect(resolveBackupProtectionStatus({ ready: false, status: "MISSING_BACKUP" })).toBe("MISSING_BACKUP");
        expect(resolveBackupProtectionStatus({ ready: true, status: "MISSING_BACKUP" })).toBe("INVALID_EVIDENCE");
        expect(resolveBackupProtectionStatus({ ready: false, status: "READY" })).toBe("INVALID_EVIDENCE");
        expect(resolveBackupProtectionStatus({ ready: false, status: "future-status" })).toBe("INVALID_EVIDENCE");
        expect(resolveBackupProtectionStatus()).toBe("INVALID_EVIDENCE");
    });
});
