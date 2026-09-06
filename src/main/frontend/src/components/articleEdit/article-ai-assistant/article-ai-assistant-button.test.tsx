import { act, ReactElement } from "react";
import { createRoot, Root } from "react-dom/client";
import { afterEach, beforeEach, describe, expect, it, jest } from "@jest/globals";
import { AIContent } from "@editor/dist/ai/AIContentItem";
import { AIProviderType } from "../../../type";
import { ArticleEditState } from "../index.types";
import { createDraftAiSaveGate, DraftAiSaveGate } from "../draft-ai-save-gate";
import { AssistantTool, ToolAwareAIContent } from "./article-ai-assistant.types";
import { useArticleAiAssistantConfig } from "./article-ai-assistant-button";

const mockMessageError = jest.fn(async (content?: unknown): Promise<void> => {
    void content;
});
const mockMessageSuccess = jest.fn(async (content?: unknown): Promise<void> => {
    void content;
});
const mockMessageWarning = jest.fn(async (content?: unknown): Promise<void> => {
    void content;
});

jest.mock("antd", () => {
    const NullComponent = () => null;
    return {
        Alert: NullComponent,
        App: {
            useApp: () => ({
                message: {
                    error: mockMessageError,
                    success: mockMessageSuccess,
                    warning: mockMessageWarning,
                },
            }),
        },
        Button: NullComponent,
        Collapse: NullComponent,
        Drawer: NullComponent,
        Grid: { useBreakpoint: () => ({ md: true, lg: true }) },
        Space: NullComponent,
        Tag: NullComponent,
        Typography: { Paragraph: NullComponent, Text: NullComponent },
    };
});

jest.mock("@ant-design/icons", () => ({ EyeOutlined: () => null }));
jest.mock("antd-style", () => ({
    useTheme: () => ({
        borderRadius: 6,
        borderRadiusLG: 6,
        colorBorderSecondary: "#ddd",
        colorFillQuaternary: "#f5f5f5",
        lineType: "solid",
        lineWidth: 1,
    }),
}));
jest.mock("@editor/dist/ai/AIButton", () => ({
    __esModule: true,
    default: () => null,
    getAIButtonDrawerOpen: () => false,
}));
jest.mock("@editor/dist/ai/AIDrawer", () => ({ resolveDrawerWidth: (width: unknown) => width }));
jest.mock("@editor/dist/editor/utils/marked-utils", () => ({
    markdownToHtmlSyncWithCallback: (markdown: string) => markdown,
}));
jest.mock("../../../base/ConfigProviderApp", () => ({ getAppState: () => ({ dark: false }) }));
jest.mock("../../../common/ImageCropper", () => ({ __esModule: true, default: () => null }));
jest.mock("../../../utils/cache", () => ({
    addToCache: require("@jest/globals").jest.fn(),
    getCacheByKey: require("@jest/globals").jest.fn(),
}));
jest.mock("../../../utils/constants", () => ({
    formatLabelValue: (label: string, value: unknown) => `${label}: ${value}`,
    getLabelValueSeparator: () => ": ",
    getRealRouteUrl: (url: string) => url,
    getRes: () => ({
        articleEdit: {
            assistant: {
                articleContextPreviewTitle: "Article context",
                saveInProgress: "Save in progress",
            },
        },
        error: {
            requestError: "Request error",
            unknown: "Unknown error",
        },
        websiteAi: { label: "AI" },
    }),
    tryAppendBackendServerUrl: (url: string) => url,
}));
jest.mock("../../../utils/helpers", () => ({ getEditorUser: () => ({}) }));
jest.mock("../../../utils/crop-image-url", () => ({ resolveBackendCropImageUrl: (url: string) => url }));
jest.mock("../../article/article-preview-snapshot", () => ({ __esModule: true, default: () => null }));
jest.mock("../cover-aspect-ratio", () => ({ parseCoverAspectRatio: () => 16 / 9 }));
jest.mock("../markdown-reference-utils", () => ({
    collectMarkdownReferenceSummary: () => ({
        imageReferenceCount: 0,
        imageReferences: [],
        linkReferenceCount: 0,
        linkReferences: [],
        externalLinkCount: 0,
        externalLinks: [],
    }),
}));
jest.mock("../shortcut-utils", () => ({
    getShortcutTitle: (label: string) => label,
    isTouchLikeDevice: () => false,
}));
jest.mock("./article-ai-assistant-skill-content", () => ({ __esModule: true, default: () => null }));
jest.mock("./tool/article-ai-assistant-tool-content", () => ({ __esModule: true, default: () => null }));
jest.mock("./tool/article-ai-assistant-tools", () => ({ getAssistantToolLabel: (tool: string) => tool }));

