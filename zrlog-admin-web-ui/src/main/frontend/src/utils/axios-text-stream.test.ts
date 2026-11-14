import { afterEach, beforeEach, expect, it, jest } from "@jest/globals";
import axios, { AxiosAdapter, AxiosError, AxiosHeaders, AxiosInstance, InternalAxiosRequestConfig } from "axios";
import { enableAxiosTextStreaming } from "./axios-text-stream";
import { getErrorMessage } from "./error-message";

const { ReadableStream: TestStream } = require("stream/web") as { ReadableStream: typeof ReadableStream };
const { TextDecoder: TestDecoder, TextEncoder: TestEncoder } = require("util");
const originalDecoder = globalThis.TextDecoder;
const encode = (text: string): Uint8Array => new TestEncoder().encode(text);
const drain = async () => {
    for (let i = 0; i < 12; i++) await Promise.resolve();
};
let client: AxiosInstance;
let source: ReadableStreamDefaultController<Uint8Array>;
let body: ReadableStream<Uint8Array>;
let cancel: ReturnType<typeof jest.fn>;
let fetchAdapter = jest.fn<ReturnType<AxiosAdapter>, Parameters<AxiosAdapter>>();

const response = (config: InternalAxiosRequestConfig, status = 200) => ({
    config,
    data: body,
    status,
    statusText: status === 200 ? "OK" : "Forbidden",
    headers: new AxiosHeaders({ "content-type": "text/event-stream" }),
});

beforeEach(() => {
    jest.useFakeTimers();
    globalThis.TextDecoder = TestDecoder;
    cancel = jest.fn();
    body = new TestStream({
        start(controller) {
            source = controller;
        },
        cancel() {
            cancel();
        },
    });
    fetchAdapter = jest.fn(async (config: InternalAxiosRequestConfig) => response(config));
    const originalGetAdapter = axios.getAdapter;
    jest.spyOn(axios, "getAdapter").mockImplementation((adapters) =>
        adapters === "fetch" ? fetchAdapter : originalGetAdapter(adapters)
    );
    client = axios.create({ baseURL: "https://example.test/sub/", withCredentials: true });
    enableAxiosTextStreaming(client);
});

afterEach(() => {
    jest.restoreAllMocks();
    jest.useRealTimers();
    globalThis.TextDecoder = originalDecoder;
});

it("renders successive chunks on animation frames without waiting 333ms, then resolves the full text", async () => {
    const updates: string[] = [];
    let settled = false;
    const request = client
        .post(
            "/chat",
            { input: "Hello" },
            {
                onTextProgress: (text) => updates.push(text),
            }
        )
        .then((result) => {
            settled = true;
            return result;
        });
    await drain();
    for (const character of ["你", "好", "，", "世", "界"]) {
        source.enqueue(encode(character));
        await drain();
        jest.advanceTimersByTime(16);
    }
    expect(updates).toEqual(["你", "你好", "你好，", "你好，世", "你好，世界"]);
    expect(settled).toBe(false);
    source.enqueue(encode("🙂"));
    await drain();
    source.close();
    expect((await request).data).toBe("你好，世界🙂");
    expect(updates[updates.length - 1]).toBe("你好，世界🙂");
    expect(body.locked).toBe(false);
    const count = updates.length;
    jest.advanceTimersByTime(1000);
    expect(updates).toHaveLength(count);
});

it("combines chunks within a frame and preserves UTF-8 and SSE delimiters split across bytes", async () => {
    const updates = jest.fn();
    const request = client.post("/chat", {}, { onTextProgress: updates });
    await drain();
    const text = 'data: {"content":"你好🙂"}\r\n\r\n';
    for (const byte of Array.from(encode(text))) {
        source.enqueue(new Uint8Array([byte]));
        await drain();
    }
    expect(updates).not.toHaveBeenCalled();
    jest.advanceTimersByTime(16);
    expect(updates.mock.calls).toEqual([[text]]);
    source.close();
    expect((await request).data).toBe(text);
});

