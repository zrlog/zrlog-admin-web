import {getContextPath} from "./helpers";

export type DockerUpgradeRequest = {
    requestId: string;
    version: string;
    buildId: string;
    channel: "release" | "preview";
};

export const dockerPhases = ["queued", "pulling", "replacing", "checking", "succeeded", "failed", "interrupted"] as const;
export type DockerPhase = typeof dockerPhases[number];
export type DockerUpgradeJob = {
    id: string;
    version: string;
    buildId: string;
    channel: string;
    phase: DockerPhase;
    recoveryRequired: boolean;
};

const storageKey = () => `zrlog-docker-upgrade:${getContextPath()}`;

export function readDockerUpgrade(): DockerUpgradeRequest | undefined {
    try {
        const value = JSON.parse(localStorage.getItem(storageKey()) || "null") as DockerUpgradeRequest | null;
        if (value && /^[A-Za-z0-9][A-Za-z0-9_-]{15,79}$/.test(value.requestId)
            && typeof value.version === "string" && /^[A-Za-z0-9][A-Za-z0-9._-]{0,127}$/.test(value.buildId)
            && (value.channel === "release" || value.channel === "preview")) {
            return value;
        }
    } catch { /* Unavailable storage is reported before submission. */
    }
}

export function saveDockerUpgrade(value: DockerUpgradeRequest) {
    localStorage.setItem(storageKey(), JSON.stringify(value));
}

export function clearDockerUpgrade(requestId: string) {
    if (readDockerUpgrade()?.requestId === requestId) {
        localStorage.removeItem(storageKey());
    }
}

export function newDockerRequestId() {
    return Array.from(crypto.getRandomValues(new Uint8Array(16)), value => value.toString(16).padStart(2, "0")).join("");
}

export function matchesDockerJob(job: DockerUpgradeJob | null | undefined, request: DockerUpgradeRequest): job is DockerUpgradeJob {
    return !!job && job.id === request.requestId && job.version === request.version && job.buildId === request.buildId
        && job.channel === request.channel && dockerPhases.includes(job.phase);
}
