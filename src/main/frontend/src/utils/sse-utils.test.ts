import { afterAll, beforeEach, describe, expect, it, jest } from "@jest/globals";
import type { MessageInstance } from "antd/es/message/interface";
import { postRefreshCacheSse } from "./sse-utils";

let mockSupportSse = true;

jest.mock("./constants", () => ({
    formatLabelValue: (label: string, value: unknown) => `${label}: ${value}`,
    getBackendServerUrl: () => "https://api.example/",
    getRes: () => ({
        articleEdit: {
            publishCheck: {
                finished: "Publish check finished",
                running: "Publish check running",
            },
        },
        backgroundTask: {
            finished: "Task finished",
            started: "Task started",
        },
        error: {
            requestError: "Request failed",
        },
        staticSite: {
            generatingHtml: "Generating",
            generatingHtmlAdmin: "Generating admin",
            generatingHtmlAll: "Generating all",
            generatingHtmlBlog: "Generating blog",
            publishComplete: "Publish complete",
            publishStart: "Publishing",
            retrying: "Retrying",
            syncComplete: "Sync complete",
            syncFailed: "Sync failed",
            syncingAdmin: "Syncing admin",
            syncingAll: "Syncing all",
            syncingBlog: "Syncing blog",
        },
        supportSse: mockSupportSse,
    }),
    isStaticPage: () => true,
}));

jest.mock("./background-task-store", () => ({
    createBackgroundTask: require("@jest/globals").jest.fn(),
    finishBackgroundTask: require("@jest/globals").jest.fn(),
    removeBackgroundTask: require("@jest/globals").jest.fn(),
    updateBackgroundTask: require("@jest/globals").jest.fn(),
}));

jest.mock("./background-task-result", () => ({
    buildBackgroundTaskResult: (response: { error?: number; message?: string }) => ({
        description: response.message,
        status: response.error ? "error" : "success",
    }),
}));

const originalFetch = globalThis.fetch;
const originalTextDecoder = globalThis.TextDecoder;
const originalTextEncoder = globalThis.TextEncoder;
const NodeTextDecoder = require("util").TextDecoder;
const NodeTextEncoder = require("util").TextEncoder;
const mockFetch = jest.fn<Promise<Response>, [RequestInfo | URL, RequestInit?]>();
const encode = (value: string) => new TextEncoder().encode(value);
const flushPromises = () => new Promise((resolve) => setTimeout(resolve, 0));

const messageApi = {
    destroy: jest.fn(),
    error: jest.fn(),
    open: jest.fn(),
    success: jest.fn(),
} as unknown as MessageInstance;

const sseResponse = (read: () => Promise<ReadableStreamReadResult<Uint8Array>>) =>
    ({
        body: {
            getReader: () => ({ read }),
        },
        headers: {
            get: () => "text/event-stream;charset=UTF-8",
        },
        ok: true,
    } as unknown as Response);

