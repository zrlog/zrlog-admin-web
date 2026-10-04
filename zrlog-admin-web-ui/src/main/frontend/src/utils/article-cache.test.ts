import { beforeEach, describe, expect, it } from "@jest/globals";
import { ArticleDraftSyncState } from "../components/articleEdit/draft-sync/article-draft-sync-state-machine";
import { AIProviderType } from "../type";
import {
    articleDataToState,
    articleSaveToCache,
    removeArticleCache,
    restoreLocalArticleCache,
    getArticleDraftBase,
    getArticleDraftSyncState,
    getLocalArticleCaches,
} from "./article-cache";

const cacheStorageKey = () => `${window.location.host}_cache_page_data_session_anonymous`;

describe("article cache", () => {
    beforeEach(() => {
        localStorage.clear();
    });

    it("removes the exact empty draft created by the legacy mount effect", () => {
        localStorage.setItem(
            cacheStorageKey(),
            JSON.stringify({
                "local-article-cache-draft": {
                    version: -1,
                    title: "",
                    keywords: "",
                    rubbish: true,
                },
                "local-article-cache-draft-meta": {
                    updatedAt: 123,
                },
                other: {
                    retained: true,
                },
            })
        );

        expect(getLocalArticleCaches()).toEqual([]);
        expect(JSON.parse(localStorage.getItem(cacheStorageKey()) || "{}")).toEqual({
            other: {
                retained: true,
            },
        });
    });

    it("retains a real local draft", () => {
        const article = {
            version: -1,
            title: "Changed title",
            keywords: "",
            rubbish: true,
        };
        localStorage.setItem(
            cacheStorageKey(),
            JSON.stringify({
                "local-article-cache-draft": article,
                "local-article-cache-draft-meta": {
                    updatedAt: 456,
                },
            })
        );

        expect(getLocalArticleCaches()).toEqual([
            {
                key: "local-article-cache-draft",
                article,
                draft: true,
                updatedAt: 456,
            },
        ]);
    });

    it("persists draft synchronization state and preserves it during local-only saves", () => {
        const article = {
            version: -1,
            title: "Offline title",
            rubbish: true,
        };
        const syncState: ArticleDraftSyncState = {
            connectivity: "offline",
            document: "dirty",
            sync: "idle",
            revision: 3,
            retryCount: 1,
            lastError: "network unavailable",
        };

        articleSaveToCache(article, 100, syncState);
        articleSaveToCache(
            {
                ...article,
                markdown: "offline body",
            },
            200
        );

        expect(getArticleDraftSyncState(article)).toEqual(syncState);
        expect(getLocalArticleCaches()).toEqual([
            {
                key: expect.stringContaining("local-article-cache-draft"),
                article: {
                    ...article,
                    markdown: "offline body",
                },
                draft: true,
                updatedAt: 200,
                syncState,
            },
        ]);
    });

    it("restores a persisted synchronization conflict even when versions match", () => {
        const localArticle = {
            logId: 2,
            version: 1,
            title: "Conflicting local title",
            rubbish: true,
        };
        articleSaveToCache(localArticle, 456, {
            connectivity: "online",
            document: "dirty",
            sync: "conflict",
            revision: 2,
            retryCount: 0,
            lastError: "version expired",
        });
        const serverArticle = {
            ...localArticle,
            title: "Server title",
        };

        const state = articleDataToState({
            article: serverArticle,
            tags: [],
            types: [],
            aiProvider: AIProviderType.OPEN_AI,
            aiConfigured: false,
            aiMessages: [],
        });

        expect(state.article).toEqual(serverArticle);
        expect(state.contentSource).toBe("server");
        expect(state.contentConflict).toEqual({
            source: "localEdit",
            localArticle,
            localVersion: 1,
            localUpdatedAt: 456,
            serverVersion: 1,
        });
    });
    it("keeps the exact acknowledged base with offline edits and restores it after a remote update", () => {
        const base = { logId: 8, version: 2, title: "Base", markdown: "Base body", rubbish: true };
        const local = { ...base, markdown: "Offline body" };
        articleSaveToCache(local, 100, undefined, base);
        articleSaveToCache({ ...local, title: "Offline title" }, 200);
        expect(getArticleDraftBase(local)).toEqual(base);
        const server = { ...base, version: 3, digest: "Remote summary" };
        const state = articleDataToState({
            article: server,
            types: [],
            tags: [],
            aiProvider: AIProviderType.OPEN_AI,
            aiMessages: [],
        });
        expect(state.contentConflict).toMatchObject({
            baseArticle: base,
            localArticle: { markdown: "Offline body", title: "Offline title" },
            localVersion: 2,
            serverVersion: 3,
        });
        articleSaveToCache({ ...local, version: 3 }, 300);
        expect(getArticleDraftBase({ ...local, version: 3 })).toBeUndefined();
    });

    it.each(["queued", "conflict"] as const)("cleans up a %s draft already saved on the server", (sync) => {
        const local = { logId: 8, version: 3, title: "AI title", markdown: "AI body", content: "", rubbish: true };
        articleSaveToCache(local, 100, {
            connectivity: "online",
            document: "dirty",
            sync,
            revision: 2,
            retryCount: 0,
        });
        const server = { ...local, version: 4, content: "<p>AI body</p>", lastUpdateDate: 200 };
        const state = articleDataToState({
            article: server,
            types: [],
            tags: [],
            aiProvider: AIProviderType.OPEN_AI,
            aiMessages: [],
        });
        expect(state.article).toEqual(server);
        expect(state.contentSource).toBe("server");
        expect(state.contentConflict).toBeUndefined();
        expect(getLocalArticleCaches()).toEqual([]);
    });

    it("retains genuinely unsaved edits when recovering an old conflicting draft", () => {
        const local = { logId: 8, version: 3, title: "AI title", markdown: "Later manual edit", rubbish: true };
        articleSaveToCache(local, 100, {
            connectivity: "online",
            document: "dirty",
            sync: "conflict",
            revision: 3,
            retryCount: 0,
        });
        const server = { ...local, version: 4, markdown: "AI body" };
        const state = articleDataToState({
            article: server,
            types: [],
            tags: [],
            aiProvider: AIProviderType.OPEN_AI,
            aiMessages: [],
        });
        expect(state.contentConflict?.localArticle).toEqual(local);
        expect(getLocalArticleCaches()).toHaveLength(1);
    });

    it("does not acknowledge identical content from a lower server version", () => {
        const local = { logId: 8, version: 5, title: "AI title", markdown: "AI body", rubbish: true };
        articleSaveToCache(local, 100);
        const state = articleDataToState({
            article: { ...local, version: 4 },
            aiMessages: [],
            types: [],
            tags: [],
            aiProvider: AIProviderType.OPEN_AI,
        });
        expect(state.contentConflict?.localArticle).toEqual(local);
        expect(getLocalArticleCaches()).toHaveLength(1);
    });

    it("treats a local version ahead of the server as a conflict", () => {
        const local = { logId: 8, version: 8, title: "Local", rubbish: true };
        articleSaveToCache(local);
        const server = { ...local, version: 2, title: "Server" };
        const state = articleDataToState({
            article: server,
            types: [],
            tags: [],
            aiProvider: AIProviderType.OPEN_AI,
            aiMessages: [],
        });
        expect(state.article).toEqual(server);
        expect(state.contentConflict?.localArticle).toEqual(local);
    });
    it("isolates two tabs' offline drafts and does not erase the other draft after a save", () => {
        const base = { logId: 7, version: 1, title: "Base", rubbish: true };
        const data = { article: base, types: [], tags: [], aiProvider: AIProviderType.OPEN_AI, aiMessages: [] };
        const mine = { ...base, markdown: "Tab A offline" };
        articleSaveToCache(mine, 100, undefined, base);
        const ownerKey = Object.keys(sessionStorage).find((key) => key.endsWith(":owner"))!;
        const ownerA = sessionStorage.getItem(ownerKey)!;
        sessionStorage.removeItem(ownerKey);
        const theirs = { ...base, markdown: "Tab B offline" };
        articleSaveToCache(theirs, 200, undefined, base);
        const ownerB = sessionStorage.getItem(ownerKey)!;
        expect(getLocalArticleCaches()).toHaveLength(2);
        sessionStorage.setItem(ownerKey, ownerA);
        expect(articleDataToState(data).article).toEqual(mine);
        expect(getArticleDraftBase(mine)).toEqual(base);
        sessionStorage.setItem(ownerKey, ownerB);
        removeArticleCache(theirs);
        sessionStorage.setItem(ownerKey, ownerA);
        expect(articleDataToState(data).article).toEqual(mine);
        expect(getLocalArticleCaches()).toHaveLength(1);
    });

    it("recovers a closed tab's chosen draft with its base and removes that recovery only after synchronization", () => {
        const base = { logId: 9, version: 1, title: "Base", rubbish: true };
        const local = { ...base, markdown: "Closed tab work" };
        articleSaveToCache(local, 100, undefined, base);
        const entry = getLocalArticleCaches()[0];
        sessionStorage.clear();
        restoreLocalArticleCache(entry.key);
        const data = {
            article: { ...base, version: 2 },
            types: [],
            tags: [],
            aiProvider: AIProviderType.OPEN_AI,
            aiMessages: [],
        };
        expect(articleDataToState(data).contentConflict).toMatchObject({ localArticle: local, baseArticle: base });
        articleSaveToCache({ ...local, version: 2 }, 200, undefined, data.article);
        removeArticleCache({ ...local, version: 2 });
        expect(getLocalArticleCaches()).toHaveLength(0);
    });
});
