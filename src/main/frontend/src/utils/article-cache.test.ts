import { beforeEach, describe, expect, it } from "@jest/globals";
import { ArticleDraftSyncState } from "../components/articleEdit/draft-sync/article-draft-sync-state-machine";
import { AIProviderType } from "../type";
import { articleDataToState, articleSaveToCache, getArticleDraftSyncState, getLocalArticleCaches } from "./article-cache";

const cacheStorageKey = () => `${window.location.host}_cache_page_data`;

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
                key: "local-article-cache-draft",
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
});