describe("postRefreshCacheSse", () => {
    beforeEach(() => {
        mockSupportSse = true;
        mockFetch.mockReset();
        Object.defineProperty(globalThis, "fetch", { configurable: true, value: mockFetch });
        Object.defineProperty(globalThis, "TextDecoder", { configurable: true, value: NodeTextDecoder });
        Object.defineProperty(globalThis, "TextEncoder", { configurable: true, value: NodeTextEncoder });
        jest.clearAllMocks();
    });

    afterAll(() => {
        Object.defineProperty(globalThis, "fetch", { configurable: true, value: originalFetch });
        Object.defineProperty(globalThis, "TextDecoder", { configurable: true, value: originalTextDecoder });
        Object.defineProperty(globalThis, "TextEncoder", { configurable: true, value: originalTextEncoder });
    });

    it("waits for the refresh stream to finish when requested", async () => {
        let readCount = 0;
        let releaseRefresh!: (result: ReadableStreamReadResult<Uint8Array>) => void;
        const refreshEvent = new Promise<ReadableStreamReadResult<Uint8Array>>((resolve) => {
            releaseRefresh = resolve;
        });
        const read = jest.fn(() => {
            readCount += 1;
            if (readCount === 1) {
                return Promise.resolve({
                    done: false as const,
                    value: encode('event: response\ndata: {"error":0,"data":true}\n\n'),
                });
            }
            if (readCount === 2) {
                return refreshEvent;
            }
            return Promise.resolve({ done: true as const, value: undefined });
        });
        mockFetch.mockResolvedValueOnce(sseResponse(read));

        let settled = false;
        const request = postRefreshCacheSse<{ error: number; data: boolean }>("/refresh", {
            messageApi,
            requiredCompletionEvent: "refresh-complete",
            waitForComplete: true,
        }).finally(() => {
            settled = true;
        });
        await flushPromises();

        expect(settled).toBe(false);
        releaseRefresh({
            done: false,
            value: encode('event: refresh-complete\ndata: {"error":0,"data":true}\n\n'),
        });

        await expect(request).resolves.toEqual({ error: 0, data: true });
        expect(messageApi.success).toHaveBeenCalledWith({
            key: "refreshCache",
            content: "Sync complete",
        });
    });

    it("returns the business response while continuing to report static refresh failures", async () => {
        let readCount = 0;
        let releaseRefresh!: (result: ReadableStreamReadResult<Uint8Array>) => void;
        const refreshEvent = new Promise<ReadableStreamReadResult<Uint8Array>>((resolve) => {
            releaseRefresh = resolve;
        });
        mockFetch.mockResolvedValueOnce(
            sseResponse(() => {
                readCount += 1;
                if (readCount === 1) {
                    return Promise.resolve({
                        done: false as const,
                        value: encode('event: response\ndata: {"error":0,"data":true}\n\n'),
                    });
                }
                if (readCount === 2) {
                    return refreshEvent;
                }
                return Promise.resolve({ done: true as const, value: undefined });
            })
        );

        await expect(postRefreshCacheSse("/refresh", { messageApi })).resolves.toEqual({ error: 0, data: true });
        expect(messageApi.error).not.toHaveBeenCalled();

        releaseRefresh({
            done: false,
            value: encode('event: static-error\ndata: {"message":"Static refresh failed"}\n\n'),
        });
        await flushPromises();

        expect(messageApi.error).toHaveBeenCalledWith({
            key: "refreshCache",
            content: "Static refresh failed",
        });
    });

    it("reports a broken stream after the business response without rejecting the completed request", async () => {
        let readCount = 0;
        mockFetch.mockResolvedValueOnce(
            sseResponse(() => {
                readCount += 1;
                if (readCount === 1) {
                    return Promise.resolve({
                        done: false as const,
                        value: encode('event: response\ndata: {"error":0,"data":true}\n\n'),
                    });
                }
                return Promise.reject(new Error("connection closed"));
            })
        );

        await expect(postRefreshCacheSse("/refresh", { messageApi })).resolves.toEqual({ error: 0, data: true });
        await flushPromises();

        expect(messageApi.error).toHaveBeenCalledWith({
            key: "refreshCache",
            content: "Sync failed: connection closed",
        });
    });

    it("reports a missing required completion event after the business response", async () => {
        let readCount = 0;
        mockFetch.mockResolvedValueOnce(
            sseResponse(() => {
                readCount += 1;
                return Promise.resolve(
                    readCount === 1
                        ? {
                              done: false as const,
                              value: encode('event: response\ndata: {"error":0,"data":true}\n\n'),
                          }
                        : { done: true as const, value: undefined }
                );
            })
        );

        await expect(
            postRefreshCacheSse("/refresh", {
                messageApi,
                requiredCompletionEvent: "refresh-complete",
            })
        ).resolves.toEqual({ error: 0, data: true });
        await flushPromises();

        expect(messageApi.error).toHaveBeenCalledWith({
            key: "refreshCache",
            content: "Sync failed",
        });
    });

    it("stops consuming after the required completion event", async () => {
        let readCount = 0;
        mockFetch.mockResolvedValueOnce(
            sseResponse(() => {
                readCount += 1;
                if (readCount === 1) {
                    return Promise.resolve({
                        done: false as const,
                        value: encode('event: response\ndata: {"error":0,"data":true}\n\n'),
                    });
                }
                if (readCount === 2) {
                    return Promise.resolve({
                        done: false as const,
                        value: encode('event: refresh-complete\ndata: {"error":0,"data":true}\n\n'),
                    });
                }
                return Promise.reject(new Error("connection reset after completion"));
            })
        );

        await expect(
            postRefreshCacheSse("/refresh", {
                messageApi,
                requiredCompletionEvent: "refresh-complete",
            })
        ).resolves.toEqual({ error: 0, data: true });
        await flushPromises();

        expect(readCount).toBe(2);
        expect(messageApi.error).not.toHaveBeenCalled();
        expect(messageApi.success).toHaveBeenCalledWith({
            key: "refreshCache",
            content: "Sync complete",
        });
    });

    it("rejects instead of hanging when the stream ends before a response event", async () => {
        mockFetch.mockResolvedValueOnce(sseResponse(() => Promise.resolve({ done: true as const, value: undefined })));

        await expect(postRefreshCacheSse("/refresh")).rejects.toThrow("Request failed");
    });

    it("rejects malformed response events instead of hanging", async () => {
        let readCount = 0;
        mockFetch.mockResolvedValueOnce(
            sseResponse(() => {
                readCount += 1;
                return Promise.resolve(
                    readCount === 1
                        ? { done: false as const, value: encode("event: response\ndata: invalid-json\n\n") }
                        : { done: true as const, value: undefined }
                );
            })
        );

        await expect(postRefreshCacheSse("/refresh")).rejects.toBeInstanceOf(SyntaxError);
    });

    it("falls back to JSON when SSE is disabled", async () => {
        mockSupportSse = false;
        const apiResponse = { error: 0, data: true };
        mockFetch.mockResolvedValueOnce({
            headers: { get: () => "application/json" },
            json: async () => apiResponse,
            ok: true,
        } as unknown as Response);

        await expect(postRefreshCacheSse("/refresh", { body: { enabled: true }, messageApi })).resolves.toBe(
            apiResponse
        );
        expect(mockFetch).toHaveBeenCalledWith("https://api.example/refresh", {
            method: "POST",
            headers: {
                "Content-Type": "application/json",
                Accept: "application/json",
            },
            credentials: "include",
            body: '{"enabled":true}',
        });
    });
});
