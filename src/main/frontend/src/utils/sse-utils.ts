import { MessageInstance } from "antd/es/message/interface";
import { getBackendServerUrl, getRes, isStaticPage } from "./constants";
import {
    createBackgroundTask,
    finishBackgroundTask,
    removeBackgroundTask,
    updateBackgroundTask,
} from "./background-task-store";

export type StaticProgress = {
    total?: number;
    handled?: number;
    handing?: number;
    pending?: number;
    retrying?: number;
    siteTypes?: string[];
};

export type SseEvent<T = any> = {
    event: string;
    data: T;
};

export type RefreshCacheSseOptions<T> = {
    body?: any;
    messageApi?: MessageInstance;
    messageKey?: string;
    onEvent?: (event: SseEvent) => void;
    onResponse?: (data: T) => void;
    responseEvents?: string[];
    waitForComplete?: boolean;
    resolveWhenStarted?: boolean;
    backgroundTaskTitle?: string;
    removeBackgroundTaskOnSuccess?: boolean;
};

const toApiUrl = (uri: string) => {
    if (uri.startsWith("http://") || uri.startsWith("https://")) {
        return uri;
    }
    if (uri.startsWith("/")) {
        return getBackendServerUrl() + uri.substring(1);
    }
    return getBackendServerUrl() + uri;
};

export const getStaticProgressText = (progress: StaticProgress) => {
    const siteTypes = progress.siteTypes || [];
    const total = progress.total || 0;
    const handled = progress.handled || 0;
    const retrying = progress.retrying || 0;
    const actionText = getStaticSiteActionText(siteTypes, total > 0 && handled < total ? "generating" : "syncing");
    const suffix = retrying > 0 ? ` ${getRes().staticSite.retrying}: ${retrying}` : "";
    if (total > 0 && handled < total) {
        return `${actionText} ${handled}/${total}${suffix}`;
    }
    if (total > 0) {
        return `${actionText} ${handled}/${total}${suffix}`;
    }
    return getStaticSiteActionText(siteTypes, "syncing");
};

const getStaticSiteActionText = (siteTypes: string[], stage: "generating" | "syncing") => {
    const hasAdmin = siteTypes.includes("ADMIN");
    const hasBlog = siteTypes.includes("BLOG");
    const staticSiteRes = getRes().staticSite;
    if (hasAdmin && hasBlog) {
        return stage === "generating" ? staticSiteRes.generatingHtmlAll : staticSiteRes.syncingAll;
    }
    if (hasAdmin) {
        return stage === "generating" ? staticSiteRes.generatingHtmlAdmin : staticSiteRes.syncingAdmin;
    }
    if (hasBlog) {
        return stage === "generating" ? staticSiteRes.generatingHtmlBlog : staticSiteRes.syncingBlog;
    }
    return stage === "generating" ? staticSiteRes.generatingHtml : staticSiteRes.syncing;
};

export const parseSseEvent = (chunk: string): SseEvent | null => {
    const lines = chunk.split("\n");
    const event = lines
        .find((line) => line.startsWith("event:"))
        ?.substring("event:".length)
        .trim();
    const data = lines
        .filter((line) => line.startsWith("data:"))
        .map((line) => line.substring("data:".length).trim())
        .join("\n");
    if (!event || !data) {
        return null;
    }
    return {
        event,
        data: JSON.parse(data),
    };
};

export const postRefreshCacheSse = async <T>(uri: string, options: RefreshCacheSseOptions<T> = {}): Promise<T> => {
    const messageKey = options.messageKey || "refreshCache";
    const supportSse = getRes().supportSse !== false;
    const backgroundTaskId = options.backgroundTaskTitle
        ? createBackgroundTask(options.backgroundTaskTitle, getRes().backgroundTask.started)
        : undefined;
    const response = await fetch(toApiUrl(uri), {
        method: "POST",
        headers: {
            "Content-Type": "application/json",
            Accept: supportSse ? "text/event-stream" : "application/json",
        },
        credentials: isStaticPage() ? "include" : "same-origin",
        body: options.body === undefined ? undefined : JSON.stringify(options.body),
    });
    if (!response.ok) {
        if (backgroundTaskId) {
            finishBackgroundTask(backgroundTaskId, "error", `${getRes().error.requestError}: ${response.status}`);
        }
        throw new Error(`${getRes().error.requestError}: ${response.status}`);
    }
    if (!supportSse) {
        options.messageApi?.destroy(messageKey);
        const data = await response.json();
        if (backgroundTaskId) {
            finishBackgroundTask(backgroundTaskId, data.error ? "error" : "success", data.message);
        }
        return data;
    }
    const contentType = response.headers.get("content-type");
    if (!contentType || !contentType.includes("text/event-stream")) {
        options.messageApi?.destroy(messageKey);
        const data = await response.json();
        if (backgroundTaskId) {
            finishBackgroundTask(backgroundTaskId, data.error ? "error" : "success", data.message);
        }
        return data;
    }
    if (options.resolveWhenStarted) {
        consumeRefreshCacheSse(response, { ...options, backgroundTaskTitle: undefined }, undefined, undefined, backgroundTaskId).catch(
            () => {
                // background consume errors are already surfaced through UI.
            }
        );
        return undefined as T;
    }
    if (options.waitForComplete) {
        return await readRefreshCacheSse(response, options, backgroundTaskId);
    }
    return await readRefreshCacheSseImmediate(response, options, backgroundTaskId);
};

