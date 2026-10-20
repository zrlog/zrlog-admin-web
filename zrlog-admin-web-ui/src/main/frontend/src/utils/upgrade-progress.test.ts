import {describe, expect, it} from "@jest/globals";
import {
    getUpgradeStepProgress,
    mergeUpgradeProgress,
    UpgradeProgressEvent,
    UpgradeProgressItem
} from "./upgrade-progress";

const apply = (items: UpgradeProgressItem[], stage: string, status: UpgradeProgressEvent["status"] = "running") =>
    mergeUpgradeProgress(items, {stage, status, message: stage}, stage);

describe("shared upgrade progress", () => {
    it("settles serial provider stages while keeping version verification independent", () => {
        let items = apply([], "download");
        items = apply(items, "restart");
        expect(items.map((item) => item.status)).toEqual(["complete", "running"]);
        items = apply(items, "restart-check");
        expect(items.map((item) => item.status)).toEqual(["complete", "running", "running"]);
        expect(getUpgradeStepProgress(items).percent).toBe(0);
        items = apply(items, "complete", "complete");
        expect(items.find((item) => item.stage === "restart-check")?.status).toBe("running");
        expect(getUpgradeStepProgress(items).percent).toBe(33);
    });

    it("settles a disconnected provider only after verifying the target version", () => {
        let items = apply(apply([], "download"), "restart-check");
        items = apply(items, "restart-check", "complete");
        expect(items.every((item) => item.status === "complete")).toBe(true);
        expect(getUpgradeStepProgress(items)).toEqual({completed: 2, total: 3, percent: 67});
        items = apply(items, "admin-static-sync");
        expect(getUpgradeStepProgress(items).percent).toBe(67);
        items = apply(items, "admin-static-sync", "complete");
        expect(getUpgradeStepProgress(items).percent).toBe(100);
    });

    it.each(["error", "manual"] as const)("stops pending stages on %s without changing completed work", (status) => {
        let items = apply(apply([], "download", "complete"), "restart");
        items = apply(items, status, status);
        expect(items.map((item) => item.status)).toEqual(["complete", status, status]);
        expect(getUpgradeStepProgress(items).percent).toBe(0);
    });

    it("does not reopen a completed stage for delayed running events", () => {
        const items = apply([], "download", "complete");
        expect(apply(items, "download")).toBe(items);
    });
});
