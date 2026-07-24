import { beforeEach, describe, expect, it } from "@jest/globals";
import { getLocalArticleCaches } from "./article-cache";

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
});
