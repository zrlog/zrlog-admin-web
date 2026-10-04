import useArticleFieldAi from "./use-article-field-ai";
import { articleContextRevision } from "./article-ai-assistant/article-ai-skill-contract";
import { act, SetStateAction, useSyncExternalStore } from "react";
import { createRoot, Root } from "react-dom/client";
import { afterEach, beforeEach, describe, expect, it, jest } from "@jest/globals";
import { AIContent } from "@zrlog/editor/dist/ai/AIContentItem";
import {
    articleDataToState,
    articleSaveToCache,
    removeArticleCache,
    getArticleDraftBase,
    getArticleDraftSyncState,
} from "../../utils/article-cache";
import { getCacheByKey, removeCacheDataByKey } from "../../utils/cache";
import { disableExitTips } from "../../utils/helpers";
import { AIProviderType } from "../../type";
import { ArticleEditInfo, ArticleEditState, ArticleEntry } from "./index.types";
import { ToolAwareAIContent } from "./article-ai-assistant/article-ai-assistant.types";
import useArticleSaveCoordinator from "./use-article-save-coordinator";
import useArticleEditorDocument from "./use-article-editor-document";
import { createDraftAiSaveGate, DraftAiSaveGate } from "./draft-ai-save-gate";
import { ArticleDraftSyncTask } from "./draft-sync/use-article-draft-sync";

let mockOffline = false;
let mockUseRealDraftSync = false;
const mockPostPublish = jest.fn(async (uri?: string, article?: unknown): Promise<any> => {
    void uri;
    void article;
});
let mockTransparentPublishOptions: {
    onAiMessagesChange: (action: SetStateAction<AIContent[]>, articleId?: number) => void;
};
const mockPageCache = new Map<string, ArticleEditInfo>();
const mockArticleGet = jest.fn<Promise<any>, any[]>();
const mockArticlePost = jest.fn(async (uri?: string, article?: unknown, config?: unknown): Promise<any> => {
    void uri;
    void article;
    void config;
});
let mockDraftSyncOptions: { onRequestSync: (task: ArticleDraftSyncTask) => void };
let mockDraftSyncApi: Record<string, ReturnType<typeof jest.fn>> | undefined;

// Use the package's real CommonJS build with CRA's Jest resolver.
jest.mock("@marijn/find-cluster-break", () => require("@marijn/find-cluster-break/dist/index.cjs"));

import { hasAction } from "../../utils/account-access";
jest.mock("../../utils/account-access", () => ({ hasAction: require("@jest/globals").jest.fn(() => true) }));