type AssistantConfig = ReturnType<typeof useArticleAiAssistantConfig>;
type FooterActions = {
    onAddArticleContext: () => void;
    onClearAiMessages: () => void;
    onSubmit: (message: string, tool?: AssistantTool) => void;
};

type ToolContentActions = {
    onCropCover: (url: string) => void;
    onUpdateToolPayload: (
        messageIndex: number,
        payload: { tool: "cover"; payload: { url: string } },
        persist?: boolean
    ) => void;
};

type CropperActions = {
    onOk: (dataUrl: string) => Promise<void>;
};

type Deferred<T> = {
    promise: Promise<T>;
    resolve: (value: T) => void;
    reject: (reason: unknown) => void;
};

type AxiosPostConfig = {
    onDownloadProgress?: (event: unknown) => void;
};

type AxiosPost = (url?: string, body?: unknown, config?: AxiosPostConfig) => Promise<any>;

const deferred = <T,>(): Deferred<T> => {
    let resolve!: (value: T) => void;
    let reject!: (reason: unknown) => void;
    const promise = new Promise<T>((resolvePromise, rejectPromise) => {
        resolve = resolvePromise;
        reject = rejectPromise;
    });
    return { promise, resolve, reject };
};

const createState = (logId?: number, aiMessages: AIContent[] = []): ArticleEditState => ({
    typeOptions: [{ value: 1, label: "General" }],
    tags: [],
    aiProvider: AIProviderType.OPEN_AI,
    aiModel: "test-model",
    aiConfigured: true,
    aiMessages,
    linkPreviewEnabled: false,
    publishCheckEnabled: false,
    articleCoverAspectRatio: "16:9",
    articleEditAutoSaveInterval: 5,
    rubbish: true,
    editorVersion: 1,
    contentSource: "server",
    article: {
        logId,
        title: "Draft title",
        typeId: 1,
        markdown: "Draft body",
        content: "<p>Draft body</p>",
        rubbish: true,
        version: logId ? 1 : -1,
    },
    saving: {
        rubbishSaving: false,
        previewIng: false,
        autoSaving: false,
        releaseSaving: false,
    },
});

const reactActEnvironment = globalThis as typeof globalThis & {
    IS_REACT_ACT_ENVIRONMENT?: boolean;
};

