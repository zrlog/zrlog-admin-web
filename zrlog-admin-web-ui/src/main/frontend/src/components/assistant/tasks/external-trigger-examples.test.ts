import { buildWorkerExample } from "./external-trigger-examples";
import { expect, it, jest } from "@jest/globals";

const messages = {
    workerRunning: "Cron triggers the tasks.",
    workerConfigRequired: "Configure the URL and Secret.",
    workerRequestFailed: "HTTP status: {status}",
    workerResponseFailed: "Trigger rejected.",
};
const env = {
    ZRLOG_ASSISTANT_TRIGGER_URL: "https://blog.example.com/sub/api/admin/assistant/trigger",
    ZRLOG_ASSISTANT_TASK_TOKEN: "test-token",
};

function loadWorker(fetch: (...args: unknown[]) => unknown) {
    return new Function("fetch", "Response", buildWorkerExample(messages).replace("export default", "return"))(
        fetch,
        class {
            constructor(public body: string) {}
        }
    );
}

it("keeps scheduled work alive until the authenticated request and its result finish", async () => {
    let finish!: (value: unknown) => void;
    const body = new Promise((resolve) => {
        finish = resolve;
    });
    const fetch = jest.fn(async () => ({ ok: true, json: () => body }));
    const worker = loadWorker(fetch);
    const waitUntil = jest.fn<void, [Promise<void>]>();
    await worker.scheduled({}, env, { waitUntil });
    expect(fetch).toHaveBeenCalledWith(env.ZRLOG_ASSISTANT_TRIGGER_URL, {
        method: "POST",
        redirect: "error",
        headers: { Authorization: "Bearer test-token", Accept: "application/json" },
    });
    let completed = false;
    const pending = waitUntil.mock.calls[0][0].then(() => {
        completed = true;
    });
    await Promise.resolve();
    expect(completed).toBe(false);
    finish({ error: 0, data: { executed: 1 } });
    await pending;
    expect(completed).toBe(true);
});

it("does not trigger tasks when the Worker health URL is opened", async () => {
    const fetch = jest.fn();
    expect((await loadWorker(fetch).fetch()).body).toBe(messages.workerRunning);
    expect(fetch).not.toHaveBeenCalled();
});

it.each([
    [{ ok: false, status: 401 }, "HTTP status: 401"],
    [{ ok: true, json: async () => ({ error: 1 }) }, "Trigger rejected."],
])("propagates rejected triggers to the Cron result", async (response, message) => {
    const waitUntil = jest.fn<void, [Promise<void>]>();
    await loadWorker(jest.fn(async () => response)).scheduled({}, env, { waitUntil });
    await expect(waitUntil.mock.calls[0][0]).rejects.toThrow(message);
});

it("rejects missing Secrets before making an outbound request", async () => {
    const fetch = jest.fn();
    const waitUntil = jest.fn<void, [Promise<void>]>();
    await loadWorker(fetch).scheduled({}, { ...env, ZRLOG_ASSISTANT_TASK_TOKEN: "" }, { waitUntil });
    await expect(waitUntil.mock.calls[0][0]).rejects.toThrow(messages.workerConfigRequired);
    expect(fetch).not.toHaveBeenCalled();
});
