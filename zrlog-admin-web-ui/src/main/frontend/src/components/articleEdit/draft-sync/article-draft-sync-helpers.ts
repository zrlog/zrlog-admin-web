import { ArticleEntry } from "../index.types";

export const isRetryableArticleSyncError = (error: unknown) => {
    if (!error || typeof error !== "object") {
        return true;
    }
    const response = error as {
        error?: unknown;
        response?: {
            status?: unknown;
        };
        status?: unknown;
    };
    if (typeof response.error === "number") {
        return false;
    }
    const status = Number(response.status ?? response.response?.status);
    if (!Number.isFinite(status) || status <= 0) {
        return true;
    }
    return status === 408 || status === 425 || status === 429 || status >= 500;
};

export const mergeArticleSynchronizationMetadata = (
    currentArticle: ArticleEntry,
    savedArticle: ArticleEntry,
    create: boolean = false
): ArticleEntry => {
    const mergedArticle: ArticleEntry = {
        ...currentArticle,
        logId: savedArticle.logId,
        lastUpdateDate: savedArticle.lastUpdateDate,
        version: savedArticle.version,
        previewUrl: savedArticle.previewUrl,
        socialPreview: savedArticle.socialPreview,
    };
    if (create && !currentArticle.alias?.trim()) {
        mergedArticle.alias = savedArticle.alias;
    }
    if (!currentArticle.thumbnail?.trim()) {
        mergedArticle.thumbnail = savedArticle.thumbnail;
    }
    return mergedArticle;
};
