import {getContextPath} from "./helpers";

export type UpgradeTaskRequest = {
    requestId: string;
    version: string;
    buildId: string;
    channel: "release" | "preview";
};

export const upgradeTaskStates = ["queued", "running", "succeeded", "failed", "interrupted"] as const;
export type UpgradeTaskState = typeof upgradeTaskStates[number];
export type UpgradeTask = {
    id: string;
    version: string;
    buildId: string;
    channel: string;
    state: UpgradeTaskState;
    stage: string;
    awaitingRestart?: boolean;
    recoveryRequired: boolean;
    message?: string;
    detail?: string;
};

const storageKey = () => `zrlog-upgrade-task:${getContextPath()}`;

export function readUpgradeTask(): UpgradeTaskRequest | undefined {
    try {
        const value = JSON.parse(localStorage.getItem(storageKey()) || localStorage.getItem(`zrlog-docker-upgrade:${getContextPath()}`) || "null") as UpgradeTaskRequest | null;
        if (value && /^[A-Za-z0-9][A-Za-z0-9_-]{15,79}$/.test(value.requestId)
            && typeof value.version === "string" && /^[A-Za-z0-9][A-Za-z0-9._-]{0,127}$/.test(value.buildId)
            && (value.channel === "release" || value.channel === "preview")) {
            return value;
        }
    } catch { /* Unavailable storage is reported before submission. */
    }
}

export function saveUpgradeTask(value: UpgradeTaskRequest) {
    localStorage.setItem(storageKey(), JSON.stringify(value));
}

export function clearUpgradeTask(requestId: string) {
    if (readUpgradeTask()?.requestId === requestId) {
        localStorage.removeItem(storageKey());
        localStorage.removeItem(`zrlog-docker-upgrade:${getContextPath()}`);
    }
}

export function newUpgradeRequestId() {
    return Array.from(crypto.getRandomValues(new Uint8Array(16)), value => value.toString(16).padStart(2, "0")).join("");
}

export function matchesUpgradeTask(job: UpgradeTask | null | undefined, request: UpgradeTaskRequest): job is UpgradeTask {
    return !!job && job.id === request.requestId && job.version === request.version && job.buildId === request.buildId
        && job.channel === request.channel && upgradeTaskStates.includes(job.state) && typeof job.stage === "string";
}