const readRefreshCacheSse = async <T>(
    response: Response,
    options: RefreshCacheSseOptions<T> = {},
    backgroundTaskId?: string
): Promise<T> => {
    let responseData: T | undefined;
    await consumeRefreshCacheSse(response, options, (data) => {
        responseData = data;
    }, undefined, backgroundTaskId);
    if (responseData === undefined) {
        throw new Error(getRes().error.requestError);
    }
    return responseData;
};

const readRefreshCacheSseImmediate = async <T>(
    response: Response,
    options: RefreshCacheSseOptions<T> = {},
    backgroundTaskId?: string
): Promise<T> => {
    return await new Promise<T>((resolve, reject) => {
        let resolved = false;
        consumeRefreshCacheSse(
            response,
            options,
            (data) => {
                if (resolved) {
                    return;
                }
                resolved = true;
                resolve(data);
            },
            (error) => {
                if (resolved) {
                    return;
                }
                resolved = true;
                reject(error);
            },
            backgroundTaskId
        ).catch(() => {
            // background consume errors are already surfaced through UI.
        });
    });
};

const consumeRefreshCacheSse = async <T>(
    response: Response,
    options: RefreshCacheSseOptions<T> = {},
    onResponseData?: (data: T) => void,
    onFatal?: (error: Error) => void,
    backgroundTaskId?: string
) => {
    const reader = response.body?.getReader();
    if (!reader) {
        if (backgroundTaskId) {
            finishBackgroundTask(backgroundTaskId, "error", getRes().error.requestError);
        }
        throw new Error(getRes().error.requestError);
    }
    const messageKey = options.messageKey || "refreshCache";
    const responseEvents =
        options.responseEvents && options.responseEvents.length > 0 ? options.responseEvents : ["response"];
    const decoder = new TextDecoder();
    let buffer = "";
    let latestResponseMessage = "";
    let hasError = false;
    let responseHasError = false;
    const showProgressMessage = !backgroundTaskId;
    const showSuccessMessage = !backgroundTaskId;
    for (;;) {
        const { done, value } = await reader.read();
        if (done) {
            break;
        }
        buffer += decoder.decode(value, { stream: true });
        const chunks = buffer.split("\n\n");
        buffer = chunks.pop() || "";
        for (const chunk of chunks) {
            const event = parseSseEvent(chunk);
            if (!event) {
                continue;
            }
            options.onEvent?.(event);
            if (responseEvents.includes(event.event)) {
                const data = event.data as T;
                onResponseData?.(data);
                options.onResponse?.(data);
                latestResponseMessage = (event.data as any)?.message || latestResponseMessage;
                responseHasError = (event.data as any)?.error > 0;
            }
            if (event.event === "static-progress") {
                if (backgroundTaskId) {
                    updateBackgroundTask(backgroundTaskId, {
                        description: getStaticProgressText(event.data),
                    });
                }
                if (showProgressMessage) {
                    options.messageApi?.open({
                        key: messageKey,
                        type: "loading",
                        content: getStaticProgressText(event.data),
                        duration: 0,
                    });
                }
            }
            if (event.event === "static-sync-start" || event.event === "static-sync-progress") {
                if (backgroundTaskId) {
                    updateBackgroundTask(backgroundTaskId, {
                        description: getStaticProgressText(event.data),
                    });
                }
                if (showProgressMessage) {
                    options.messageApi?.open({
                        key: messageKey,
                        type: "loading",
                        content: getStaticProgressText(event.data),
                        duration: 0,
                    });
                }
            }
            if (event.event === "static-sync-complete") {
                if (backgroundTaskId) {
                    updateBackgroundTask(backgroundTaskId, {
                        description: getRes().staticSite.syncComplete,
                    });
                }
                if (showProgressMessage) {
                    options.messageApi?.open({
                        key: messageKey,
                        type: "loading",
                        content: getRes().staticSite.syncComplete,
                        duration: 0,
                    });
                }
            }
            if (event.event === "refresh-complete") {
                if (backgroundTaskId) {
                    updateBackgroundTask(backgroundTaskId, {
                        description: getRes().staticSite.syncComplete,
                    });
                }
                if (showSuccessMessage) {
                    options.messageApi?.success({
                        key: messageKey,
                        content: getRes().staticSite.syncComplete,
                    });
                }
            }
            if (event.event === "publish-start") {
                if (backgroundTaskId) {
                    updateBackgroundTask(backgroundTaskId, {
                        description: event.data?.message || getRes().staticSite.publishStart,
                    });
                }
            }
            if (event.event === "publish-complete") {
                if (backgroundTaskId) {
                    updateBackgroundTask(backgroundTaskId, {
                        description: event.data?.message || getRes().staticSite.publishComplete,
                    });
                }
            }
            if (event.event === "static-error" || event.event === "sse-error" || event.event === "publish-error") {
                hasError = true;
                options.messageApi?.error({
                    key: messageKey,
                    content: event.data?.message || getRes().staticSite.syncFailed,
                });
                if (backgroundTaskId) {
                    finishBackgroundTask(backgroundTaskId, "error", event.data?.message || getRes().staticSite.syncFailed);
                }
                const error = new Error(event.data?.message || getRes().staticSite.syncFailed);
                onFatal?.(error);
                throw error;
            }
        }
    }
    if (backgroundTaskId && !hasError) {
        if (options.removeBackgroundTaskOnSuccess && !responseHasError) {
            removeBackgroundTask(backgroundTaskId);
            return;
        }
        finishBackgroundTask(
            backgroundTaskId,
            responseHasError ? "error" : "success",
            latestResponseMessage || getRes().backgroundTask.finished
        );
    }
};
