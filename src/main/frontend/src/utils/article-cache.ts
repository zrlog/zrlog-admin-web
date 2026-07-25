import { ArticleEditInfo, ArticleEditState, ArticleEntry } from "../components/articleEdit/index.types";
import type {
    ArticleConnectivity,
    ArticleDocumentStatus,
    ArticleDraftSyncState,
    ArticleSyncActivity,
} from "../components/articleEdit/draft-sync/article-draft-sync-state-machine";
import { getCachedData, putCache } from "./cache";

const LOCAL_ARTICLE_CACHE_DRAFT_KEY = "local-article-cache-draft";
const LOCAL_ARTICLE_CACHE_PREFIX = "local-article-cache-";
const LEGACY_EMPTY_DRAFT_KEYS = new Set(["version", "title", "keywords", "rubbish"]);

export type LocalArticleCacheEntry = {
    key: string;
    article: ArticleEntry;
    draft: boolean;
    updatedAt: number;
    syncState?: ArticleDraftSyncState;
};

type LocalArticleCacheMeta = {
    updatedAt?: unknown;
    syncState?: unknown;
};

const buildCacheKey = (logId: number | undefined | null) => {
    if (logId === undefined || logId === null || logId <= 0) {
        return LOCAL_ARTICLE_CACHE_DRAFT_KEY;
    }
    return LOCAL_ARTICLE_CACHE_PREFIX + logId;
};

const buildCacheMetaKey = (key: string) => {
    return key + "-meta";
};

const isArticleCacheKey = (key: string) => {
    return (
        key === LOCAL_ARTICLE_CACHE_DRAFT_KEY || (key.startsWith(LOCAL_ARTICLE_CACHE_PREFIX) && !key.endsWith("-meta"))
    );
};

const isArticleEntry = (value: unknown): value is ArticleEntry => {
    if (!value || typeof value !== "object") {
        return false;
    }
    const article = value as Partial<ArticleEntry>;
    return typeof article.title === "string" && typeof article.version === "number";
};

const ARTICLE_CONNECTIVITY = new Set<ArticleConnectivity>(["online", "offline"]);
const ARTICLE_DOCUMENT_STATUS = new Set<ArticleDocumentStatus>(["clean", "dirty"]);
const ARTICLE_SYNC_ACTIVITY = new Set<ArticleSyncActivity>([
    "idle",
    "queued",
    "syncing",
    "retryWaiting",
    "conflict",
    "blocked",
]);

const isArticleDraftSyncState = (value: unknown): value is ArticleDraftSyncState => {
    if (!value || typeof value !== "object") {
        return false;
    }
    const state = value as Partial<ArticleDraftSyncState>;
    return (
        ARTICLE_CONNECTIVITY.has(state.connectivity as ArticleConnectivity) &&
        ARTICLE_DOCUMENT_STATUS.has(state.document as ArticleDocumentStatus) &&
        ARTICLE_SYNC_ACTIVITY.has(state.sync as ArticleSyncActivity) &&
        typeof state.revision === "number" &&
        Number.isFinite(state.revision) &&
        state.revision >= 0 &&
        typeof state.retryCount === "number" &&
        Number.isFinite(state.retryCount) &&
        state.retryCount >= 0 &&
        (state.nextRetryAt === undefined ||
            (typeof state.nextRetryAt === "number" && Number.isFinite(state.nextRetryAt))) &&
        (state.lastError === undefined || typeof state.lastError === "string")
    );
};

export const isLegacyEmptyLocalDraft = (value: unknown): value is ArticleEntry => {
    if (!isArticleEntry(value)) {
        return false;
    }
    const article = value as ArticleEntry;
    return (
        Object.keys(article).every((key) => LEGACY_EMPTY_DRAFT_KEYS.has(key)) &&
        article.version === -1 &&
        article.title === "" &&
        (article.keywords === "" || article.keywords === undefined) &&
        article.rubbish === true
    );
};

const removeLegacyEmptyLocalDraft = (record: Record<string, any>) => {
    if (!isLegacyEmptyLocalDraft(record[LOCAL_ARTICLE_CACHE_DRAFT_KEY])) {
        return false;
    }
    delete record[LOCAL_ARTICLE_CACHE_DRAFT_KEY];
    delete record[buildCacheMetaKey(LOCAL_ARTICLE_CACHE_DRAFT_KEY)];
    return true;
};

const getArticleCacheUpdatedAt = (record: Record<string, any>, key: string) => {
    const meta = record[buildCacheMetaKey(key)] as LocalArticleCacheMeta | undefined;
    const updatedAt = Number(meta?.updatedAt);
    return Number.isFinite(updatedAt) ? updatedAt : 0;
};

const getArticleDraftSyncStateByKey = (record: Record<string, any>, key: string) => {
    const meta = record[buildCacheMetaKey(key)] as LocalArticleCacheMeta | undefined;
    return isArticleDraftSyncState(meta?.syncState) ? meta.syncState : undefined;
};

const removeArticleCacheByKey = (key: string) => {
    const record = getCachedData();
    delete record[key];
    delete record[buildCacheMetaKey(key)];
    putCache(record);
};

