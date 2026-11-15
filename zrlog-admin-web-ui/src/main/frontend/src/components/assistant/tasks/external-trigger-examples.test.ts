import { readFileSync } from "fs";
import { resolve } from "path";
import { Lexer, Tokens } from "marked";
import { buildExternalTriggerMarkdown } from "./external-trigger-examples";
import { describe, expect, it, jest } from "@jest/globals";

const env = {
    ZRLOG_ASSISTANT_TRIGGER_URL: "https://blog.example.com/sub/api/admin/assistant/trigger",
    ZRLOG_ASSISTANT_TASK_TOKEN: "test-token",
};

describe.each(["zh_CN", "en_US"])("%s guide", (locale) => {
    const template = readFileSync(resolve(__dirname, "../../../i18n/help/assistant-trigger", locale + ".md"), "utf8");
    const workerCode = Lexer.lex(template).find((token) => token.type === "code" && token.lang === "javascript");
    if (!workerCode || workerCode.type !== "code") throw new Error("Missing Worker code example");
    const workerSource = workerCode.text;

    function loadWorker(fetch: (...args: unknown[]) => unknown) {
        return new Function("fetch", "Response", workerSource.replace("export default", "return"))(
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
        expect((await loadWorker(fetch).fetch()).body).toMatch(/Cron/);
        expect(fetch).not.toHaveBeenCalled();
    });

    it.each([
        [{ ok: false, status: 401 }, /HTTP 401/],
        [{ ok: true, json: async () => ({ error: 1 }) }, /助手未接受|assistant rejected/],
    ])("propagates rejected triggers to the Cron result", async (response, message) => {
        const waitUntil = jest.fn<void, [Promise<void>]>();
        await loadWorker(jest.fn(async () => response)).scheduled({}, env, { waitUntil });
        await expect(waitUntil.mock.calls[0][0]).rejects.toThrow(message);
    });

    it("rejects missing Secrets before making an outbound request", async () => {
        const fetch = jest.fn();
        const waitUntil = jest.fn<void, [Promise<void>]>();
        await loadWorker(fetch).scheduled({}, { ...env, ZRLOG_ASSISTANT_TASK_TOKEN: "" }, { waitUntil });
        await expect(waitUntil.mock.calls[0][0]).rejects.toThrow(
            /ZRLOG_ASSISTANT_TRIGGER_URL.*ZRLOG_ASSISTANT_TASK_TOKEN/
        );
        expect(fetch).not.toHaveBeenCalled();
    });

    it("keeps dynamic values literal in every code block, including fences and placeholder-like tokens", () => {
        const endpoint = env.ZRLOG_ASSISTANT_TRIGGER_URL + '?value="$&`{{curlExample}}';
        const curl = "curl '$&'\n```\n<script>alert(1)</script>\n{{triggerEndpoint}}";
        const markdown = buildExternalTriggerMarkdown(template, endpoint, curl);
        const blocks = Lexer.lex(markdown).filter((token): token is Tokens.Code => token.type === "code");
        expect(blocks).toHaveLength(6);
        expect(blocks.map((block) => block.lang)).toEqual([
            "bash",
            "javascript",
            "plaintext",
            "plaintext",
            "toml",
            "bash",
        ]);
        expect(blocks[0].text).toBe(curl);
        expect(blocks[1].text).toBe(workerSource);
        expect(blocks[2].text).toBe(endpoint);
        expect(blocks[4].text).toContain("ZRLOG_ASSISTANT_TRIGGER_URL = " + JSON.stringify(endpoint));
        expect(Lexer.lex(markdown).some((token) => token.type === "html")).toBe(false);
    });
});