jest.mock("./version-sync/use-article-version-sync", () => ({ __esModule: true, default: () => ({}) }));

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
    restoreLocalArticleCache: require("@jest/globals").jest.fn(),
    getArticleDraftBase: require("@jest/globals").jest.fn(),
    getArticleDraftSyncState: require("@jest/globals").jest.fn(),
    removeArticleCache: require("@jest/globals").jest.fn(),
    removeLocalArticleCache: require("@jest/globals").jest.fn(),
}));
jest.mock("../../utils/helpers", () => ({
    deepEqualWithSpecialJSON: (left: unknown, right: unknown) =>
        mockUseRealDraftSync
            ? require("@jest/globals").jest.requireActual("../../utils/helpers").deepEqualWithSpecialJSON(left, right)
            : true,
    disableExitTips: require("@jest/globals").jest.fn(),
    enableExitTips: require("@jest/globals").jest.fn(),
    updateDocumentTitle: require("@jest/globals").jest.fn(),
}));
jest.mock("../../utils/cache", () => ({
    getCachedData: () => require("@jest/globals").jest.requireActual("../../utils/cache").getCachedData(),
    putCache: (cache: unknown) => require("@jest/globals").jest.requireActual("../../utils/cache").putCache(cache),
    getCacheByKey: require("@jest/globals").jest.fn(),
    getPageDataCacheKeyByPath: (pathname: string, search: string) => {
        const normalizedSearch = new URLSearchParams(search.startsWith("?") ? search.substring(1) : search).toString();
        return pathname + (normalizedSearch ? `?${normalizedSearch}` : "");
    },
    removeCacheDataByKey: require("@jest/globals").jest.fn((cacheKey: string) => mockPageCache.delete(cacheKey)),
}));
jest.mock("./draft-sync/article-draft-sync-helpers", () => ({
    isRetryableArticleSyncError: () => false,
    mergeArticleSynchronizationMetadata: require("@jest/globals").jest.requireActual(
        "./draft-sync/article-draft-sync-helpers"
    ).mergeArticleSynchronizationMetadata,
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
        pauseForConflict: require("@jest/globals").jest.fn((article: ArticleEntry) => ({
            article,
            revision: 1,
            updatedAt: 100,
        })),
        markSyncing: require("@jest/globals").jest.fn(() => true),
        resolveConflict: require("@jest/globals").jest.fn(),
        receiveServerArticle: require("@jest/globals").jest.fn(),
    });
    return {
        __esModule: true,
        default: (options: { onRequestSync: (task: ArticleDraftSyncTask) => void }) => {
            if (mockUseRealDraftSync) {
                return require("@jest/globals")
                    .jest.requireActual("./draft-sync/use-article-draft-sync")
                    .default(options);
            }
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
jest.mock("@zrlog/editor/dist/editor/utils/marked-utils", () => ({
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
    let harnessLocation: { pathname: string; search: string; key: string };
    let updateCache: (cache: ArticleEditInfo, cacheKey: string) => void;
    let draftAiSaveGate: DraftAiSaveGate;
    let fieldAi: ReturnType<typeof useArticleFieldAi>;
    let editorDocument: ReturnType<typeof useArticleEditorDocument>;

    const Harness = () => {
        const draftAiPendingCount = useSyncExternalStore(
            draftAiSaveGate.subscribe,
            draftAiSaveGate.getPendingAiCount,
            draftAiSaveGate.getPendingAiCount
        );
        coordinator = useArticleSaveCoordinator({
            aliasRef: { current: null },
            axiosInstance: { post: mockArticlePost, get: mockArticleGet } as never,
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
        fieldAi = useArticleFieldAi({
            getCurrentArticle: coordinator.getCurrentArticle,
            onValuesChange: coordinator.handleValuesChange,
        });
        editorDocument = useArticleEditorDocument(
            coordinator.state.article.markdown,
            coordinator.restoreInputRevision,
            coordinator.handleValuesChange
        );
        return null;
    };

    const remountWith = (nextData: ArticleEditInfo, search = "") => {
        act(() => root.unmount());
        harnessData = nextData;
        harnessLocation = { pathname: "/article-edit", search, key: "draft-location" };
        mockDraftSyncApi = undefined;
        window.history.replaceState({}, "", `/article-edit${search}`);
        root = createRoot(container);
        act(() => root.render(<Harness />));
    };

    beforeEach(() => {
        jest.mocked(hasAction).mockReturnValue(true);
        mockOffline = false;
        mockUseRealDraftSync = false;
        mockPostPublish.mockReset();
        mockArticlePost.mockReset();
        mockArticleGet.mockReset();
        mockDraftSyncApi = undefined;
        mockPageCache.clear();
        localStorage.clear();
        jest.mocked(getArticleDraftBase).mockReset();
        jest.mocked(getArticleDraftSyncState).mockReset();
        jest.mocked(articleSaveToCache).mockReset();
        jest.mocked(removeArticleCache).mockReset();
        jest.mocked(disableExitTips).mockReset();
        jest.mocked(articleDataToState).mockImplementation((articleEditInfo) => createState(articleEditInfo));
        jest.mocked(getCacheByKey).mockImplementation((cacheKey) => mockPageCache.get(cacheKey));
        jest.mocked(removeCacheDataByKey).mockImplementation((cacheKey) => {
            mockPageCache.delete(cacheKey);
        });
        harnessData = data;
        harnessLocation = { pathname: "/article-edit", search: "?id=7", key: "article-location" };
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
        jest.useRealTimers();
    });

    const enableRealCacheFeedback = () => {
        const realCache = jest.requireActual("../../utils/article-cache") as typeof import("../../utils/article-cache");
        jest.mocked(articleDataToState).mockImplementation(realCache.articleDataToState);
        jest.mocked(articleSaveToCache).mockImplementation(realCache.articleSaveToCache);
        jest.mocked(removeArticleCache).mockImplementation(realCache.removeArticleCache);
        jest.mocked(getArticleDraftBase).mockImplementation(realCache.getArticleDraftBase);
        jest.mocked(getArticleDraftSyncState).mockImplementation(realCache.getArticleDraftSyncState);
        updateCache = (cache, key) => {
            mockPageCache.set(key, cache);
            harnessData = cache;
        };
        return realCache;
    };

    it.each([false, true])(
        "autosaves ordinary body edits as drafts without AI (reload before save: %s)",
        async (reloadBeforeSave) => {
            jest.useFakeTimers();
            mockUseRealDraftSync = true;
            const realCache = enableRealCacheFeedback();
            const publishedData = { ...data, article: { ...initialArticle, rubbish: false } };
            remountWith(publishedData, "?id=7");
            mockArticlePost.mockImplementation(async (_uri, body) => {
                const article = body as ArticleEntry;
                return { data: { error: 0, data: { ...data, article: { ...article, version: article.version + 1 } } } };
            });
            expect(coordinator.state.aiConfigured).toBe(false);
            expect(coordinator.state.rubbish).toBe(false);
            act(() => coordinator.handleValuesChange({ title: publishedData.article.title }));
            await act(async () => jest.advanceTimersByTime(5000));
            expect(mockArticlePost).not.toHaveBeenCalled();
            expect(coordinator.state.rubbish).toBe(false);
            act(() => editorDocument.onChange({ value: "Manual body", previewContent: "<p>Manual body</p>" }));
            expect(coordinator.state.contentSource).toBe("localEdit");
            expect(coordinator.state.rubbish).toBe(true);
            expect(realCache.getLocalArticleCaches()[0].article.rubbish).toBe(true);
            if (reloadBeforeSave) remountWith(publishedData, "?id=7");
            await act(async () => jest.advanceTimersByTime(5000));
            expect(mockArticlePost).toHaveBeenCalledTimes(1);
            expect(mockArticlePost.mock.calls[0][1]).toMatchObject({
                markdown: "Manual body",
                content: "<p>Manual body</p>",
                rubbish: true,
                transparentPublish: false,
            });
            expect(coordinator.state.contentSource).toBe("server");
            expect(coordinator.isSaving).toBe(false);
            expect(realCache.getLocalArticleCaches()).toEqual([]);
            act(() => editorDocument.onChange({ value: "Second edit", previewContent: "<p>Second edit</p>" }));
            await act(async () => jest.advanceTimersByTime(5000));
            expect(mockArticlePost).toHaveBeenCalledTimes(2);
            expect(mockArticlePost.mock.calls[1][1]).toMatchObject({
                markdown: "Second edit",
                rubbish: true,
                version: 4,
            });
            expect(coordinator.state.contentSource).toBe("server");
            remountWith(harnessData, "?id=7");
            await act(async () => jest.advanceTimersByTime(5000));
            expect(mockArticlePost).toHaveBeenCalledTimes(2);
            expect(mockPostPublish).not.toHaveBeenCalled();
            expect(coordinator.state.article).toMatchObject({ markdown: "Second edit", rubbish: true, version: 5 });
        }
    );

    it("restores old unsynced published-article edits as a draft without publishing them", async () => {
        jest.useFakeTimers();
        mockUseRealDraftSync = true;
        const realCache = enableRealCacheFeedback();
        const published = { ...initialArticle, rubbish: false };
        realCache.articleSaveToCache({ ...published, markdown: "Previously unsynced body" });
        remountWith({ ...data, article: published }, "?id=7");
        mockArticlePost.mockImplementation(async (_uri, body) => {
            const article = body as ArticleEntry;
            return { data: { error: 0, data: { ...data, article: { ...article, version: article.version + 1 } } } };
        });
        expect(coordinator.state.rubbish).toBe(true);
        await act(async () => jest.advanceTimersByTime(5000));
        expect(mockArticlePost).toHaveBeenCalledTimes(1);
        expect(mockArticlePost.mock.calls[0][1]).toMatchObject({
            markdown: "Previously unsynced body",
            rubbish: true,
            transparentPublish: false,
        });
        expect(coordinator.state.contentSource).toBe("server");
        expect(coordinator.state.article.rubbish).toBe(true);
        expect(realCache.getLocalArticleCaches()).toEqual([]);
        expect(mockPostPublish).not.toHaveBeenCalled();
    });

    it.each([false, true])(
        "saves AI applications to a published article as drafts and keeps autosaving after reload (reload before save: %s)",
        async (reloadBeforeSave) => {
            jest.useFakeTimers();
            mockUseRealDraftSync = true;
            const realCache = enableRealCacheFeedback();
            const publishedData = { ...data, article: { ...initialArticle, rubbish: false } };
            mockPageCache.set("/article-edit?id=7", publishedData);
            remountWith(publishedData, "?id=7");
            mockArticlePost.mockImplementation(async (_uri, body) => {
                const article = body as ArticleEntry;
                return { data: { error: 0, data: { ...data, article: { ...article, version: article.version + 1 } } } };
            });
            const source: ToolAwareAIContent = {
                role: "assistant",
                content: "",
                thinking: false,
                messageType: "writingSkill",
                messageId: "run-1:skill:1:1",
                skillContract: {
                    version: 1,
                    contextRevision: articleContextRevision(publishedData.article),
                    applicableFields: ["title"],
                },
            };
            act(() => fieldAi.applyGeneratedValues({ title: "AI title" }, source));
            expect(coordinator.state.rubbish).toBe(true);
            expect(realCache.getLocalArticleCaches()[0].article.rubbish).toBe(true);
            // The assistant continues updating its conversation after applying a result.
            act(() => coordinator.updateAiMessageCache([{ role: "assistant", content: "Done", thinking: false }], 7));
            if (reloadBeforeSave) remountWith(harnessData, "?id=7");
            await act(async () => jest.advanceTimersByTime(5000));
            expect(mockArticlePost).toHaveBeenCalledTimes(1);
            expect(mockArticlePost.mock.calls[0][1]).toMatchObject({
                title: "AI title",
                rubbish: true,
                transparentPublish: false,
                version: 3,
            });
            expect(coordinator.state.contentSource).toBe("server");
            expect(coordinator.state.contentConflict).toBeUndefined();
            expect(coordinator.isSaving).toBe(false);
            expect(realCache.getLocalArticleCaches()).toEqual([]);

            act(() => coordinator.handleValuesChange({ markdown: "Manual body", content: "<p>Manual body</p>" }));
            await act(async () => jest.advanceTimersByTime(5000));
            expect(mockArticlePost).toHaveBeenCalledTimes(2);
            expect(mockArticlePost.mock.calls[1][1]).toMatchObject({
                title: "AI title",
                markdown: "Manual body",
                rubbish: true,
                transparentPublish: false,
                version: 4,
            });
            remountWith(harnessData, "?id=7");
            await act(async () => jest.advanceTimersByTime(5000));
            expect(mockArticlePost).toHaveBeenCalledTimes(2);
            expect(mockPostPublish).not.toHaveBeenCalled();
            expect(coordinator.state).toMatchObject({
                contentSource: "server",
                rubbish: true,
                article: { title: "AI title", markdown: "Manual body", rubbish: true, version: 5 },
            });
        }
    );

    it("keeps a queued AI application when the auto-save interval changes", async () => {
        jest.useFakeTimers();
        mockUseRealDraftSync = true;
        enableRealCacheFeedback();
        remountWith(data, "?id=7");
        mockArticlePost.mockImplementation(async (_uri, body) => {
            const article = body as ArticleEntry;
            return {
                data: { error: 0, data: { ...harnessData, article: { ...article, version: article.version + 1 } } },
            };
        });
        act(() => fieldAi.applyGeneratedValues({ title: "AI title" }));
        harnessData = { ...harnessData, articleEditAutoSaveInterval: 2 };
        act(() => root.render(<Harness />));
        await act(async () => jest.advanceTimersByTime(5000));
        expect(mockArticlePost).toHaveBeenCalledTimes(1);
        expect(coordinator.state.contentSource).toBe("server");
        expect(coordinator.isSaving).toBe(false);
    });

    it("preserves newer edits while an earlier autosave finishes", async () => {
        jest.useFakeTimers();
        mockUseRealDraftSync = true;
        const realCache = enableRealCacheFeedback();
        const publishedData = { ...data, article: { ...initialArticle, rubbish: false } };
        remountWith(publishedData, "?id=7");
        const earlierSave = deferred<any>();
        mockArticlePost.mockImplementationOnce(async () => earlierSave.promise);
        mockArticlePost.mockImplementation(async (_uri, body) => {
            const article = body as ArticleEntry;
            return { data: { error: 0, data: { ...data, article: { ...article, version: article.version + 1 } } } };
        });
        act(() => coordinator.handleValuesChange({ title: "Earlier edit" }));
        await act(async () => jest.advanceTimersByTime(5000));
        expect(mockArticlePost).toHaveBeenCalledTimes(1);
        act(() => fieldAi.applyGeneratedValues({ title: "AI title" }));
        await act(async () =>
            earlierSave.resolve({
                data: {
                    error: 0,
                    data: {
                        ...publishedData,
                        article: { ...publishedData.article, title: "Earlier edit", rubbish: true, version: 4 },
                    },
                },
            })
        );
        expect(coordinator.state).toMatchObject({
            contentSource: "localEdit",
            rubbish: true,
            article: { title: "AI title", rubbish: true, version: 4 },
        });
        expect(coordinator.isSaving).toBe(false);
        expect(coordinator.state.contentConflict).toBeUndefined();
        expect(realCache.getLocalArticleCaches()[0].article.rubbish).toBe(true);
        await act(async () => jest.advanceTimersByTime(5000));
        expect(mockArticlePost).toHaveBeenCalledTimes(2);
        expect(mockArticlePost.mock.calls[1][1]).toMatchObject({
            title: "AI title",
            rubbish: true,
            transparentPublish: false,
            version: 4,
        });
        expect(coordinator.state.contentSource).toBe("server");
        expect(realCache.getLocalArticleCaches()).toEqual([]);
    });

    it("clears the unsynced label with real draft storage and page cache feedback", async () => {
        jest.useFakeTimers();
        mockUseRealDraftSync = true;
        const realCache = enableRealCacheFeedback();
        remountWith(data, "?id=7");
        mockArticlePost.mockImplementation(async (_uri, body) => {
            const article = body as ArticleEntry;
            return { data: { error: 0, data: { ...data, article: { ...article, version: article.version + 1 } } } };
        });
        act(() => fieldAi.applyGeneratedValues({ title: "AI title" }));
        expect(coordinator.state.contentSource).toBe("localEdit");
        await act(async () => {
            jest.advanceTimersByTime(5000);
        });
        expect(mockArticlePost).toHaveBeenCalledTimes(1);
        expect(coordinator.state.contentSource).toBe("server");
        expect(coordinator.state.contentConflict).toBeUndefined();
        expect(realCache.getLocalArticleCaches()).toEqual([]);
        act(() => coordinator.updateAiMessageCache([{ role: "assistant", content: "Done", thinking: false }], 7));
        expect(coordinator.state.contentSource).toBe("server");
        expect(coordinator.state.contentConflict).toBeUndefined();
        act(() => coordinator.handleValuesChange({ markdown: "Manual body", content: "<p>Manual body</p>" }));
        await act(async () => {
            jest.advanceTimersByTime(5000);
        });
        expect(mockArticlePost).toHaveBeenCalledTimes(2);
        expect(coordinator.state.article).toMatchObject({ title: "AI title", markdown: "Manual body", version: 5 });
        expect(coordinator.state.contentSource).toBe("server");
        expect(realCache.getLocalArticleCaches()).toEqual([]);
        remountWith(harnessData, "?id=7");
        expect(coordinator.state.contentSource).toBe("server");
        expect(coordinator.state.contentConflict).toBeUndefined();
        expect(coordinator.state.article).toMatchObject({ title: "AI title", markdown: "Manual body", version: 5 });
    });

    it.each([false, true])(
        "saves all accepted sibling fields with monotonic versions (save between applications: %s)",
        async (saveBetween) => {
            jest.useFakeTimers();
            mockUseRealDraftSync = true;
            remountWith(data, "?id=7");
            mockArticlePost.mockImplementation(async (_uri, body) => {
                const article = body as ArticleEntry;
                return { data: { error: 0, data: { ...data, article: { ...article, version: article.version + 1 } } } };
            });
            const skill = (field: string, index: number): ToolAwareAIContent => ({
                role: "assistant",
                content: "",
                thinking: false,
                messageType: "writingSkill",
                messageId: `run-1:skill:1:${index}`,
                skillContract: {
                    version: 1,
                    contextRevision: articleContextRevision(initialArticle),
                    applicableFields: [field],
                },
            });
            act(() => fieldAi.applyGeneratedValues({ title: "AI title" }, skill("title", 1)));
            if (saveBetween) {
                await act(async () => {
                    jest.advanceTimersByTime(5000);
                });
                expect(mockArticlePost).toHaveBeenCalledTimes(1);
                expect(coordinator.state.article.version).toBe(4);
            }
            act(() => {
                fieldAi.applyGeneratedValues({ digest: "AI digest" }, skill("digest", 2));
                fieldAi.applyGeneratedValues({ keywords: "AI,writing" }, skill("keywords", 3));
                fieldAi.applyGeneratedValues({ markdown: "AI body" }, skill("markdown", 4));
            });
            await act(async () => {
                jest.advanceTimersByTime(5000);
            });
            expect(mockArticlePost).toHaveBeenLastCalledWith(
                "/api/admin/article/update",
                expect.objectContaining({
                    title: "AI title",
                    digest: "AI digest",
                    keywords: "AI,writing",
                    markdown: "AI body",
                    version: saveBetween ? 4 : 3,
                }),
                { showError: false }
            );
            expect(coordinator.state.article.version).toBe(saveBetween ? 5 : 4);
            expect(coordinator.state.contentSource).toBe("server");
            const writes = mockArticlePost.mock.calls.length;
            act(() => fieldAi.applyGeneratedValues({ keywords: "AI,writing" }, skill("keywords", 3)));
            await act(async () => {
                jest.advanceTimersByTime(5000);
            });
            expect(mockArticlePost).toHaveBeenCalledTimes(writes);
        }
    );

    it("retains sibling applications made while an earlier autosave is in flight", async () => {
        jest.useFakeTimers();
        mockUseRealDraftSync = true;
        const realCache = enableRealCacheFeedback();
        remountWith(data, "?id=7");
        const firstSave = deferred<any>();
        mockArticlePost.mockImplementationOnce(async () => firstSave.promise);
        mockArticlePost.mockImplementation(async (_uri, body) => {
            const article = body as ArticleEntry;
            return { data: { error: 0, data: { ...data, article: { ...article, version: article.version + 1 } } } };
        });
        const source: ToolAwareAIContent = {
            role: "assistant",
            content: "",
            thinking: false,
            messageType: "writingSkill",
            messageId: "run-1:skill:1:1",
            skillContract: {
                version: 1,
                contextRevision: articleContextRevision(initialArticle),
                applicableFields: ["title", "digest", "keywords"],
            },
        };
        act(() => fieldAi.applyGeneratedValues({ title: "AI title" }, source));
        await act(async () => {
            jest.advanceTimersByTime(5000);
        });
        expect(mockArticlePost).toHaveBeenCalledTimes(1);
        act(() => {
            fieldAi.applyGeneratedValues({ digest: "AI digest" }, { ...source, messageId: "run-1:skill:1:2" });
            fieldAi.applyGeneratedValues({ keywords: "AI,writing" }, { ...source, messageId: "run-1:skill:1:3" });
        });
        await act(async () => {
            firstSave.resolve({
                data: { error: 0, data: { ...data, article: { ...initialArticle, title: "AI title", version: 4 } } },
            });
        });
        expect(coordinator.getCurrentArticle()).toMatchObject({
            title: "AI title",
            digest: "AI digest",
            keywords: "AI,writing",
            version: 4,
        });
        await act(async () => {
            jest.advanceTimersByTime(5000);
        });
        expect(mockArticlePost).toHaveBeenLastCalledWith(
            "/api/admin/article/update",
            expect.objectContaining({
                title: "AI title",
                digest: "AI digest",
                keywords: "AI,writing",
                version: 4,
            }),
            { showError: false }
        );
        expect(coordinator.state.article.version).toBe(5);
        expect(coordinator.state.contentSource).toBe("server");
        expect(coordinator.state.contentConflict).toBeUndefined();
        expect(realCache.getLocalArticleCaches()).toEqual([]);
        remountWith(harnessData, "?id=7");
        expect(coordinator.state.contentConflict).toBeUndefined();
        expect(coordinator.state.article).toMatchObject({
            title: "AI title",
            digest: "AI digest",
            keywords: "AI,writing",
            version: 5,
        });
    });

    it("does not extend a run's application baseline when a conflict rejects the patch", async () => {
        mockUseRealDraftSync = true;
        remountWith(data, "?id=7");
        const source: ToolAwareAIContent = {
            role: "assistant",
            content: "",
            thinking: false,
            messageType: "writingSkill",
            messageId: "run-1:skill:1:1",
            skillContract: {
                version: 1,
                contextRevision: articleContextRevision(initialArticle),
                applicableFields: ["title"],
            },
        };
        act(() => fieldAi.applyGeneratedValues({ title: "AI title" }, source));
        mockArticleGet.mockResolvedValue({
            data: { error: 0, data: { ...data, article: { ...initialArticle, title: "Server title", version: 4 } } },
        });
        await act(async () => coordinator.onArticleUpdated({ articleId: 7, version: 4 }));
        expect(coordinator.state.contentConflict).toBeDefined();
        const before = coordinator.getCurrentArticle();
        act(() => fieldAi.applyGeneratedValues({ title: "Another AI title" }, source));
        expect(coordinator.getCurrentArticle()).toBe(before);
        expect(mockArticlePost).not.toHaveBeenCalled();
    });

    it("reloads the assistant's saved article and preserves the streaming conversation", async () => {
        const request = deferred<any>();
        mockArticleGet.mockReturnValue(request.promise);
        const updated = { ...initialArticle, title: "AI title", markdown: "AI body", version: 4, rubbish: false };
        act(() => coordinator.onArticleUpdated({ articleId: 7, version: 4 }));
        const conversation = [{ role: "assistant" as const, content: "Still writing", thinking: false }];
        act(() => coordinator.updateAiMessageCache(conversation, 7));
        await act(async () => {
            request.resolve({ data: { error: 0, data: { ...data, article: updated } } });
        });
        expect(mockArticleGet).toHaveBeenCalledWith("/api/admin/article-edit", { params: { id: 7 } });
        expect(coordinator.state.article).toEqual(updated);
        expect(coordinator.state.editorVersion).toBe(4);
        expect(coordinator.state.rubbish).toBe(false);
        expect(coordinator.state.aiMessages).toEqual(conversation);
        expect(coordinator.restoreInputRevision).toBe(1);
        expect(mockPageCache.get("/article-edit?id=7")).toEqual({
            ...data,
            article: updated,
            aiMessages: conversation,
        });
        act(() => {
            coordinator.onArticleUpdated({ articleId: 7, version: 4 });
            coordinator.onArticleUpdated({ articleId: 8, version: 5 });
        });
        expect(mockArticleGet).toHaveBeenCalledTimes(1);
    });

    it("clears the unsynced label when AI saved the local content and uses its version for the next edit", async () => {
        mockUseRealDraftSync = true;
        remountWith(data, "?id=7");
        const request = deferred<any>();
        mockArticleGet.mockReturnValue(request.promise);
        act(() => coordinator.handleValuesChange({ title: "AI title" }));
        expect(coordinator.state.contentSource).toBe("localEdit");
        const local = coordinator.state.article;
        const updated = { ...local, version: 4, lastUpdateDate: 456 };
        act(() => coordinator.onArticleUpdated({ articleId: 7, version: 4 }));
        await act(async () => {
            request.resolve({ data: { error: 0, data: { ...data, article: updated } } });
        });
        expect(coordinator.state.article).toEqual(updated);
        expect(coordinator.state.editorVersion).toBe(4);
        expect(coordinator.state.contentSource).toBe("server");
        expect(coordinator.state.contentSourceUpdatedAt).toBeUndefined();
        expect(coordinator.state.contentConflict).toBeUndefined();
        expect(removeArticleCache).toHaveBeenCalledWith(local);
        expect(mockArticlePost).not.toHaveBeenCalled();
        act(() => coordinator.handleValuesChange({ digest: "New local edit" }));
        expect(coordinator.state.article).toEqual({ ...updated, digest: "New local edit" });
        expect(coordinator.state.contentSource).toBe("localEdit");
        act(() => coordinator.onArticleUpdated({ articleId: 7, version: 4 }));
        expect(mockArticleGet).toHaveBeenCalledTimes(1);
        expect(coordinator.state.contentSource).toBe("localEdit");
        expect(coordinator.state.article.digest).toBe("New local edit");
        mockArticlePost.mockResolvedValue({
            data: { error: 0, data: { ...data, article: { ...coordinator.state.article, version: 5 } } },
        });
        await act(async () => {
            await coordinator.onSubmit(coordinator.state.article, false, false, false);
        });
        expect(mockArticlePost).toHaveBeenCalledWith(
            "/api/admin/article/update",
            expect.objectContaining({ logId: 7, version: 4, digest: "New local edit" }),
            undefined
        );
    });

    it("coalesces duplicate and older notifications while a newer refresh is pending", async () => {
        const request = deferred<any>();
        mockArticleGet.mockReturnValue(request.promise);
        act(() => coordinator.onArticleUpdated({ articleId: 7, version: 5 }));
        act(() => coordinator.onArticleUpdated({ articleId: 7, version: 5 }));
        act(() => coordinator.onArticleUpdated({ articleId: 7, version: 4 }));
        expect(mockArticleGet).toHaveBeenCalledTimes(1);
        const updated = { ...initialArticle, title: "Latest saved title", version: 5 };
        await act(async () => {
            request.resolve({ data: { error: 0, data: { ...data, article: updated } } });
        });
        expect(coordinator.state.article).toEqual(updated);
        expect(coordinator.restoreInputRevision).toBe(1);
        expect(mockDraftSyncApi!.receiveServerArticle).toHaveBeenCalledTimes(1);
        act(() => coordinator.onArticleUpdated({ articleId: 7, version: 5 }));
        expect(mockArticleGet).toHaveBeenCalledTimes(1);
    });

    it.each([-1, 1.5, NaN, undefined])(
        "ignores invalid notification version %s before reserving a created article",
        (version) => {
            remountWith({ ...data, article: { title: "", version: -1, rubbish: true } });
            act(() => coordinator.onArticleUpdated({ articleId: 18, version: version as number, created: true }));
            expect(mockArticleGet).not.toHaveBeenCalled();
            expect(coordinator.state.article.logId).toBeUndefined();
        }
    );

    it.each([2, 3, 4, NaN, undefined, 5.5])(
        "does not acknowledge response version %s for a version 5 notification",
        async (version) => {
            mockArticleGet.mockResolvedValueOnce({
                data: { error: 0, data: { ...data, article: { ...initialArticle, title: "Stale response", version } } },
            });
            await act(async () => coordinator.onArticleUpdated({ articleId: 7, version: 5 }));
            expect(coordinator.state.article).toEqual(initialArticle);
            expect(mockDraftSyncApi!.receiveServerArticle).not.toHaveBeenCalled();
            expect(coordinator.restoreInputRevision).toBe(0);
            const updated = { ...initialArticle, title: "Fresh response", version: 5 };
            mockArticleGet.mockResolvedValueOnce({ data: { error: 0, data: { ...data, article: updated } } });
            await act(async () => coordinator.onArticleUpdated({ articleId: 7, version: 5 }));
            expect(coordinator.state.article).toEqual(updated);
            expect(mockDraftSyncApi!.receiveServerArticle).toHaveBeenCalledTimes(1);
        }
    );

    it("retains the newest notified version after a read failure and retries without repeating the write", async () => {
        mockArticleGet.mockResolvedValueOnce({ data: { error: 1, message: "Read failed" } });
        await act(async () => coordinator.onArticleUpdated({ articleId: 7, version: 5 }));
        act(() => coordinator.onArticleUpdated({ articleId: 7, version: 4 }));
        expect(mockArticleGet).toHaveBeenCalledTimes(1);
        const updated = { ...initialArticle, version: 6 };
        mockArticleGet.mockResolvedValueOnce({ data: { error: 0, data: { ...data, article: updated } } });
        await act(async () => coordinator.onArticleUpdated({ articleId: 7, version: 5 }));
        expect(coordinator.state.article.version).toBe(6);
        expect(mockDraftSyncApi!.receiveServerArticle).toHaveBeenCalledTimes(1);
        act(() => coordinator.onArticleUpdated({ articleId: 7, version: 6 }));
        expect(mockArticleGet).toHaveBeenCalledTimes(2);
        expect(mockArticlePost).not.toHaveBeenCalled();
    });

    it("does not refresh an AI notification already superseded by an in-flight save", async () => {
        const saving = deferred<any>();
        mockArticlePost.mockReturnValueOnce(saving.promise);
        let pending!: Promise<boolean>;
        act(() => {
            pending = coordinator.onSubmit(initialArticle, false, false, false);
        });
        act(() => coordinator.onArticleUpdated({ articleId: 7, version: 4 }));
        const updated = { ...initialArticle, title: "Saved locally", version: 5 };
        await act(async () => {
            saving.resolve({ data: { error: 0, data: { ...data, article: updated } } });
            await pending;
        });
        expect(mockArticleGet).not.toHaveBeenCalled();
        expect(coordinator.state.article).toMatchObject(updated);
        expect(mockDraftSyncApi!.receiveServerArticle).not.toHaveBeenCalled();
    });

    it.each([4, 5])("ignores a pending AI read after a manual save acknowledges version %s", async (version) => {
        const reading = deferred<any>();
        const saving = deferred<any>();
        mockArticleGet.mockReturnValue(reading.promise);
        mockArticlePost.mockReturnValue(saving.promise);
        act(() => coordinator.onArticleUpdated({ articleId: 7, version: 4 }));
        let pending!: Promise<boolean>;
        act(() => {
            pending = coordinator.onSubmit(initialArticle, false, false, false);
        });
        const updated = { ...initialArticle, title: "Manual save", version };
        await act(async () => {
            saving.resolve({ data: { error: 0, data: { ...data, article: updated } } });
            await pending;
            reading.resolve({ data: { error: 0, data: { ...data, article: { ...initialArticle, version: 4 } } } });
        });
        expect(coordinator.state.article).toMatchObject(updated);
        expect(mockArticleGet).toHaveBeenCalledTimes(1);
        expect(mockDraftSyncApi!.receiveServerArticle).not.toHaveBeenCalled();
        expect(coordinator.restoreInputRevision).toBe(0);
    });

    it("does not restore an older page snapshot after applying an AI update", async () => {
        mockUseRealDraftSync = true;
        remountWith(data, "?id=7");
        const updated = { ...initialArticle, title: "AI saved title", version: 5 };
        mockArticleGet.mockResolvedValue({ data: { error: 0, data: { ...data, article: updated } } });
        await act(async () => coordinator.onArticleUpdated({ articleId: 7, version: 5 }));
        act(() => {
            harnessData = { ...data, article: { ...initialArticle } };
            root.render(<Harness />);
        });
        expect(coordinator.state.article).toEqual(updated);
        expect(coordinator.state.editorVersion).toBe(5);
        act(() => coordinator.onArticleUpdated({ articleId: 7, version: 4 }));
        expect(mockArticleGet).toHaveBeenCalledTimes(1);
    });

    it("loads an AI-created draft, migrates the editor session, and updates it on the next save", async () => {
        const draft = { ...data, article: { title: "", version: -1, rubbish: true } };
        remountWith(draft);
        const request = deferred<any>();
        mockArticleGet.mockReturnValue(request.promise);
        const conversation = [{ role: "assistant" as const, content: "Draft saved", thinking: false }];
        act(() => coordinator.updateAiMessageCache(conversation, 0));
        act(() => coordinator.onArticleUpdated({ articleId: 18, version: 0, created: true }));
        expect(coordinator.state.article.logId).toBe(18);
        const created = { ...initialArticle, logId: 18, title: "AI draft", markdown: "AI body", version: 0 };
        navigate.mockImplementation(() => {
            expect(mockPageCache.get("/article-edit?id=18")?.article).toEqual(created);
        });
        await act(async () => request.resolve({ data: { error: 0, data: { ...data, article: created } } }));
        expect(mockArticleGet).toHaveBeenCalledWith("/api/admin/article-edit", { params: { id: 18 } });
        expect(coordinator.state.article).toEqual(created);
        expect(coordinator.state.aiMessages).toEqual(conversation);
        expect(coordinator.restoreInputRevision).toBe(1);
        expect(migrateUiStateToArticle).toHaveBeenCalledWith(18);
        expect(navigate).toHaveBeenCalledWith("/article-edit?id=18", {
            replace: true,
            state: { articleCreatedFrom: "draft-location" },
        });
        expect(mockPageCache.get("/article-edit?id=18")?.aiMessages).toEqual(conversation);
        expect(mockPageCache.has("/article-edit")).toBe(false);
        mockArticlePost.mockResolvedValue({
            data: { error: 0, data: { ...data, article: { ...created, version: 1 } } },
        });
        await act(async () => {
            await coordinator.onSubmit(coordinator.state.article, false, false, false);
        });
        expect(mockArticlePost).toHaveBeenCalledWith(
            "/api/admin/article/update",
            expect.objectContaining({ logId: 18, version: 0 }),
            undefined
        );
    });

    it("does not switch an existing article to an AI-created article", () => {
        act(() => coordinator.onArticleUpdated({ articleId: 18, version: 0, created: true }));
        expect(mockArticleGet).not.toHaveBeenCalled();
        expect(coordinator.state.article.logId).toBe(7);
        expect(navigate).not.toHaveBeenCalled();
    });

    it("preserves edits made during the read as a conflict instead of autosaving over the AI update", async () => {
        const request = deferred<any>();
        mockArticleGet.mockReturnValue(request.promise);
        act(() => coordinator.onArticleUpdated({ articleId: 7, version: 4 }));
        const localArticle = { ...initialArticle, title: "Local edit" };
        mockDraftSyncApi!.receiveServerArticle.mockReturnValue({ article: localArticle, revision: 1, updatedAt: 123 });
        const updated = { ...initialArticle, title: "AI title", version: 4 };
        await act(async () => {
            request.resolve({ data: { error: 0, data: { ...data, article: updated } } });
        });
        expect(coordinator.state.article).toEqual(updated);
        expect(coordinator.state.contentConflict).toEqual({
            source: "localEdit",
            localArticle,
            localVersion: 3,
            localUpdatedAt: 123,
            serverVersion: 4,
        });
        expect(mockArticlePost).not.toHaveBeenCalled();
    });

    it("ignores an older refresh response when a newer tool update arrives", async () => {
        const first = deferred<any>();
        const second = deferred<any>();
        mockArticleGet.mockReturnValueOnce(first.promise).mockReturnValueOnce(second.promise);
        act(() => coordinator.onArticleUpdated({ articleId: 7, version: 4 }));
        act(() => coordinator.onArticleUpdated({ articleId: 7, version: 5 }));
        await act(async () => {
            second.resolve({
                data: {
                    error: 0,
                    data: {
                        ...data,
                        article: { ...initialArticle, title: "Latest", version: 5 },
                    },
                },
            });
        });
        await act(async () => {
            first.resolve({
                data: {
                    error: 0,
                    data: {
                        ...data,
                        article: { ...initialArticle, title: "Old", version: 4 },
                    },
                },
            });
        });
        expect(coordinator.state.article.title).toBe("Latest");
        expect(coordinator.state.article.version).toBe(5);
        expect(coordinator.restoreInputRevision).toBe(1);
    });

    it("ignores refresh responses after leaving the article", async () => {
        const request = deferred<any>();
        mockArticleGet.mockReturnValue(request.promise);
        act(() => coordinator.onArticleUpdated({ articleId: 7, version: 4 }));
        remountWith({ ...data, article: { ...initialArticle, logId: 8 } }, "?id=8");
        await act(async () => {
            request.resolve({
                data: {
                    error: 0,
                    data: {
                        ...data,
                        article: { ...initialArticle, title: "AI title", version: 4 },
                    },
                },
            });
        });
        expect(coordinator.state.article.logId).toBe(8);
        expect(coordinator.restoreInputRevision).toBe(0);
    });

    it("waits for a pending save before applying the tool update and reports read errors separately", async () => {
        const saving = deferred<any>();
        mockArticlePost.mockReturnValue(saving.promise);
        let pending!: Promise<boolean>;
        act(() => {
            pending = coordinator.onSubmit(coordinator.state.article, false, false, false);
        });
        act(() => coordinator.onArticleUpdated({ articleId: 7, version: 5 }));
        expect(mockArticleGet).not.toHaveBeenCalled();
        mockArticleGet.mockResolvedValue({ data: { error: 1, message: "Read failed" } });
        await act(async () => {
            saving.resolve({ data: { error: 0, data: { ...data, article: { ...initialArticle, version: 4 } } } });
            await pending;
        });
        expect(mockArticleGet).toHaveBeenCalledTimes(1);
        expect(coordinator.state.article.version).toBe(4);
        expect(messageApi.error).toHaveBeenCalledWith("Read failed");
        expect(modal.error).not.toHaveBeenCalled();
    });

    it("uses the acknowledged rollback version for the next save without waiting for page cache hydration", async () => {
        mockArticlePost.mockResolvedValueOnce({
            data: { error: 0, data: { ...data, article: { ...initialArticle, version: 4 } } },
        });
        await act(async () => {
            await coordinator.onRollback(1);
        });
        expect(coordinator.state.article.version).toBe(4);
        mockArticlePost.mockResolvedValueOnce({
            data: { error: 0, data: { ...data, article: { ...initialArticle, version: 5 } } },
        });
        await act(async () => {
            await coordinator.onSubmit(coordinator.state.article, false, false, false);
        });
        expect(mockArticlePost).toHaveBeenLastCalledWith(
            "/api/admin/article/update",
            expect.objectContaining({ version: 4 }),
            undefined
        );
    });

    it("waits for an in-flight autosave before submitting a manual save with the acknowledged version", async () => {
        const saving = deferred<any>();
        mockArticlePost.mockImplementationOnce(async () => saving.promise);
        mockArticlePost.mockResolvedValueOnce({
            data: { error: 0, data: { ...data, article: { ...initialArticle, title: "Manual", version: 5 } } },
        });
        let automatic!: Promise<boolean>;
        let manual!: Promise<boolean>;
        await act(async () => {
            automatic = coordinator.onSubmit(initialArticle, false, false, true);
        });
        await act(async () => {
            manual = coordinator.onSubmit({ ...initialArticle, title: "Manual" }, false, false, false);
        });
        expect(mockArticlePost).toHaveBeenCalledTimes(1);
        await act(async () => {
            saving.resolve({ data: { error: 0, data: { ...data, article: { ...initialArticle, version: 4 } } } });
            await automatic;
            await manual;
        });
        expect(mockArticlePost).toHaveBeenLastCalledWith(
            "/api/admin/article/update",
            expect.objectContaining({ title: "Manual", version: 4 }),
            undefined
        );
        expect(coordinator.state.article.version).toBe(5);
    });

    it("uses the created identity for a save queued while the initial draft create is pending", async () => {
        remountWith({ ...data, article: { ...initialArticle, logId: undefined, version: -1 } });
        const creating = deferred<any>();
        mockArticlePost.mockImplementationOnce(async () => creating.promise);
        mockArticlePost.mockResolvedValueOnce({
            data: {
                error: 0,
                data: { ...data, article: { ...initialArticle, title: "Latest", logId: 42, version: 1 } },
            },
        });
        let first!: Promise<boolean>;
        let second!: Promise<boolean>;
        await act(async () => {
            first = coordinator.onSubmit(coordinator.state.article, false, false, true);
        });
        await act(async () => {
            second = coordinator.onSubmit({ ...coordinator.state.article, title: "Latest" }, false, false, false);
        });
        expect(mockArticlePost).toHaveBeenCalledTimes(1);
        await act(async () => {
            creating.resolve({
                data: { error: 0, data: { ...data, article: { ...initialArticle, logId: 42, version: 0 } } },
            });
            await first;
            await second;
        });
        expect(mockArticlePost).toHaveBeenLastCalledWith(
            "/api/admin/article/update",
            expect.objectContaining({ logId: 42, title: "Latest", version: 0 }),
            undefined
        );
        expect(mockPageCache.get("/article-edit?id=42")?.article).toEqual(
            expect.objectContaining({ title: "Latest", version: 1 })
        );
        expect(mockPageCache.has("/article-edit")).toBe(false);
    });

    it("serializes a rollback after save and keeps the next publish on its acknowledged version", async () => {
        const saving = deferred<any>();
        mockArticlePost.mockImplementationOnce(async () => saving.promise);
        mockArticlePost.mockResolvedValueOnce({
            data: { error: 0, data: { ...data, article: { ...initialArticle, version: 5 } } },
        });
        let first!: Promise<boolean>;
        let rollback!: Promise<void>;
        await act(async () => {
            first = coordinator.onSubmit(initialArticle, false, false, false);
        });
        await act(async () => {
            rollback = coordinator.onRollback(1);
        });
        expect(mockArticlePost).toHaveBeenCalledTimes(1);
        await act(async () => {
            saving.resolve({ data: { error: 0, data: { ...data, article: { ...initialArticle, version: 4 } } } });
            await first;
            await rollback;
        });
        expect(mockArticlePost).toHaveBeenLastCalledWith("/api/admin/article-version/rollback", {
            logId: 7,
            version: 4,
            targetVersion: 1,
        });
        mockPostPublish.mockResolvedValueOnce({
            error: 0,
            data: { ...data, article: { ...initialArticle, rubbish: false, version: 6 } },
        });
        await act(async () => {
            await coordinator.onSubmit(coordinator.state.article, true, false, false);
        });
        expect(mockPostPublish).toHaveBeenLastCalledWith(
            "/api/admin/article/update",
            expect.objectContaining({ version: 5, rubbish: false })
        );
    });

    it("releases the write queue after a failed request without inventing a newer version", async () => {
        mockArticlePost.mockRejectedValueOnce(new Error("Connection closed"));
        await act(async () => {
            await expect(coordinator.onSubmit(initialArticle, false, false, false)).rejects.toThrow(
                "Connection closed"
            );
        });
        mockArticlePost.mockResolvedValueOnce({
            data: { error: 0, data: { ...data, article: { ...initialArticle, version: 4 } } },
        });
        await act(async () => {
            await coordinator.onSubmit(coordinator.state.article, false, false, false);
        });
        expect(mockArticlePost).toHaveBeenLastCalledWith(
            "/api/admin/article/update",
            expect.objectContaining({ version: 3 }),
            undefined
        );
    });

    it.each([false, true])("preserves edits made while a manual save is pending (publish=%s)", async (publish) => {
        const saving = deferred<any>();
        const acknowledged = { ...initialArticle, version: 4, rubbish: !publish };
        if (publish) {
            mockPostPublish.mockReturnValueOnce(saving.promise);
        } else {
            mockArticlePost.mockReturnValueOnce(saving.promise);
        }
        let pending!: Promise<boolean>;
        await act(async () => {
            pending = coordinator.onSubmit(coordinator.state.article, publish, false, false);
        });
        const edited = {
            ...initialArticle,
            title: "Newer title",
            markdown: "Newer body",
            content: "<p>Newer body</p>",
        };
        mockDraftSyncApi!.applyPatch.mockReturnValueOnce({ article: edited, revision: 1, updatedAt: 100 });
        act(() => {
            coordinator.handleValuesChange({ title: edited.title, markdown: edited.markdown, content: edited.content });
        });
        await act(async () => {
            const response = { error: 0, data: { ...data, article: acknowledged } };
            saving.resolve(publish ? response : { data: response });
            await pending;
        });
        expect(coordinator.state.article).toEqual(
            expect.objectContaining({
                title: edited.title,
                markdown: edited.markdown,
                content: edited.content,
                version: 4,
                rubbish: !publish,
            })
        );
        expect(coordinator.state.contentSource).toBe("localEdit");
        expect(mockDraftSyncApi!.markCommitted).not.toHaveBeenCalled();
        expect(mockDraftSyncApi!.markSynced).toHaveBeenCalledWith(
            { article: expect.objectContaining({ title: initialArticle.title }), revision: 0 },
            acknowledged
        );
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
        remountWith({ ...data, article: { ...initialArticle, logId: undefined, version: -1 } });
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
        navigate.mockImplementation(() => {
            expect(mockPageCache.get("/article-edit?id=42")?.article.logId).toBe(42);
        });

        await act(async () => {
            await coordinator.onSubmit({ ...coordinator.state.article, logId: undefined }, true, false, false);
        });

        expect(migrateUiStateToArticle).toHaveBeenCalledWith(42);
        expect(navigate).toHaveBeenCalledWith("/article-edit?id=42", {
            replace: true,
            state: { articleCreatedFrom: "draft-location" },
        });
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
        harnessLocation = { pathname: "/article-edit", search: "?id=42", key: "saved-location" };
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

        expect(mockDraftSyncApi?.applyPatch).not.toHaveBeenCalled();
        expect(messageApi.success).not.toHaveBeenCalled();
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
    it.each(["manual", "automatic", "publish"])(
        "pauses %s save conflicts and prevents queued stale writes",
        async (mode) => {
            const latest = { ...initialArticle, title: "Newest local input" };
            mockDraftSyncApi!.pauseForConflict.mockReturnValue({ article: latest, revision: 2, updatedAt: 200 });
            mockArticlePost.mockResolvedValue({ data: { error: 9094 } });
            mockPostPublish.mockResolvedValue({ error: 9094 });
            const read = deferred<any>();
            mockArticleGet.mockReturnValue(read.promise);
            let save!: Promise<boolean>;
            let queued!: Promise<boolean>;
            await act(async () => {
                save = coordinator.onSubmit(initialArticle, mode === "publish", false, mode === "automatic");
                queued = coordinator.onSubmit(initialArticle, false, false, false);
            });
            expect(coordinator.state.contentConflict).toMatchObject({
                loading: true,
                localArticle: { title: latest.title },
                baseArticle: initialArticle,
            });
            const server = { ...initialArticle, version: 4, markdown: "Remote text" };
            await act(async () => {
                read.resolve({ data: { error: 0, data: { ...data, article: server } } });
                expect(await save).toBe(false);
                expect(await queued).toBe(false);
            });
            expect(mockArticlePost.mock.calls.length + mockPostPublish.mock.calls.length).toBe(1);
            expect(coordinator.state.contentConflict).toMatchObject({
                loading: false,
                localVersion: 3,
                serverVersion: 4,
                localArticle: { title: latest.title, rubbish: mode !== "publish" },
            });
            expect(modal.error).not.toHaveBeenCalled();
        }
    );

    it("retains the resolved candidate if another client writes during conflict resolution", async () => {
        mockArticlePost.mockResolvedValue({ data: { error: 9094 } });
        const remote = { ...initialArticle, title: "Other writer", version: 4 };
        mockArticleGet.mockResolvedValue({ data: { error: 0, data: { ...data, article: remote } } });
        await act(async () => {
            await coordinator.onSubmit(initialArticle, false, false, false);
        });
        const merged = { ...remote, title: "My combined result" };
        mockDraftSyncApi!.resolveConflict.mockReturnValue({ article: merged, revision: 2, updatedAt: 200 });
        mockDraftSyncApi!.pauseForConflict.mockImplementation((article) => ({ article, revision: 2, updatedAt: 200 }));
        mockArticleGet.mockResolvedValue({
            data: { error: 0, data: { ...data, article: { ...remote, version: 5, title: "Third writer" } } },
        });
        await act(async () => {
            expect(await coordinator.saveMergedConflict(merged)).toBe(false);
        });
        expect(mockDraftSyncApi!.resolveConflict).toHaveBeenCalledWith(merged, false);
        expect(mockArticlePost.mock.calls[1][1]).toMatchObject({ version: 4, title: merged.title });
        expect(coordinator.state.contentConflict).toMatchObject({
            localArticle: { title: merged.title },
            localVersion: 4,
            serverVersion: 5,
            baseArticle: remote,
        });
    });

    it.each([2, NaN])(
        "rejects conflict snapshot version %s without rolling back the local version",
        async (version) => {
            mockArticlePost.mockResolvedValue({ data: { error: 9094 } });
            mockArticleGet.mockResolvedValue({
                data: { error: 0, data: { ...data, article: { ...initialArticle, version } } },
            });
            const local = { ...initialArticle, title: "Keep local edits" };
            await act(async () => {
                await coordinator.onSubmit(local, false, false, false);
            });
            expect(coordinator.state.article.version).toBe(3);
            expect(coordinator.state.contentConflict).toMatchObject({
                loadError: true,
                localArticle: { title: local.title },
                localVersion: 3,
            });
        }
    );

    it("keeps local input when reading the conflict fails and retries without writing", async () => {
        mockArticlePost.mockResolvedValue({ data: { error: 9094 } });
        mockArticleGet.mockRejectedValueOnce(new Error("offline"));
        const local = { ...initialArticle, markdown: "Keep offline changes" };
        await act(async () => {
            await coordinator.onSubmit(local, false, false, false);
        });
        expect(coordinator.state.contentConflict).toMatchObject({
            loadError: true,
            localArticle: { markdown: local.markdown },
        });
        const remote = { ...initialArticle, version: 4 };
        mockArticleGet.mockResolvedValue({ data: { error: 0, data: { ...data, article: remote } } });
        await act(async () => coordinator.retryConflictRead());
        expect(mockArticlePost).toHaveBeenCalledTimes(1);
        expect(coordinator.state.contentConflict).toMatchObject({
            loading: false,
            localArticle: { markdown: local.markdown },
            serverVersion: 4,
        });
    });
    it("never publishes an automatic synchronization even if its snapshot is published", async () => {
        jest.useFakeTimers();
        try {
            const published = { ...initialArticle, rubbish: false };
            remountWith({ ...data, article: published }, "?id=7");
            mockArticlePost.mockResolvedValue({
                data: { error: 0, data: { ...data, article: { ...published, rubbish: true, version: 4 } } },
            });
            act(() => mockDraftSyncOptions.onRequestSync({ article: published, revision: 1 }));
            await act(async () => jest.advanceTimersByTime(5000));
            expect(mockArticlePost.mock.calls[0][1]).toMatchObject({ rubbish: true, transparentPublish: false });
            expect(coordinator.state.rubbish).toBe(true);
            expect(coordinator.state.article.rubbish).toBe(true);
            expect(coordinator.isSaving).toBe(false);
        } finally {
            jest.useRealTimers();
        }
    });
});