it("keeps request transforms, credentials, headers and final response interceptors", async () => {
    client.interceptors.request.use((config) => {
        config.headers.set("X-Test", "request-interceptor");
        return config;
    });
    const received = jest.fn();
    client.interceptors.response.use((result) => {
        received(result.data);
        return result;
    });
    const controller = new AbortController();
    const progress = jest.fn();
    const request = client.post(
        "/chat",
        { input: "你好" },
        {
            signal: controller.signal,
            onTextProgress: jest.fn(),
            onDownloadProgress: progress,
        }
    );
    await drain();
    const config = fetchAdapter.mock.calls[0][0];
    expect(config).toMatchObject({
        baseURL: "https://example.test/sub/",
        withCredentials: true,
        responseType: "stream",
        data: '{"input":"你好"}',
        signal: controller.signal,
    });
    expect(config.headers.get("X-Test")).toBe("request-interceptor");
    expect(config.headers.getContentType()).toBe("application/json");
    expect(config.onDownloadProgress).toBeUndefined();
    source.enqueue(encode("reply"));
    await drain();
    expect(received).not.toHaveBeenCalled();
    source.close();
    await request;
    expect(received).toHaveBeenCalledWith("reply");
    expect(progress).not.toHaveBeenCalled();
});

it("cancels a blocked read and pending UI update when a request is stopped", async () => {
    const controller = new AbortController();
    const updates = jest.fn();
    const result = client.post("/chat", {}, { signal: controller.signal, onTextProgress: updates }).catch((e) => e);
    await drain();
    source.enqueue(encode("old private text"));
    await drain();
    controller.abort();
    expect(axios.isCancel(await result)).toBe(true);
    jest.advanceTimersByTime(1000);
    expect(updates).not.toHaveBeenCalled();
    expect(cancel).toHaveBeenCalled();
    expect(body.locked).toBe(false);
});

it("delivers received checkpoint text before reporting a broken connection", async () => {
    const updates = jest.fn();
    const result = client.post("/chat", {}, { onTextProgress: updates }).catch((e) => e);
    await drain();
    const checkpoint = 'data: {"type":"approval-required"}\n\n';
    source.enqueue(encode(checkpoint));
    await drain();
    source.error(new TypeError("private network diagnostic"));
    const error = await result;
    expect(updates.mock.calls).toEqual([[checkpoint]]);
    expect(getErrorMessage(error, "Request failed")).toBe("Request failed");
    expect(body.locked).toBe(false);
    jest.advanceTimersByTime(1000);
    expect(updates).toHaveBeenCalledTimes(1);
});

it("decodes HTTP error bodies before existing response interceptors handle them", async () => {
    fetchAdapter.mockImplementationOnce(async (config) => {
        throw new AxiosError("HTTP error", AxiosError.ERR_BAD_RESPONSE, config, undefined, response(config, 403));
    });
    const handled = jest.fn();
    client.interceptors.response.use(undefined, (error) => {
        handled(getErrorMessage(error, "Request failed"));
        return Promise.reject(error);
    });
    const updates = jest.fn();
    const result = client.post("/chat", {}, { onTextProgress: updates }).catch((e) => e);
    await drain();
    source.enqueue(encode('{"error":9016,"message":"请重新登录"}'));
    source.close();
    const error = await result;
    expect(error.response.status).toBe(403);
    expect(handled).toHaveBeenCalledWith("请重新登录");
    expect(updates).not.toHaveBeenCalled();
    expect(body.locked).toBe(false);
});

it("does not wait for an animation frame to finish a JSON error response", async () => {
    const request = client.post("/chat", {}, { onTextProgress: jest.fn() });
    await drain();
    const text = '{"error":9039,"message":"Unsupported file"}';
    source.enqueue(encode(text));
    source.close();
    expect((await request).data).toBe(text);
});

it("cancels and rejects when a streaming consumer throws", async () => {
    const result = client
        .post(
            "/chat",
            {},
            {
                onTextProgress: () => {
                    throw new Error("Consumer failed");
                },
            }
        )
        .catch((e) => e);
    await drain();
    source.enqueue(encode("text"));
    await drain();
    jest.advanceTimersByTime(16);
    expect((await result).message).toBe("Consumer failed");
    expect(cancel).toHaveBeenCalled();
    expect(body.locked).toBe(false);
});

it("leaves ordinary requests and download progress on their configured adapter", async () => {
    const adapter: AxiosAdapter = async (config) => ({ ...response(config), data: { result: "ordinary" } });
    const result = await client.get("/settings", { adapter, onDownloadProgress: jest.fn() });
    expect(result.data).toEqual({ result: "ordinary" });
    expect(fetchAdapter).not.toHaveBeenCalled();
});

it("returns empty text for a response without a body", async () => {
    fetchAdapter.mockImplementationOnce(async (config) => ({ ...response(config, 204), data: null }));
    const updates = jest.fn();
    expect((await client.post("/chat", {}, { onTextProgress: updates })).data).toBe("");
    expect(updates).not.toHaveBeenCalled();
});
