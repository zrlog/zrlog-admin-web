import {beforeEach, expect, jest, test} from "@jest/globals";
import {
    clearUpgradeTask,
    UpgradeTask,
    matchesUpgradeTask,
    readUpgradeTask,
    saveUpgradeTask
} from "./upgrade-task";

jest.mock("./helpers", () => ({getContextPath: () => "/sub/"}));

const request = {
    requestId: "request-1234567890",
    version: "4.0.0-SNAPSHOT",
    buildId: "adminweb-1234567",
    channel: "preview" as const
};

beforeEach(() => localStorage.clear());

test("a refreshed page recovers the original request, including dependency preview IDs", () => {
    saveUpgradeTask(request);
    expect(readUpgradeTask()).toEqual(request);
    clearUpgradeTask("another-request-1234");
    expect(readUpgradeTask()).toEqual(request);
    clearUpgradeTask(request.requestId);
    expect(readUpgradeTask()).toBeUndefined();
});

test("corrupt browser storage never becomes an executable request", () => {
    localStorage.setItem("zrlog-docker-upgrade:/sub/", "not-json");
    expect(readUpgradeTask()).toBeUndefined();
    localStorage.setItem("zrlog-docker-upgrade:/sub/", JSON.stringify({...request, requestId: "../../health"}));
    expect(readUpgradeTask()).toBeUndefined();
});

test("success belongs to the persisted task and exact target", () => {
    const job: UpgradeTask = {...request, id: request.requestId, state: "succeeded", stage: "complete", recoveryRequired: false};
    expect(matchesUpgradeTask(job, request)).toBe(true);
    expect(matchesUpgradeTask({...job, buildId: "wrong"}, request)).toBe(false);
    expect(matchesUpgradeTask({...job, id: "another-request-1234"}, request)).toBe(false);
    expect(matchesUpgradeTask({...job, version: "4.0.1"}, request)).toBe(false);
    expect(matchesUpgradeTask({...job, channel: "release"}, request)).toBe(false);
    expect(matchesUpgradeTask({...job, state: "future-unknown" as UpgradeTask["state"]}, request)).toBe(false);
});
