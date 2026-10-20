export type UpgradeProgressEvent = {
    stage?: string;
    status?: "running" | "complete" | "error" | "manual";
    message?: string;
    detail?: string;
};

export type UpgradeProgressItem = UpgradeProgressEvent & { key: string };

const isRunning = (item: UpgradeProgressItem) => !item.status || item.status === "running";
const isLocalStage = (stage?: string) => stage === "restart-check" || stage === "admin-static-sync";

/** All deployment types use serial provider stages followed by version verification and resource sync. */
export function mergeUpgradeProgress(items: UpgradeProgressItem[], event: UpgradeProgressEvent, key: string) {
    const previous = items.find((item) => item.key === key);
    // A delayed progress callback must not reopen an already completed stage.
    if (previous?.status === "complete" && (!event.status || event.status === "running")) return items;
    const failed = event.status === "error";
    const manual = event.status === "manual";
    const verified = event.stage === "restart-check" && event.status === "complete";
    const providerAdvanced = !isLocalStage(event.stage) && !failed && !manual;
    const next = items.map((item): UpgradeProgressItem => {
        if (item.key === key || !isRunning(item)) return item;
        if (failed || manual) return {...item, status: event.status};
        // Starting a version check after a disconnected stream proves nothing about installation.
        if (verified || (providerAdvanced && !isLocalStage(item.stage))) return {...item, status: "complete"};
        return item;
    });
    const item = {...event, key};
    const index = next.findIndex((entry) => entry.key === key);
    if (index < 0) next.push(item);
    else next[index] = item;
    return next;
}

/** Percentage of three confirmed steps, not an estimate of bytes or remaining time. */
export function getUpgradeStepProgress(items: UpgradeProgressItem[]) {
    const completed = (stage: string) => items.some((item) => item.stage === stage && item.status === "complete");
    const steps = completed("admin-static-sync") ? 3 : completed("restart-check") ? 2 : completed("complete") ? 1 : 0;
    return {completed: steps, total: 3, percent: Math.round((steps / 3) * 100)};
}
