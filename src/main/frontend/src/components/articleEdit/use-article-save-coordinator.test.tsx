import { act, SetStateAction, useSyncExternalStore } from "react";
import { createRoot, Root } from "react-dom/client";
import { afterEach, beforeEach, describe, expect, it, jest } from "@jest/globals";
import { AIContent } from "@editor/dist/ai/AIContentItem";
import { articleDataToState, articleSaveToCache } from "../../utils/article-cache";
import { getCacheByKey, removeCacheDataByKey } from "../../utils/cache";
import { disableExitTips } from "../../utils/helpers";
import { AIProviderType } from "../../type";
import { ArticleEditInfo, ArticleEditState } from "./index.types";
import { ToolAwareAIContent } from "./article-ai-assistant/article-ai-assistant.types";
import useArticleSaveCoordinator from "./use-article-save-coordinator";
import { createDraftAiSaveGate, DraftAiSaveGate } from "./draft-ai-save-gate";
import { ArticleDraftSyncTask } from "./draft-sync/use-article-draft-sync";

let mockOffline = false;
const mockPostPublish = jest.fn(async (uri?: string, article?: unknown): Promise<any> => {
    void uri;
    void article;
});
let mockTransparentPublishOptions: {
    onAiMessagesChange: (action: SetStateAction<AIContent[]>, articleId?: number) => void;
};
const mockPageCache = new Map<string, ArticleEditInfo>();
const mockArticlePost = jest.fn(async (uri?: string, article?: unknown, config?: unknown): Promise<any> => {
    void uri;
    void article;
    void config;
});
let mockDraftSyncOptions: { onRequestSync: (task: ArticleDraftSyncTask) => void };
let mockDraftSyncApi: Record<string, ReturnType<typeof jest.fn>> | undefined;

jest.mock("antd", () => ({
    Space: ({ children }: { children?: unknown }) => children,
}));

jest.mock("../../utils/constants", () => ({
    createUri: "/api/admin/article/create",
    updateUri: "/api/admin/article/update",
    getRes: () => ({
        articleEdit: {
            aiRequestPending: "Wait for the assistant request",
            assistant: { saveInProgress: "Save in progress" },
            coverApplySuccess: "Cover applied",
            editExitWithoutSave: "Unsaved",
            markdownImport: {
                createFailed: "Imported draft failed",
                createResultUnknown: "Imported draft result unknown",
                offlineCreateUnavailable: "Reconnect before importing",
                waitForCurrentSave: "Wait for the current save",
            },
            publishReview: { offline: "Reconnect before publishing" },
            requireTitle: "Title required",
            requireType: "Category required",
            saveFailed: "Save failed",
        },
        error: { unknown: "Unknown error" },
    }),
}));

jest.mock("../../utils/env-utils", () => ({ isOffline: () => mockOffline }));
jest.mock("../../utils/article-cache", () => ({
    articleDataToState: require("@jest/globals").jest.fn(),
    articleSaveToCache: require("@jest/globals").jest.fn(),
    getArticleDraftSyncState: require("@jest/globals").jest.fn(),
    removeArticleCache: require("@jest/globals").jest.fn(),
    removeLocalArticleCache: require("@jest/globals").jest.fn(),
}));
jest.mock("../../utils/helpers", () => ({
    deepEqualWithSpecialJSON: () => true,
    disableExitTips: require("@jest/globals").jest.fn(),
    enableExitTips: require("@jest/globals").jest.fn(),
    updateDocumentTitle: require("@jest/globals").jest.fn(),
}));
jest.mock("../../utils/cache", () => ({
    getCacheByKey: require("@jest/globals").jest.fn(),
    getPageDataCacheKeyByPath: (pathname: string, search: string) => {
        const normalizedSearch = new URLSearchParams(search.startsWith("?") ? search.substring(1) : search).toString();
        return pathname + (normalizedSearch ? `?${normalizedSearch}` : "");
    },
    removeCacheDataByKey: require("@jest/globals").jest.fn((cacheKey: string) => mockPageCache.delete(cacheKey)),
}));
jest.mock("./draft-sync/article-draft-sync-helpers", () => ({
    isRetryableArticleSyncError: () => false,
    mergeArticleSynchronizationMetadata: (article: unknown) => article,
}));
jest.mock("./draft-sync/use-article-draft-sync", () => {
    const createApi = () => ({
        applyPatch: require("@jest/globals").jest.fn(),
        discard: require("@jest/globals").jest.fn(),
        markBlocked: require("@jest/globals").jest.fn(),
        markCommitted: require("@jest/globals").jest.fn(),
        markConflict: require("@jest/globals").jest.fn(),
        markDeferred: require("@jest/globals").jest.fn(),
        markFailed: require("@jest/globals").jest.fn(),
        markSynced: require("@jest/globals").jest.fn(),
        markSyncing: require("@jest/globals").jest.fn(() => true),
        resolveConflict: require("@jest/globals").jest.fn(),
    });
    return {
        __esModule: true,
        default: (options: { onRequestSync: (task: ArticleDraftSyncTask) => void }) => {
            mockDraftSyncOptions = options;
            mockDraftSyncApi ||= createApi();
            return mockDraftSyncApi;
        },
    };
});
jest.mock("./use-transparent-publish", () => ({
    __esModule: true,
    default: (options: typeof mockTransparentPublishOptions) => {
        mockTransparentPublishOptions = options;
        return mockPostPublish;
    },
}));
jest.mock("@editor/dist/editor/utils/marked-utils", () => ({
    markdownToHtml: require("@jest/globals").jest.fn(async () => "<p>Body</p>"),
}));

