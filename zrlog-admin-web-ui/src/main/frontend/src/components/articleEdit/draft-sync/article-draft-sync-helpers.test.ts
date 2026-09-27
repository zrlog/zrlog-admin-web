import { describe, expect, it } from "@jest/globals";
import { ArticleEntry } from "../index.types";
import {
    isRetryableArticleSyncError,
    mergeArticleSynchronizationMetadata,
} from "./article-draft-sync-helpers";

describe("article draft sync helpers", () => {
    it("retries only network and temporary HTTP failures", () => {
        expect(isRetryableArticleSyncError(new Error("network error"))).toBe(true);
        expect(isRetryableArticleSyncError({ response: { status: 429 } })).toBe(true);
        expect(isRetryableArticleSyncError({ status: 503 })).toBe(true);
        expect(isRetryableArticleSyncError({ status: 403 })).toBe(false);
        expect(isRetryableArticleSyncError({ error: 9001, message: "session expired" })).toBe(false);
    });

    it("merges server metadata without replacing newer local content", () => {
        const currentArticle: ArticleEntry = {
            title: "Newest local title",
            markdown: "newest local body",
            alias: "local-alias",
            thumbnail: "local-cover.png",
            typeId: 1,
            rubbish: true,
            version: 1,
        };
        const savedArticle: ArticleEntry = {
            title: "Older submitted title",
            markdown: "older submitted body",
            alias: "server-alias",
            thumbnail: "server-cover.png",
            typeId: 1,
            rubbish: true,
            logId: 42,
            lastUpdateDate: 456,
            version: 2,
            previewUrl: "/preview/42",
        };

        expect(mergeArticleSynchronizationMetadata(currentArticle, savedArticle, true)).toEqual({
            ...currentArticle,
            logId: 42,
            lastUpdateDate: 456,
            version: 2,
            previewUrl: "/preview/42",
            socialPreview: undefined,
        });
    });
});
