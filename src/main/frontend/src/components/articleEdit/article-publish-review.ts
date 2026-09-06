import { ArticleEntry } from "./index.types";

export type ArticlePublishReviewTarget = "title" | "category" | "markdown" | "alias" | "digest" | "tags" | "cover";

export type ArticlePublishReviewField = ArticlePublishReviewTarget;

export type ArticlePublishReviewCheck = {
    field: ArticlePublishReviewField;
    target: ArticlePublishReviewTarget;
    status: "ready" | "warning" | "blocker";
};

export type ArticlePublishReview = {
    categoryLabel?: string;
    checks: ArticlePublishReviewCheck[];
    blockers: ArticlePublishReviewCheck[];
    warnings: ArticlePublishReviewCheck[];
};

export type ArticlePublishTypeOption = {
    value: number;
    label: string;
};

const hasText = (value?: string) => Boolean(value?.trim());

const getRenderedText = (content?: string) => {
    if (!content) {
        return "";
    }

    const parsedDocument = new DOMParser().parseFromString(content, "text/html");
    parsedDocument.querySelectorAll("script, style, noscript, template").forEach((element) => element.remove());
    return (parsedDocument.body.textContent || "").replace(/\u00a0/g, " ").trim();
};

const hasArticleBody = (article: ArticleEntry) => {
    if (hasText(article.markdown)) {
        return true;
    }
    return Boolean(getRenderedText(article.content));
};

export const buildArticlePublishReview = (
    article: ArticleEntry,
    typeOptions: ArticlePublishTypeOption[]
): ArticlePublishReview => {
    const category = typeOptions.find((option) => option.value === article.typeId);
    const checks: ArticlePublishReviewCheck[] = [
        {
            field: "title",
            target: "title",
            status: hasText(article.title) ? "ready" : "blocker",
        },
        {
            field: "category",
            target: "category",
            status: category ? "ready" : "blocker",
        },
        {
            field: "markdown",
            target: "markdown",
            status: hasArticleBody(article) ? "ready" : "warning",
        },
        {
            field: "alias",
            target: "alias",
            status: hasText(article.alias) ? "ready" : "warning",
        },
        {
            field: "digest",
            target: "digest",
            status: hasText(article.digest) ? "ready" : "warning",
        },
        {
            field: "tags",
            target: "tags",
            status: hasText(article.keywords) ? "ready" : "warning",
        },
        {
            field: "cover",
            target: "cover",
            status: hasText(article.thumbnail) ? "ready" : "warning",
        },
    ];

    return {
        categoryLabel: category?.label,
        checks,
        blockers: checks.filter((check) => check.status === "blocker"),
        warnings: checks.filter((check) => check.status === "warning"),
    };
};