const initialArticle = {
    logId: 7,
    title: "Draft article",
    typeId: 1,
    markdown: "Body",
    content: "<p>Body</p>",
    rubbish: true,
    privacy: false,
    version: 3,
};

const createState = (articleEditInfo: ArticleEditInfo): ArticleEditState => ({
    typeOptions: [{ value: 1, label: "General" }],
    tags: [],
    aiProvider: AIProviderType.OPEN_AI,
    aiConfigured: false,
    aiMessages: articleEditInfo.aiMessages,
    linkPreviewEnabled: false,
    publishCheckEnabled: false,
    articleCoverAspectRatio: "16:9",
    articleEditAutoSaveInterval: 5,
    rubbish: true,
    editorVersion: 3,
    contentSource: "server",
    article: { ...articleEditInfo.article },
    saving: {
        rubbishSaving: false,
        previewIng: false,
        autoSaving: false,
        releaseSaving: false,
    },
});

const data: ArticleEditInfo = {
    article: { ...initialArticle },
    types: [{ id: 1, typeName: "General" }],
    tags: [],
    aiProvider: AIProviderType.OPEN_AI,
    aiConfigured: false,
    aiMessages: [],
};

const reactActEnvironment = globalThis as typeof globalThis & {
    IS_REACT_ACT_ENVIRONMENT?: boolean;
};

type Deferred<T> = {
    promise: Promise<T>;
    resolve: (value: T) => void;
    reject: (reason: unknown) => void;
};

const deferred = <T,>(): Deferred<T> => {
    let resolve!: (value: T) => void;
    let reject!: (reason: unknown) => void;
    const promise = new Promise<T>((resolvePromise, rejectPromise) => {
        resolve = resolvePromise;
        reject = rejectPromise;
    });
    return { promise, resolve, reject };
};

