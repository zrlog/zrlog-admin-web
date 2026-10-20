import {beforeEach, expect, jest, test} from "@jest/globals";
import {
    clearDockerUpgrade,
    DockerUpgradeJob,
    matchesDockerJob,
    readDockerUpgrade,
    saveDockerUpgrade
} from "./docker-upgrade";

jest.mock("./helpers", () => ({getContextPath: () => "/sub/"}));

const request = {
    requestId: "request-1234567890",
    version: "4.0.0-SNAPSHOT",
    buildId: "adminweb-1234567",
    channel: "preview" as const
};

beforeEach(() => localStorage.clear());

test("a refreshed page recovers the original request, including dependency preview IDs", () => {
    saveDockerUpgrade(request);
    expect(readDockerUpgrade()).toEqual(request);
    clearDockerUpgrade("another-request-1234");
    expect(readDockerUpgrade()).toEqual(request);
    clearDockerUpgrade(request.requestId);
    expect(readDockerUpgrade()).toBeUndefined();
});

test("corrupt browser storage never becomes an executable request", () => {
    localStorage.setItem("zrlog-docker-upgrade:/sub/", "not-json");
    expect(readDockerUpgrade()).toBeUndefined();
    localStorage.setItem("zrlog-docker-upgrade:/sub/", JSON.stringify({...request, requestId: "../../health"}));
    expect(readDockerUpgrade()).toBeUndefined();
});

test("success belongs to the persisted task and exact target", () => {
    const job: DockerUpgradeJob = {...request, id: request.requestId, phase: "succeeded", recoveryRequired: false};
    expect(matchesDockerJob(job, request)).toBe(true);
    expect(matchesDockerJob({...job, buildId: "wrong"}, request)).toBe(false);
    expect(matchesDockerJob({...job, id: "another-request-1234"}, request)).toBe(false);
    expect(matchesDockerJob({...job, version: "4.0.1"}, request)).toBe(false);
    expect(matchesDockerJob({...job, channel: "release"}, request)).toBe(false);
    expect(matchesDockerJob({...job, phase: "future-unknown" as DockerUpgradeJob["phase"]}, request)).toBe(false);
});
