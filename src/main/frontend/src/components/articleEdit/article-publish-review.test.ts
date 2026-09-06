import { describe, expect, it } from "@jest/globals";
import { ArticleEntry } from "./index.types";
import { buildArticlePublishReview } from "./article-publish-review";

const completeArticle: ArticleEntry = {
    title: "Release notes",
    typeId: 2,
    markdown: "Published body",
    content: "<p>Published body</p>",
    alias: "release-notes",
    digest: "Release summary",
    keywords: "release,zrlog",
    thumbnail: "/assets/release.png",
    rubbish: true,
    version: 1,
};

const typeOptions = [
    { value: 1, label: "Life" },
    { value: 2, label: "Engineering" },
];

describe("buildArticlePublishReview", () => {
    it("blocks a missing title and a category that is not available", () => {
        const review = buildArticlePublishReview(
            {
                ...completeArticle,
                title: "   ",
                typeId: 99,
            },
            typeOptions
        );

        expect(review.blockers.map((check) => check.field)).toEqual(["title", "category"]);
        expect(review.categoryLabel).toBeUndefined();
    });

    it("reports optional metadata and an empty body as non-blocking warnings", () => {
        const review = buildArticlePublishReview(
            {
                ...completeArticle,
                markdown: "",
                content: "<p>&nbsp;</p>",
                alias: "",
                digest: undefined,
                keywords: " ",
                thumbnail: undefined,
            },
            typeOptions
        );

        expect(review.blockers).toEqual([]);
        expect(review.warnings.map((check) => check.field)).toEqual(["markdown", "alias", "digest", "tags", "cover"]);
    });

    it("accepts rendered legacy content when Markdown is unavailable", () => {
        const review = buildArticlePublishReview(
            {
                ...completeArticle,
                markdown: undefined,
                content: "<p>Legacy body</p>",
            },
            typeOptions
        );

        expect(review.blockers).toEqual([]);
        expect(review.warnings).toEqual([]);
        expect(review.categoryLabel).toBe("Engineering");
    });
});
