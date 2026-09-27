import { BackupProtectionStatus, UpgradeData } from "../type";

export type UpgradeRuntimeMode =
    | "docker"
    | "faas"
    | "systemService"
    | "war"
    | "standalone"
    | "native"
    | "jvm"
    | "unknown";

export type BackupProtectionStatusKey = BackupProtectionStatus["status"];

const BACKUP_STATUS_KEYS = new Set<BackupProtectionStatusKey>([
    "READY",
    "MISSING_BACKUP",
    "BACKUP_STALE",
    "MISSING_VERIFICATION",
    "VERIFICATION_FAILED",
    "VERIFICATION_STALE",
    "BACKUP_CHANGED_AFTER_VERIFICATION",
    "INVALID_EVIDENCE",
]);

export const resolveUpgradeRuntimeModes = (data: UpgradeData): UpgradeRuntimeMode[] => {
    const modes: UpgradeRuntimeMode[] = [];
    const deploymentModeReported = [data.dockerMode, data.faasMode, data.systemServiceMode, data.warMode].some(
        (value) => typeof value === "boolean"
    );
    if (data.dockerMode) {
        modes.push("docker");
    } else if (data.faasMode) {
        modes.push("faas");
    } else if (data.systemServiceMode) {
        modes.push("systemService");
    } else if (data.warMode) {
        modes.push("war");
    } else if (deploymentModeReported) {
        modes.push("standalone");
    } else {
        modes.push("unknown");
    }
    if (typeof data.nativeImageMode === "boolean") {
        modes.push(data.nativeImageMode ? "native" : "jvm");
    } else if (!modes.includes("unknown")) {
        modes.push("unknown");
    }
    return modes;
};

export const resolveBackupProtectionStatus = (status?: {
    ready?: boolean;
    status?: string;
}): BackupProtectionStatusKey => {
    if (!status?.status || !BACKUP_STATUS_KEYS.has(status.status as BackupProtectionStatusKey)) {
        return "INVALID_EVIDENCE";
    }
    const statusKey = status.status as BackupProtectionStatusKey;
    return status.ready === (statusKey === "READY") ? statusKey : "INVALID_EVIDENCE";
};