describe("useArticleAiAssistantConfig draft request gate", () => {
    const mountedRoots: Array<{ root: Root; container: HTMLDivElement }> = [];

    const mountHook = (
        gate: DraftAiSaveGate,
        post: AxiosPost,
        onAiMessagesChange = jest.fn((messages?: AIContent[], articleId?: number) => {
            void messages;
            void articleId;
        }),
        initialLogId?: number,
        initialMessages: AIContent[] = []
    ) => {
        let data = createState(initialLogId, initialMessages);
        let config!: AssistantConfig;
        const container = document.createElement("div");
        document.body.appendChild(container);
        const root = createRoot(container);
        mountedRoots.push({ root, container });

        const Harness = () => {
            config = useArticleAiAssistantConfig({
                data,
                draftAiSaveGate: gate,
                offline: false,
                axiosInstance: { get: jest.fn(), post } as never,
                onAiMessagesChange,
                onApplyValues: jest.fn(),
            });
            return null;
        };

        const render = () => {
            act(() => root.render(<Harness />));
        };
        render();

        return {
            getConfig: () => config,
            getFooter: () => (config.renderFooter() as ReactElement<FooterActions>).props,
            getToolContent: (content: AIContent, index = 0) =>
                (
                    config.renderMessage({
                        content,
                        index,
                        defaultNode: null,
                    } as never) as ReactElement<ToolContentActions>
                ).props,
            getCropper: () => {
                const overlay = config.overlays as ReactElement<{ children: ReactElement<CropperActions>[] }>;
                return overlay.props.children[0].props;
            },
            onAiMessagesChange,
            rerender: (logId?: number, aiMessages = data.aiMessages) => {
                data = createState(logId, aiMessages);
                render();
            },
        };
    };

    const flushRequest = async () => {
        await Promise.resolve();
        await Promise.resolve();
        await Promise.resolve();
    };

    beforeEach(() => {
        reactActEnvironment.IS_REACT_ACT_ENVIRONMENT = true;
        mockMessageError.mockImplementation(async () => undefined);
        mockMessageSuccess.mockImplementation(async () => undefined);
        mockMessageWarning.mockImplementation(async () => undefined);
        mockMessageError.mockClear();
        mockMessageSuccess.mockClear();
        mockMessageWarning.mockClear();
    });

    afterEach(() => {
        mountedRoots.splice(0).forEach(({ root, container }) => {
            act(() => root.unmount());
            container.remove();
        });
        reactActEnvironment.IS_REACT_ACT_ENVIRONMENT = false;
    });

    it("holds one shared lease per overlapping send until success or failure settles", async () => {
        const gate = createDraftAiSaveGate();
        const firstRequest = deferred<any>();
        const secondRequest = deferred<any>();
        const firstPost = jest.fn(async (): Promise<any> => firstRequest.promise);
        const secondPost = jest.fn(async (): Promise<any> => secondRequest.promise);
        const first = mountHook(gate, firstPost);
        const second = mountHook(gate, secondPost);

        act(() => {
            first.getFooter().onSubmit("First request");
            second.getFooter().onSubmit("Second request");
        });

        expect(gate.getPendingAiCount()).toBe(2);
        expect(firstPost).toHaveBeenCalledTimes(1);
        expect(secondPost).toHaveBeenCalledTimes(1);

        await act(async () => {
            firstRequest.resolve({
                status: 200,
                data: 'data: {"content":"First answer","messageId":"first"}\n\n',
            });
            await flushRequest();
        });
        expect(gate.getPendingAiCount()).toBe(1);

        await act(async () => {
            secondRequest.reject(new Error("Second request failed"));
            await flushRequest();
        });
        expect(gate.getPendingAiCount()).toBe(0);
        expect(mockMessageError).toHaveBeenCalledWith("Second request failed");
    });

    it("holds append-context leases through both success and failure", async () => {
        const gate = createDraftAiSaveGate();
        const successfulRequest = deferred<any>();
        const failedRequest = deferred<any>();
        const successfulPost = jest.fn(async (): Promise<any> => successfulRequest.promise);
        const failedPost = jest.fn(async (): Promise<any> => failedRequest.promise);
        const successful = mountHook(gate, successfulPost);
        const failed = mountHook(gate, failedPost);

        act(() => {
            successful.getFooter().onAddArticleContext();
            failed.getFooter().onAddArticleContext();
        });
        expect(gate.getPendingAiCount()).toBe(2);

        const contextMessage = {
            role: "assistant",
            content: "Article context",
            thinking: false,
            messageType: "articleContext",
            messageId: "context-message",
        } as ToolAwareAIContent;
        successful.rerender(42);
        await act(async () => {
            successfulRequest.resolve({ data: { error: 0, data: [contextMessage] } });
            await flushRequest();
        });
        expect(gate.getPendingAiCount()).toBe(1);
        expect(successful.onAiMessagesChange).toHaveBeenCalledWith([contextMessage], 0);

        await act(async () => {
            failedRequest.reject(new Error("Context request failed"));
            await flushRequest();
        });
        expect(gate.getPendingAiCount()).toBe(0);
        expect(mockMessageError).toHaveBeenCalledWith("Context request failed");
    });

    it("does not start a draft request while first create owns the gate", () => {
        const gate = createDraftAiSaveGate();
        const releaseCreate = gate.tryBeginCreate(0);
        const post = jest.fn(async (): Promise<any> => undefined);
        const mounted = mountHook(gate, post);

        act(() => mounted.getFooter().onSubmit("Blocked request"));

        expect(post).not.toHaveBeenCalled();
        expect(gate.getPendingAiCount()).toBe(0);
        expect(mockMessageWarning).toHaveBeenCalledWith("Save in progress");
        releaseCreate?.();
    });

    it("keeps a draft clear request on id zero and holds its lease until completion", async () => {
        const gate = createDraftAiSaveGate();
        const request = deferred<any>();
        const post = jest.fn(async (url?: string, body?: unknown, config?: unknown): Promise<any> => {
            void url;
            void body;
            void config;
            return request.promise;
        });
        const draftMessage = {
            role: "user",
            content: "Draft question",
            thinking: false,
            messageId: "draft-question",
        } as ToolAwareAIContent;
        const mounted = mountHook(gate, post, undefined, undefined, [draftMessage]);

        act(() => mounted.getFooter().onClearAiMessages());

        expect(post).toHaveBeenCalledWith("/api/admin/article/ai/messages/clear?id=0");
        expect(gate.getPendingAiCount()).toBe(1);
        expect(gate.tryBeginCreate(0)).toBeUndefined();
        mounted.rerender(42, [draftMessage]);

        await act(async () => {
            request.resolve({ data: { error: 0, data: true } });
            await flushRequest();
        });

        expect(mounted.onAiMessagesChange).toHaveBeenCalledWith([], 0);
        expect(gate.getPendingAiCount()).toBe(0);
    });

    it("releases a draft clear lease when the request fails", async () => {
        const gate = createDraftAiSaveGate();
        const request = deferred<any>();
        const post = jest.fn(async (): Promise<any> => request.promise);
        const draftMessage = {
            role: "user",
            content: "Draft question",
            thinking: false,
            messageId: "draft-question",
        } as ToolAwareAIContent;
        const mounted = mountHook(gate, post, undefined, undefined, [draftMessage]);

        act(() => mounted.getFooter().onClearAiMessages());
        await act(async () => {
            request.reject(new Error("Clear failed"));
            await flushRequest();
        });

        expect(mockMessageError).toHaveBeenCalledWith("Clear failed");
        expect(gate.getPendingAiCount()).toBe(0);
        const releaseCreate = gate.tryBeginCreate(0);
        expect(releaseCreate).toBeDefined();
        releaseCreate?.();
    });

    it("keeps a draft payload update on id zero and releases its lease after failure", async () => {
        const gate = createDraftAiSaveGate();
        const request = deferred<any>();
        const post = jest.fn(async (): Promise<any> => request.promise);
        const coverMessage = {
            role: "assistant",
            content: "Generated cover",
            thinking: false,
            messageId: "cover-message",
            tool: "cover",
            payload: { url: "/temporary/original.png" },
        } as ToolAwareAIContent;
        const mounted = mountHook(gate, post, undefined, undefined, [coverMessage]);

        act(() => {
            mounted.getToolContent(coverMessage).onUpdateToolPayload(0, {
                tool: "cover",
                payload: { url: "/temporary/updated.png" },
            });
        });

        expect(post).toHaveBeenCalledWith("/api/admin/article/ai/message?id=0", {
            messageId: "cover-message",
            tool: "cover",
            payload: { url: "/temporary/updated.png" },
        });
        expect(mounted.onAiMessagesChange).toHaveBeenCalledWith(
            [expect.objectContaining({ messageId: "cover-message", payload: { url: "/temporary/updated.png" } })],
            0
        );
        expect(gate.tryBeginCreate(0)).toBeUndefined();
        mounted.rerender(42, [coverMessage]);

        await act(async () => {
            request.reject(new Error("Payload update failed"));
            await flushRequest();
        });

        expect(gate.getPendingAiCount()).toBe(0);
        const releaseCreate = gate.tryBeginCreate(0);
        expect(releaseCreate).toBeDefined();
        releaseCreate?.();
    });

    it("rejects draft clear and payload writes while first create owns the gate", () => {
        const gate = createDraftAiSaveGate();
        const releaseCreate = gate.tryBeginCreate(0);
        const post = jest.fn(async (): Promise<any> => undefined);
        const coverMessage = {
            role: "assistant",
            content: "Generated cover",
            thinking: false,
            messageId: "cover-message",
            tool: "cover",
            payload: { url: "/temporary/original.png" },
        } as ToolAwareAIContent;
        const mounted = mountHook(gate, post, undefined, undefined, [coverMessage]);

        act(() => {
            mounted.getFooter().onClearAiMessages();
            mounted.getToolContent(coverMessage).onUpdateToolPayload(0, {
                tool: "cover",
                payload: { url: "/temporary/updated.png" },
            });
        });

        expect(post).not.toHaveBeenCalled();
        expect(mounted.onAiMessagesChange).not.toHaveBeenCalled();
        expect(mockMessageWarning).toHaveBeenCalledTimes(2);
        expect(mockMessageWarning).toHaveBeenNthCalledWith(1, "Save in progress");
        expect(mockMessageWarning).toHaveBeenNthCalledWith(2, "Save in progress");
        releaseCreate?.();
    });

    it("rejects a crop upload while first create owns the draft gate", async () => {
        const gate = createDraftAiSaveGate();
        const releaseCreate = gate.tryBeginCreate(0);
        const post = jest.fn(async (): Promise<any> => undefined);
        const coverMessage = {
            role: "assistant",
            content: "Generated cover",
            thinking: false,
            messageId: "cover-message",
            tool: "cover",
            payload: { url: "/temporary/original.png" },
        } as ToolAwareAIContent;
        const mounted = mountHook(gate, post, undefined, undefined, [coverMessage]);

        await act(async () => {
            await mounted.getCropper().onOk("data:image/png;base64,cropped");
        });

        expect(post).not.toHaveBeenCalled();
        expect(mockMessageWarning).toHaveBeenCalledWith("Save in progress");
        releaseCreate?.();
    });

    it("holds the crop lease across upload and the id-zero payload update", async () => {
        const originalFetch = globalThis.fetch;
        const gate = createDraftAiSaveGate();
        const uploadRequest = deferred<any>();
        const payloadRequest = deferred<any>();
        const post = jest.fn(async (url?: string): Promise<any> => {
            if (url?.startsWith("/api/admin/upload")) {
                return uploadRequest.promise;
            }
            return payloadRequest.promise;
        });
        const coverMessage = {
            role: "assistant",
            content: "Generated cover",
            thinking: false,
            messageId: "cover-message",
            tool: "cover",
            payload: { url: "/temporary/original.png" },
        } as ToolAwareAIContent;
        const mounted = mountHook(gate, post, undefined, undefined, [coverMessage]);
        globalThis.fetch = jest.fn(async () => ({
            blob: async () => new Blob(["cover"], { type: "image/png" }),
        })) as never;

        try {
            act(() => mounted.getToolContent(coverMessage).onCropCover("/temporary/original.png"));
            let cropPromise: Promise<void>;
            act(() => {
                cropPromise = mounted.getCropper().onOk("data:image/png;base64,cropped");
            });
            await act(async () => flushRequest());

            expect(post.mock.calls[0][0]).toBe("/api/admin/upload?dir=ai-cover&temporary=true");
            expect(gate.tryBeginCreate(0)).toBeUndefined();
            mounted.rerender(42, [coverMessage]);

            await act(async () => {
                uploadRequest.resolve({ data: { error: 0, data: { url: "/temporary/cropped.png" } } });
                await flushRequest();
            });

            expect(post.mock.calls[1]).toEqual([
                "/api/admin/article/ai/message?id=0",
                {
                    messageId: "cover-message",
                    tool: "cover",
                    payload: { url: "/temporary/cropped.png" },
                },
            ]);
            expect(gate.getPendingAiCount()).toBe(1);
            expect(gate.tryBeginCreate(0)).toBeUndefined();

            await act(async () => {
                payloadRequest.resolve({ data: { error: 0, data: true } });
                await cropPromise!;
                await flushRequest();
            });

            expect(gate.getPendingAiCount()).toBe(0);
            const releaseCreate = gate.tryBeginCreate(0);
            expect(releaseCreate).toBeDefined();
            releaseCreate?.();
        } finally {
            globalThis.fetch = originalFetch;
        }
    });

    it("keeps initial, streaming, and final send callbacks on the starting article id", async () => {
        const gate = createDraftAiSaveGate();
        const request = deferred<any>();
        const post = jest.fn(async (_url?: string, _body?: unknown, _config?: AxiosPostConfig): Promise<any> => {
            void _url;
            void _body;
            void _config;
            return request.promise;
        });
        const mounted = mountHook(gate, post);

        act(() => mounted.getFooter().onSubmit("Route-bound request"));
        expect(mounted.onAiMessagesChange).toHaveBeenCalledTimes(1);
        mounted.rerender(42);

        const requestConfig = post.mock.calls[0][2];
        expect(requestConfig).toBeDefined();
        act(() => {
            requestConfig?.onDownloadProgress?.({
                event: {
                    target: {
                        responseText: 'data: {"content":"Streaming","messageId":"route-message"}\n\n',
                    },
                },
            });
        });
        await act(async () => {
            request.resolve({
                status: 200,
                data: 'data: {"content":"Final","messageId":"route-message"}\n\n',
            });
            await flushRequest();
        });

        expect(mounted.onAiMessagesChange.mock.calls.map(([, articleId]) => articleId)).toEqual([0, 0, 0]);
        expect(gate.getPendingAiCount()).toBe(0);
    });

    it("keeps a failed send callback on the starting article id", async () => {
        const gate = createDraftAiSaveGate();
        const request = deferred<any>();
        const post = jest.fn(async (): Promise<any> => request.promise);
        const mounted = mountHook(gate, post);

        act(() => mounted.getFooter().onSubmit("Fail after navigation"));
        mounted.rerender(42);
        await act(async () => {
            request.resolve({
                status: 503,
                data: 'event: ai-error\ndata: {"message":"Provider unavailable"}\n\n',
            });
            await flushRequest();
        });

        expect(mounted.onAiMessagesChange.mock.calls.map(([, articleId]) => articleId)).toEqual([0, 0]);
        expect(mockMessageError).toHaveBeenCalledWith("Provider unavailable");
        expect(gate.getPendingAiCount()).toBe(0);
    });
});
