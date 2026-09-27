import { AxiosInstance } from "axios";
import { ApiResponse } from "../../type";
import { postRefreshCacheSse } from "../../utils/sse-utils";
import { MessageInstance } from "antd/es/message/interface";

export type ArticlePinningEntry = {
    logId: number;
    title: string;
    sticky: number;
};

export type ArticlePinningData = {
    items: ArticlePinningEntry[];
};

export type PinnableArticle = {
    id: number;
    privacy?: boolean;
    rubbish?: boolean;
    sticky?: number;
};

export type ArticlePinningAction = "pin" | "unpin" | "move";
export type ArticlePinningDirection = "UP" | "DOWN";
export type ArticlePinningRequestGuard = {
    current: boolean;
};

export type ArticlePinningOverride = {
    sourceRows: readonly PinnableArticle[];
    stickyById: Map<number, number>;
};

const API_PATH = "/api/admin/article-pinning";

export const canPinArticle = (article: PinnableArticle) => !article.privacy && !article.rubbish;

export const toStickyMap = (items: ArticlePinningEntry[]) =>
    new Map(items.map((item) => [item.logId, item.sticky]));

export const toArticleStickyMap = (articles: readonly PinnableArticle[]) =>
    new Map(articles.map((article) => [article.id, Number(article.sticky) || 0]));

export const resolveArticleStickyMap = (
    articles: readonly PinnableArticle[],
    override?: ArticlePinningOverride
) => (override?.sourceRows === articles ? override.stickyById : toArticleStickyMap(articles));

export const applyArticlePinningSnapshot = <T extends PinnableArticle>(
    articles: readonly T[],
    items: ArticlePinningEntry[]
) => {
    const latest = toStickyMap(items);
    return articles.map((article) => ({
        ...article,
        sticky: latest.get(article.id) || 0,
    }));
};

export const tryBeginArticlePinningRequest = (guard: ArticlePinningRequestGuard) => {
    if (guard.current) {
        return false;
    }
    guard.current = true;
    return true;
};

export const finishArticlePinningRequest = (guard: ArticlePinningRequestGuard) => {
    guard.current = false;
};

export const loadArticlePinning = async (axiosInstance: AxiosInstance) => {
    const { data } = await axiosInstance.get<ApiResponse<ArticlePinningData>>(API_PATH);
    return data;
};

export const updateArticlePinning = (
    action: ArticlePinningAction,
    logId: number,
    options: {
        direction?: ArticlePinningDirection;
        messageApi: MessageInstance;
        backgroundTaskTitle: string;
    }
) =>
    postRefreshCacheSse<ApiResponse<ArticlePinningData>>(`${API_PATH}/${action}`, {
        body: {
            logId,
            ...(options.direction ? { direction: options.direction } : {}),
        },
        messageApi: options.messageApi,
        messageKey: "articlePinningRefreshCache",
        backgroundTaskTitle: options.backgroundTaskTitle,
    });