export const articleDataToState = (data: ArticleEditInfo, preferredTypeId?: number): ArticleEditState => {
    const serverArticle = data.article.logId && data.article.logId > 0;
    const article: ArticleEntry = serverArticle
        ? data.article
        : {
              version: -1,
              title: "",
              keywords: "",
              /*默认创建的为草稿*/
              rubbish: true,
          };
    const cacheKey = buildCacheKey(article.logId);
    const record = getCachedData();
    if (removeLegacyEmptyLocalDraft(record)) {
        putCache(record);
    }
    const cachedArticleValue = record[cacheKey];
    const cachedArticle = isArticleEntry(cachedArticleValue) ? cachedArticleValue : undefined;
    const cachedSyncState = getArticleDraftSyncStateByKey(record, cacheKey);
    const cachedUpdatedAt = getArticleCacheUpdatedAt(record, cacheKey);
    const serverVersion = Number.isFinite(Number(article.version)) ? Number(article.version) : -1;
    let realArticle;
    let contentSource: ArticleEditState["contentSource"] = "server";
    let contentConflict: ArticleEditState["contentConflict"];
    //本地缓存版本是没有被服务器再次修改的情况下才使用缓存数据
    if (cachedArticle && serverArticle && cachedSyncState?.sync === "conflict") {
        realArticle = article;
        contentConflict = {
            source: "localEdit",
            localArticle: cachedArticle,
            localVersion: cachedArticle.version,
            localUpdatedAt: cachedUpdatedAt,
            serverVersion,
        };
    } else if (cachedArticle && !serverArticle) {
        realArticle = cachedArticle;
        contentSource = "localDraft";
    } else if (cachedArticle && cachedArticle.version >= serverVersion) {
        realArticle = cachedArticle;
        contentSource = "localEdit";
    } else {
        if (cachedArticle && serverArticle) {
            contentConflict = {
                source: "localEdit",
                localArticle: cachedArticle,
                localVersion: cachedArticle.version,
                localUpdatedAt: cachedUpdatedAt,
                serverVersion,
            };
        }
        realArticle = article;
    }

    const realArticleWithPreferredType =
        preferredTypeId && (!realArticle.logId || realArticle.logId <= 0)
            ? {
                  ...realArticle,
                  typeId: preferredTypeId,
              }
            : realArticle;

    return {
        typeOptions: data.types
            ? data.types.map((x) => {
                  return { value: x.id, label: x.typeName };
              })
            : [],
        aiProvider: data.aiProvider,
        aiModel: data.aiModel,
        aiConfigured: data.aiConfigured === true,
        aiMessages: data.aiMessages,
        linkPreviewEnabled: data.linkPreviewEnabled === true,
        publishCheckEnabled: data.publishCheckEnabled !== false,
        articleCoverAspectRatio: data.articleCoverAspectRatio || "16:9",
        articleEditAutoSaveInterval: data.articleEditAutoSaveInterval || 5,
        editorVersion: realArticleWithPreferredType.version,
        contentSource,
        contentSourceUpdatedAt: contentSource === "server" ? undefined : cachedUpdatedAt,
        contentConflict,
        tags: data.tags ? data.tags : [],
        rubbish: realArticleWithPreferredType.rubbish === true,
        article: realArticleWithPreferredType,
        saving: {
            previewIng: false,
            releaseSaving: false,
            rubbishSaving: false,
            autoSaving: false,
        },
    };
};

export const articleSaveToCache = (
    article: ArticleEntry,
    updatedAt: number = Date.now(),
    syncState?: ArticleDraftSyncState
) => {
    const key = buildCacheKey(article.logId);
    const record = getCachedData();
    const currentMeta = record[buildCacheMetaKey(key)] as LocalArticleCacheMeta | undefined;
    record[key] = article;
    record[buildCacheMetaKey(key)] = {
        updatedAt,
        ...(syncState
            ? {
                  syncState,
              }
            : isArticleDraftSyncState(currentMeta?.syncState)
            ? {
                  syncState: currentMeta.syncState,
              }
            : {}),
    };
    putCache(record);
};

export const getArticleDraftSyncState = (article: ArticleEntry) => {
    return getArticleDraftSyncStateByKey(getCachedData(), buildCacheKey(article.logId));
};

export const getLocalArticleCaches = (): LocalArticleCacheEntry[] => {
    const record = getCachedData();
    if (removeLegacyEmptyLocalDraft(record)) {
        putCache(record);
    }
    return Object.entries(record)
        .filter(([key, value]) => isArticleCacheKey(key) && isArticleEntry(value))
        .map(([key, article]) => {
            const syncState = getArticleDraftSyncStateByKey(record, key);
            return {
                key,
                article,
                draft: key === LOCAL_ARTICLE_CACHE_DRAFT_KEY,
                updatedAt: getArticleCacheUpdatedAt(record, key),
                ...(syncState ? { syncState } : {}),
            };
        })
        .sort((a, b) => {
            const updatedAtDiff = b.updatedAt - a.updatedAt;
            if (updatedAtDiff !== 0) {
                return updatedAtDiff;
            }
            if (a.draft !== b.draft) {
                return a.draft ? -1 : 1;
            }
            return 0;
        });
};

export const removeArticleCache = (article: ArticleEntry) => {
    removeArticleCacheByKey(buildCacheKey(article.logId));
};

export const removeLocalArticleCache = () => {
    removeArticleCacheByKey(buildCacheKey(null));
};

export const removeLocalArticleCacheByKey = (key: string) => {
    if (isArticleCacheKey(key)) {
        removeArticleCacheByKey(key);
    }
};