describe("useArticleSaveCoordinator publish outcomes", () => {
    let container: HTMLDivElement;
    let root: Root;
    let coordinator: ReturnType<typeof useArticleSaveCoordinator>;
    let messageApi: {
        error: ReturnType<typeof jest.fn>;
        info: ReturnType<typeof jest.fn>;
        success: ReturnType<typeof jest.fn>;
        warning: ReturnType<typeof jest.fn>;
    };
    let modal: { error: ReturnType<typeof jest.fn> };
    let migrateUiStateToArticle: ReturnType<typeof jest.fn>;
    let navigate: ReturnType<typeof jest.fn>;
    let harnessData: ArticleEditInfo;
    let harnessLocation: { pathname: string; search: string };
    let updateCache: (cache: ArticleEditInfo, cacheKey: string) => void;
    let draftAiSaveGate: DraftAiSaveGate;

    const Harness = () => {
        const draftAiPendingCount = useSyncExternalStore(
            draftAiSaveGate.subscribe,
            draftAiSaveGate.getPendingAiCount,
            draftAiSaveGate.getPendingAiCount
        );
        coordinator = useArticleSaveCoordinator({
            aliasRef: { current: null },
            axiosInstance: { post: mockArticlePost } as never,
            data: harnessData,
            draftAiPendingCount,
            draftAiSaveGate,
            digestRef: { current: null },
            editCardRef: { current: container },
            location: harnessLocation as never,
            messageApi: messageApi as never,
            modal: modal as never,
            navigate: navigate as never,
            offline: false,
            migrateUiStateToArticle,
            restoreUiState: jest.fn(),
            updateCache,
            updatePublishStatus: jest.fn(),
        });
        return null;
    };

    const remountWith = (nextData: ArticleEditInfo, search = "") => {
        act(() => root.unmount());
        harnessData = nextData;
        harnessLocation = { pathname: "/article-edit", search };
        mockDraftSyncApi = undefined;
        window.history.replaceState({}, "", `/article-edit${search}`);
        root = createRoot(container);
        act(() => root.render(<Harness />));
    };

    beforeEach(() => {
        mockOffline = false;
        mockPostPublish.mockReset();
        mockArticlePost.mockReset();
        mockDraftSyncApi = undefined;
        mockPageCache.clear();
        jest.mocked(articleSaveToCache).mockReset();
        jest.mocked(disableExitTips).mockReset();
        jest.mocked(articleDataToState).mockImplementation((articleEditInfo) => createState(articleEditInfo));
        jest.mocked(getCacheByKey).mockImplementation((cacheKey) => mockPageCache.get(cacheKey));
        jest.mocked(removeCacheDataByKey).mockImplementation((cacheKey) => {
            mockPageCache.delete(cacheKey);
        });
        harnessData = data;
        harnessLocation = { pathname: "/article-edit", search: "?id=7" };
        updateCache = jest.fn((cache, cacheKey) => {
            mockPageCache.set(cacheKey, cache);
        });
        mockPageCache.set("/article-edit?id=7", data);
        window.history.replaceState({}, "", "/article-edit?id=7");
        messageApi = { error: jest.fn(), info: jest.fn(), success: jest.fn(), warning: jest.fn() };
        modal = { error: jest.fn() };
        migrateUiStateToArticle = jest.fn();
        navigate = jest.fn();
        draftAiSaveGate = createDraftAiSaveGate();
        reactActEnvironment.IS_REACT_ACT_ENVIRONMENT = true;
        container = document.createElement("div");
        document.body.appendChild(container);
        root = createRoot(container);
        act(() => root.render(<Harness />));
    });

    afterEach(() => {
        act(() => root.unmount());
        container.remove();
        reactActEnvironment.IS_REACT_ACT_ENVIRONMENT = false;
        window.history.replaceState({}, "", "/");
    });

    it("preserves the draft state when publishing returns a business error", async () => {
        mockPostPublish.mockResolvedValueOnce({ error: 9001, message: "Database rejected the update" });

        let saved = true;
        await act(async () => {
            saved = await coordinator.onSubmit(coordinator.state.article, true, false, false);
        });

        expect(saved).toBe(false);
        expect(coordinator.state.rubbish).toBe(true);
        expect(coordinator.state.article.rubbish).toBe(true);
        expect(coordinator.state.saving.releaseSaving).toBe(false);
        expect(modal.error).toHaveBeenCalledTimes(1);
    });

    it("preserves the draft state when transparent publishing throws", async () => {
        mockPostPublish.mockRejectedValueOnce(new Error("Connection closed"));

        let saved = true;
        await act(async () => {
            saved = await coordinator.onSubmit(coordinator.state.article, true, false, false);
        });

        expect(saved).toBe(false);
        expect(coordinator.state.rubbish).toBe(true);
        expect(coordinator.state.article.rubbish).toBe(true);
        expect(coordinator.state.saving.releaseSaving).toBe(false);
    });

    it("updates the article to published only after a successful response", async () => {
        mockPostPublish.mockResolvedValueOnce({
            error: 0,
            message: "Published",
            data: {
                article: {
                    ...initialArticle,
                    rubbish: false,
                    version: 4,
                    previewUrl: "//blog.example.com/draft-article?v=4",
                },
            },
        });

        let saved = false;
        await act(async () => {
            saved = await coordinator.onSubmit(coordinator.state.article, true, false, false);
        });

        expect(saved).toBe(true);
        expect(coordinator.state.rubbish).toBe(false);
        expect(coordinator.state.article.rubbish).toBe(false);
        expect(coordinator.state.article.version).toBe(4);
    });

    it("migrates the editor UI scope before navigating a newly published article", async () => {
        const publishCheckMessage = {
            role: "assistant",
            content: "Publish check result",
            messageId: "publish-check-before-navigation",
        } as ToolAwareAIContent;
        mockPostPublish.mockImplementationOnce(async () => {
            mockTransparentPublishOptions.onAiMessagesChange((current) => [...current, publishCheckMessage], 42);
            return {
                error: 0,
                message: "Published",
                data: {
                    article: {
                        ...initialArticle,
                        logId: 42,
                        rubbish: false,
                        version: 1,
                        previewUrl: "//blog.example.com/new-article?v=1",
                    },
                    aiMessages: [],
                },
            };
        });

        await act(async () => {
            await coordinator.onSubmit({ ...coordinator.state.article, logId: undefined }, true, false, false);
        });

        expect(migrateUiStateToArticle).toHaveBeenCalledWith(42);
        expect(navigate).toHaveBeenCalledWith("/article-edit?id=42", { replace: true });
        expect(migrateUiStateToArticle.mock.invocationCallOrder[0]).toBeLessThan(navigate.mock.invocationCallOrder[0]);
        expect(
            mockPageCache
                .get("/article-edit?id=42")
                ?.aiMessages.map((message) => (message as ToolAwareAIContent).messageId)
        ).toEqual(["publish-check-before-navigation"]);
    });

    it("applies a late publish-check update to the mounted article route without losing newer messages", () => {
        const updateFromStartedPublish = mockTransparentPublishOptions.onAiMessagesChange;

        act(() => root.unmount());
        const existingMessage = {
            role: "assistant",
            content: "Existing message",
            messageId: "existing-message",
        } as ToolAwareAIContent;
        const laterMessage = {
            role: "user",
            content: "Question added after publish",
            messageId: "later-message",
        } as ToolAwareAIContent;
        const publishCheckMessage = {
            role: "assistant",
            content: "Publish check result",
            messageId: "publish-check-message",
        } as ToolAwareAIContent;
        harnessData = {
            ...data,
            article: { ...data.article, logId: 42 },
            aiMessages: [existingMessage],
        };
        harnessLocation = { pathname: "/article-edit", search: "?id=42" };
        mockPageCache.set("/article-edit?id=42", harnessData);
        window.history.replaceState({}, "", "/article-edit?id=42");
        root = createRoot(container);
        act(() => root.render(<Harness />));

        act(() => coordinator.updateAiMessageCache([existingMessage, laterMessage]));
        act(() => updateFromStartedPublish((current) => [...current, publishCheckMessage], 42));

        expect(coordinator.state.aiMessages.map((message) => (message as ToolAwareAIContent).messageId)).toEqual([
            "existing-message",
            "later-message",
            "publish-check-message",
        ]);
        expect(
            mockPageCache
                .get("/article-edit?id=42")
                ?.aiMessages.map((message) => (message as ToolAwareAIContent).messageId)
        ).toEqual(["existing-message", "later-message", "publish-check-message"]);
    });

    it("keeps a request callback bound to its starting route when the browser route changes", () => {
        const draftData = {
            ...data,
            article: { ...initialArticle, logId: undefined, version: -1 },
            aiMessages: [],
        };
        remountWith(draftData, "?intent=create&typeId=3");
        const updateFromDraftRequest = coordinator.updateAiMessageCache;
        const draftMessage = {
            role: "assistant",
            content: "Draft response",
            messageId: "draft-route-message",
        } as ToolAwareAIContent;

        window.history.replaceState({}, "", "/article-edit?id=42&intent=edit&typeId=9");
        act(() => updateFromDraftRequest([draftMessage], 0));

        expect(jest.mocked(updateCache)).toHaveBeenCalledWith(
            expect.objectContaining({ aiMessages: [draftMessage] }),
            "/article-edit?intent=create&typeId=3"
        );
        expect(mockPageCache.has("/article-edit?id=42&intent=edit&typeId=9")).toBe(false);
    });

    it("does not revive an old AI message store after leaving and clearing the page cache", () => {
        const staleMessage = {
            role: "assistant",
            content: "Stale session message",
            thinking: false,
            messageId: "stale-message",
        } as ToolAwareAIContent;
        const freshMessage = {
            role: "assistant",
            content: "Fresh server message",
            thinking: false,
            messageId: "fresh-message",
        } as ToolAwareAIContent;

        act(() => coordinator.updateAiMessageCache([staleMessage]));
        act(() => root.unmount());
        mockPageCache.delete("/article-edit?id=7");
        harnessData = { ...data, aiMessages: [freshMessage] };
        root = createRoot(container);
        act(() => root.render(<Harness />));

        expect(coordinator.state.aiMessages).toEqual([freshMessage]);
    });

    it("does not report success or cache a published state when connectivity is lost before submit", async () => {
        mockOffline = true;

        let saved = true;
        await act(async () => {
            saved = await coordinator.onSubmit(coordinator.state.article, true, false, false);
        });

        expect(saved).toBe(false);
        expect(coordinator.state.rubbish).toBe(true);
        expect(coordinator.state.article.rubbish).toBe(true);
        expect(articleSaveToCache).not.toHaveBeenCalled();
        expect(mockPostPublish).not.toHaveBeenCalled();
        expect(messageApi.error).toHaveBeenCalledWith("Reconnect before publishing");
    });

    it("rejects an imported draft create while draft AI is pending without sending a request", async () => {
        let releaseAi: (() => void) | undefined;
        act(() => {
            releaseAi = draftAiSaveGate.tryBeginAiRequest(0);
        });

        let created = true;
        await act(async () => {
            created = await coordinator.createImportedDraft({
                ...initialArticle,
                logId: undefined,
                title: "Imported draft",
            });
        });

        expect(created).toBe(false);
        expect(mockArticlePost).not.toHaveBeenCalled();
        expect(messageApi.warning).toHaveBeenCalledWith("Wait for the assistant request");
        act(() => releaseAi?.());
    });

    it("holds the draft create lease for an imported draft until its request succeeds", async () => {
        const request = deferred<any>();
        mockArticlePost.mockImplementationOnce(async () => request.promise);

        let createPromise: Promise<boolean>;
        act(() => {
            createPromise = coordinator.createImportedDraft({
                ...initialArticle,
                logId: undefined,
                title: "Imported draft",
            });
        });

        expect(mockArticlePost).toHaveBeenCalledWith(
            "/api/admin/article/create",
            expect.objectContaining({
                title: "Imported draft",
                preserveDraftAiMessages: true,
            }),
            { showError: false }
        );
        expect(draftAiSaveGate.tryBeginAiRequest(0)).toBeUndefined();
        const existingArticleAiRelease = draftAiSaveGate.tryBeginAiRequest(7);
        expect(existingArticleAiRelease).toBeDefined();
        existingArticleAiRelease?.();

        await act(async () => {
            request.resolve({
                data: {
                    error: 0,
                    data: {
                        article: { ...initialArticle, title: "Imported draft", logId: 42, version: 0 },
                        aiMessages: [],
                    },
                },
            });
            expect(await createPromise!).toBe(true);
        });

        let releaseAi: (() => void) | undefined;
        act(() => {
            releaseAi = draftAiSaveGate.tryBeginAiRequest(0);
        });
        expect(releaseAi).toBeDefined();
        act(() => {
            releaseAi?.();
            releaseAi?.();
        });
    });

    it.each([
        {
            name: "business failure",
            settle: (request: Deferred<any>) =>
                request.resolve({ data: { error: 1, message: "Rejected", data: undefined } }),
            expectedMessage: "Rejected",
        },
        {
            name: "request failure",
            settle: (request: Deferred<any>) => request.reject(new Error("Connection failed")),
            expectedMessage: "Imported draft result unknown",
        },
    ])("releases an imported draft create lease after $name", async ({ settle, expectedMessage }) => {
        const request = deferred<any>();
        mockArticlePost.mockImplementationOnce(async () => request.promise);

        let createPromise: Promise<boolean>;
        act(() => {
            createPromise = coordinator.createImportedDraft({
                ...initialArticle,
                logId: undefined,
                title: "Imported draft",
            });
        });
        expect(draftAiSaveGate.tryBeginAiRequest(0)).toBeUndefined();

        await act(async () => {
            settle(request);
            expect(await createPromise!).toBe(false);
        });

        expect(messageApi.error).toHaveBeenCalledWith(expectedMessage);
        let releaseAi: (() => void) | undefined;
        act(() => {
            releaseAi = draftAiSaveGate.tryBeginAiRequest(0);
        });
        expect(releaseAi).toBeDefined();
        act(() => releaseAi?.());
    });

    it("releases and restores imported-draft coordination when navigation throws", async () => {
        mockArticlePost.mockResolvedValueOnce({
            data: {
                error: 0,
                data: {
                    article: { ...initialArticle, title: "Imported draft", logId: 42, version: 0 },
                    aiMessages: [],
                },
            },
        });
        navigate.mockImplementationOnce(() => {
            throw new Error("Navigation failed");
        });

        await act(async () => {
            expect(
                await coordinator.createImportedDraft({
                    ...initialArticle,
                    logId: undefined,
                    title: "Imported draft",
                })
            ).toBe(false);
        });

        expect(messageApi.error).toHaveBeenCalledWith("Imported draft result unknown");
        let releaseAi: (() => void) | undefined;
        act(() => {
            releaseAi = draftAiSaveGate.tryBeginAiRequest(0);
        });
        expect(releaseAi).toBeDefined();
        act(() => releaseAi?.());
    });

    it("holds a draft AI lease while applying a generated cover and releases it on success", async () => {
        const draftData = {
            ...data,
            article: { ...initialArticle, logId: undefined, version: -1 },
        };
        remountWith(draftData);
        const request = deferred<any>();
        mockArticlePost.mockImplementationOnce(async () => request.promise);

        let coverPromise: Promise<string | undefined>;
        act(() => {
            coverPromise = coordinator.applyGeneratedCover({
                dataUrl: "data:image/png;base64,cover",
                extension: "png",
                messageId: "cover-message",
            });
        });

        expect(mockArticlePost).toHaveBeenCalledWith("/api/admin/article/cover/apply?id=0", {
            dataUrl: "data:image/png;base64,cover",
            extension: "png",
            messageId: "cover-message",
        });
        expect(draftAiSaveGate.tryBeginCreate(0)).toBeUndefined();

        await act(async () => {
            request.resolve({ data: { error: 0, data: { url: "/attached/cover.png" } } });
            expect(await coverPromise!).toBe("/attached/cover.png");
        });

        const releaseCreate = draftAiSaveGate.tryBeginCreate(0);
        expect(releaseCreate).toBeDefined();
        releaseCreate?.();
    });

    it("releases a generated-cover draft lease after failure and rejects cover writes during create", async () => {
        const draftData = {
            ...data,
            article: { ...initialArticle, logId: undefined, version: -1 },
        };
        remountWith(draftData);
        const request = deferred<any>();
        mockArticlePost.mockImplementationOnce(async () => request.promise);

        let coverPromise: Promise<string | undefined>;
        act(() => {
            coverPromise = coordinator.applyGeneratedCover({ dataUrl: "data:image/png;base64,cover" });
        });
        await act(async () => {
            request.reject(new Error("Cover request failed"));
            expect(await coverPromise!).toBeUndefined();
        });

        const releaseCreate = draftAiSaveGate.tryBeginCreate(0);
        expect(releaseCreate).toBeDefined();
        const callsBeforeBlockedCover = mockArticlePost.mock.calls.length;
        await act(async () => {
            expect(await coordinator.applyGeneratedCover({ dataUrl: "data:image/png;base64,blocked" })).toBeUndefined();
        });
        expect(mockArticlePost).toHaveBeenCalledTimes(callsBeforeBlockedCover);
        expect(messageApi.warning).toHaveBeenCalledWith("Save in progress");
        releaseCreate?.();
    });

    it("does not create a new article while a draft AI request is pending", async () => {
        const draftData = {
            ...data,
            article: { ...initialArticle, logId: undefined, version: -1 },
        };
        remountWith(draftData);
        let releaseAi: (() => void) | undefined;
        act(() => {
            releaseAi = draftAiSaveGate.tryBeginAiRequest(0);
        });

        let saved = true;
        await act(async () => {
            saved = await coordinator.onSubmit(coordinator.state.article, false, false, false);
        });

        expect(saved).toBe(false);
        expect(mockArticlePost).not.toHaveBeenCalled();
        expect(mockPostPublish).not.toHaveBeenCalled();
        expect(messageApi.error).toHaveBeenCalledWith("Wait for the assistant request");
        act(() => releaseAi?.());
    });

    it("holds the synchronous create gate before rendering content or starting the request", async () => {
        const draftData = {
            ...data,
            article: { ...initialArticle, logId: undefined, version: -1 },
        };
        remountWith(draftData);
        mockArticlePost.mockResolvedValueOnce({
            data: {
                error: 0,
                message: "Saved",
                data: { article: { ...draftData.article, logId: 42, version: 0 }, aiMessages: [] },
            },
        });

        let savePromise: Promise<boolean>;
        act(() => {
            savePromise = coordinator.onSubmit(coordinator.state.article, false, false, false);
        });

        expect(draftAiSaveGate.tryBeginAiRequest(0)).toBeUndefined();
        await act(async () => {
            expect(await savePromise!).toBe(true);
        });
        expect(mockArticlePost).toHaveBeenCalledTimes(1);
    });

    it("replays only the latest queued auto-save after all draft AI requests finish", async () => {
        jest.useFakeTimers();
        try {
            const draftData = {
                ...data,
                article: { ...initialArticle, logId: undefined, version: -1 },
            };
            remountWith(draftData);
            mockArticlePost.mockResolvedValueOnce({
                data: {
                    error: 0,
                    message: "Saved",
                    data: {
                        article: { ...draftData.article, title: "Latest revision", logId: 42, version: 0 },
                        aiMessages: [],
                    },
                },
            });
            let releaseFirst: (() => void) | undefined;
            let releaseSecond: (() => void) | undefined;
            act(() => {
                releaseFirst = draftAiSaveGate.tryBeginAiRequest(0);
                releaseSecond = draftAiSaveGate.tryBeginAiRequest(0);
                mockDraftSyncOptions.onRequestSync({
                    article: { ...draftData.article, title: "Older revision" },
                    revision: 1,
                });
                mockDraftSyncOptions.onRequestSync({
                    article: { ...draftData.article, title: "Latest revision" },
                    revision: 2,
                });
            });

            await act(async () => {
                jest.advanceTimersByTime(5000);
                await Promise.resolve();
            });
            expect(mockArticlePost).not.toHaveBeenCalled();
            expect(mockDraftSyncApi?.markSyncing).not.toHaveBeenCalled();
            expect(disableExitTips).not.toHaveBeenCalled();

            act(() => releaseFirst?.());
            expect(mockArticlePost).not.toHaveBeenCalled();
            act(() => releaseSecond?.());
            await act(async () => {
                jest.advanceTimersByTime(5000);
                await Promise.resolve();
                await Promise.resolve();
            });

            expect(mockArticlePost).toHaveBeenCalledTimes(1);
            expect(mockArticlePost.mock.calls[0][1]).toMatchObject({ title: "Latest revision" });
            expect(mockDraftSyncApi?.markSyncing).toHaveBeenCalledWith(expect.objectContaining({ revision: 2 }));
            expect(mockDraftSyncApi?.markDeferred).not.toHaveBeenCalled();
        } finally {
            jest.useRealTimers();
        }
    });

    it("keeps existing article saves independent from the draft gate", async () => {
        const releaseDraftCreate = draftAiSaveGate.tryBeginCreate(0);
        mockArticlePost.mockResolvedValueOnce({
            data: {
                error: 0,
                message: "Saved",
                data: { article: { ...initialArticle, version: 4 }, aiMessages: [] },
            },
        });

        let saved = false;
        await act(async () => {
            saved = await coordinator.onSubmit(coordinator.state.article, false, false, false);
        });

        expect(saved).toBe(true);
        expect(mockArticlePost).toHaveBeenCalledWith(
            "/api/admin/article/update",
            expect.objectContaining({ logId: 7 }),
            undefined
        );
        releaseDraftCreate?.();
    });

    it("moves draft AI messages to the created article and clears the draft page scope", async () => {
        const draftMessage = {
            role: "assistant",
            content: "Draft answer",
            messageId: "draft-message",
        } as ToolAwareAIContent;
        const publishCheckMessage = {
            role: "assistant",
            content: "Publish check",
            messageId: "publish-check-message",
        } as ToolAwareAIContent;
        const draftData = {
            ...data,
            article: { ...initialArticle, logId: undefined, version: -1 },
            aiMessages: [draftMessage],
        };
        mockPageCache.set("/article-edit", draftData);
        remountWith(draftData);
        mockArticlePost.mockImplementationOnce(async () => {
            coordinator.updateAiMessageCache([publishCheckMessage], 42);
            return {
                data: {
                    error: 0,
                    message: "Saved",
                    data: {
                        article: { ...draftData.article, logId: 42, version: 0 },
                        aiMessages: [draftMessage],
                    },
                },
            };
        });

        await act(async () => {
            await coordinator.onSubmit(coordinator.state.article, false, false, false);
        });

        expect(removeCacheDataByKey).toHaveBeenCalledWith("/article-edit");
        expect(jest.mocked(updateCache).mock.calls.map(([, cacheKey]) => cacheKey)).toEqual([
            "/article-edit?id=42",
            "/article-edit?id=42",
        ]);
        expect(mockPageCache.has("/article-edit")).toBe(false);
        expect(
            mockPageCache
                .get("/article-edit?id=42")
                ?.aiMessages.map((message) => (message as ToolAwareAIContent).messageId)
        ).toEqual(["draft-message", "publish-check-message"]);

        const nextDraftData = { ...draftData, aiMessages: [] };
        remountWith(nextDraftData);
        expect(coordinator.state.aiMessages).toEqual([]);
    });

    it("reconciles id-less draft questions with server UUIDs one-for-one without collapsing repeated text", async () => {
        const firstLocalQuestion = {
            role: "user",
            content: "Please review this draft",
            thinking: false,
        } as ToolAwareAIContent;
        const secondLocalQuestion = {
            role: "user",
            content: "Please review this draft",
            thinking: false,
        } as ToolAwareAIContent;
        const draftData = {
            ...data,
            article: { ...initialArticle, logId: undefined, version: -1 },
            aiMessages: [firstLocalQuestion, secondLocalQuestion],
        };
        const sourceCacheKey = "/article-edit?intent=duplicate-merge";
        mockPageCache.set(sourceCacheKey, draftData);
        remountWith(draftData, "?intent=duplicate-merge");
        mockArticlePost.mockResolvedValueOnce({
            data: {
                error: 0,
                message: "Saved",
                data: {
                    article: { ...draftData.article, logId: 42, version: 0 },
                    aiMessages: [
                        {
                            role: "user",
                            content: "Please review this draft",
                            messageId: "server-question-1",
                        },
                        {
                            role: "user",
                            content: "Please review this draft",
                            messageId: "server-question-2",
                        },
                    ] as ToolAwareAIContent[],
                },
            },
        });

        await act(async () => {
            expect(await coordinator.onSubmit(coordinator.state.article, false, false, false)).toBe(true);
        });

        const migratedEntry = Array.from(mockPageCache.entries()).find(([cacheKey]) => cacheKey.includes("id=42"));
        const migratedMessages = migratedEntry?.[1].aiMessages as ToolAwareAIContent[];
        expect(migratedMessages).toHaveLength(2);
        expect(migratedMessages.map(({ content }) => content)).toEqual([
            "Please review this draft",
            "Please review this draft",
        ]);
        expect(migratedMessages.map(({ messageId }) => messageId)).toEqual(["server-question-1", "server-question-2"]);
        expect(mockPageCache.has(sourceCacheKey)).toBe(false);
    });
});
