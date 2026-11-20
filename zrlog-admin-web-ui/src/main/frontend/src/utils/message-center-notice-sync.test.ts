import { beforeEach, expect, it, jest } from "@jest/globals";
import type { MessageCenterNotice } from "../type";
import { createBackgroundTask, getBackgroundTasks, removeBackgroundTask } from "./background-task-store";
import { removeNotice, syncMessageCenterNotices } from "./message-center-notice-sync";

jest.mock("./constants", () => ({ getRes: () => ({ backgroundTask: { updatedAt: "Updated" } }) }));

const notice = (taskKey: string, metadata: Partial<MessageCenterNotice> = {}): MessageCenterNotice => ({
    taskKey,
    type: "operationTask",
    status: "notice",
    updatedAt: 1000,
    payload: { title: "Operation" },
    ...metadata,
});

beforeEach(() => {
    getBackgroundTasks().forEach(({ id }) => removeBackgroundTask(id));
});

it("tracks notice scopes independently of their task keys", () => {
    const local = createBackgroundTask("Local task");
    syncMessageCenterNotices([notice("opaque-site-key", { scope: "site", source: "operation" })], "site");
    syncMessageCenterNotices([
        notice("opaque-user-key", { scope: "account", source: "assistant", referenceId: "run-id" }),
    ], "account");
    const account = getBackgroundTasks().find((task) => task.taskKey === "opaque-user-key");
    expect(account).toMatchObject({
        noticeScope: "account",
        dismissPath: "/api/admin/assistant/readNotice",
        dismissPayload: { taskKey: "opaque-user-key", source: "assistant", referenceId: "run-id" },
    });
    expect(localStorage.getItem("zrlog-admin-background-tasks-v1")).not.toContain("opaque-user-key");
    syncMessageCenterNotices([], "account");
    expect(getBackgroundTasks()).toEqual(expect.arrayContaining([
        expect.objectContaining({ id: local }),
        expect.objectContaining({ taskKey: "opaque-site-key", noticeScope: "site" }),
    ]));
    expect(getBackgroundTasks().some((task) => task.taskKey === "opaque-user-key")).toBe(false);
});

it("supports older notice responses and removes an assistant notice using its run ID", () => {
    syncMessageCenterNotices([notice("server.assistant.legacy-run")], "account");
    expect(getBackgroundTasks()[0]).toMatchObject({
        noticeScope: "account",
        noticeSource: "assistant",
        noticeReferenceId: "legacy-run",
    });
    removeNotice("assistant", "legacy-run");
    expect(getBackgroundTasks()).toEqual([]);
});

it("keeps unrelated notices when two scopes use the same key", () => {
    syncMessageCenterNotices([notice("same-key", { source: "operation" })], "site");
    syncMessageCenterNotices([notice("same-key", { source: "assistant", referenceId: "run-id" })], "account");
    expect(getBackgroundTasks()).toHaveLength(2);
    removeNotice("assistant", "run-id");
    expect(getBackgroundTasks()).toHaveLength(1);
    expect(getBackgroundTasks()[0].noticeScope).toBe("site");
});
