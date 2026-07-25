import { useCallback, useEffect, useRef, useState } from "react";
import { deepEqualWithSpecialJSON } from "../../../utils/helpers";
import { ArticleChangeableValue, ArticleEntry } from "../index.types";
import {
    ArticleDraftSyncEvent,
    ArticleDraftSyncState,
    reduceArticleDraftSyncState,
    restoreArticleDraftSyncState,
} from "./article-draft-sync-state-machine";
import { mergeArticleSynchronizationMetadata } from "./article-draft-sync-helpers";

export type ArticleDraftSyncTask = {
    article: ArticleEntry;
    revision: number;
};

export type ArticleDraftChange = ArticleDraftSyncTask & {
    updatedAt: number;
};

type UseArticleDraftSyncOptions = {
    article: ArticleEntry;
    initialDirty: boolean;
    initialConflict?: boolean;
    initialState?: ArticleDraftSyncState;
    initialUpdatedAt?: number;
    isSyncable: (article: ArticleEntry) => boolean;
    now?: () => number;
    offline: boolean;
    onPersist: (article: ArticleEntry, updatedAt: number, syncState: ArticleDraftSyncState) => void;
    onRemove: (article: ArticleEntry) => void;
    onRequestSync: (task: ArticleDraftSyncTask) => void;
    onSynced: () => void;
    retryDelay?: (retryCount: number) => number;
};

const RETRY_DELAYS_MS = [1000, 3000, 10000, 30000, 60000];

export const getArticleDraftRetryDelay = (retryCount: number) => {
    const index = Math.min(Math.max(0, retryCount - 1), RETRY_DELAYS_MS.length - 1);
    return RETRY_DELAYS_MS[index];
};

const toErrorMessage = (error: unknown) => {
    if (error instanceof Error) {
        return error.message;
    }
    if (typeof error === "string") {
        return error;
    }
    return "Article synchronization failed";
};

const useArticleDraftSync = ({
    article,
    initialDirty,
    initialConflict = false,
    initialState,
    initialUpdatedAt,
    isSyncable,
    now = Date.now,
    offline,
    onPersist,
    onRemove,
    onRequestSync,
    onSynced,
    retryDelay = getArticleDraftRetryDelay,
}: UseArticleDraftSyncOptions) => {
    const restoredStateRef = useRef<ArticleDraftSyncState | undefined>(undefined);
    if (!restoredStateRef.current) {
        restoredStateRef.current = restoreArticleDraftSyncState(
            offline,
            initialDirty,
            initialState,
            now(),
            initialConflict
        );
    }
    const [syncState, setSyncState] = useState(restoredStateRef.current);
    const syncStateRef = useRef(syncState);
    const articleRef = useRef(article);
    const renderedArticleRef = useRef(article);
    const updatedAtRef = useRef(initialUpdatedAt || now());
    const previousOfflineRef = useRef(offline);
    const initialNetworkCheckRef = useRef(false);
    const isSyncableRef = useRef(isSyncable);
    const nowRef = useRef(now);
    const onPersistRef = useRef(onPersist);
    const onRemoveRef = useRef(onRemove);
    const onRequestSyncRef = useRef(onRequestSync);
    const onSyncedRef = useRef(onSynced);
    const retryDelayRef = useRef(retryDelay);

    isSyncableRef.current = isSyncable;
    nowRef.current = now;
    onPersistRef.current = onPersist;
    onRemoveRef.current = onRemove;
    onRequestSyncRef.current = onRequestSync;
    onSyncedRef.current = onSynced;
    retryDelayRef.current = retryDelay;

    if (renderedArticleRef.current !== article) {
        renderedArticleRef.current = article;
        articleRef.current =
            syncStateRef.current.document === "clean"
                ? article
                : mergeArticleSynchronizationMetadata(articleRef.current, article);
    }

    const transition = useCallback((event: ArticleDraftSyncEvent) => {
        const currentState = syncStateRef.current;
        const nextState = reduceArticleDraftSyncState(currentState, event);
        if (nextState !== currentState) {
            syncStateRef.current = nextState;
            setSyncState(nextState);
        }
        return nextState;
    }, []);

    const persistCurrent = useCallback((state: ArticleDraftSyncState) => {
        onPersistRef.current(articleRef.current, updatedAtRef.current, state);
    }, []);

    const requestQueuedSync = useCallback(() => {
        const state = syncStateRef.current;
        const currentArticle = articleRef.current;
        if (state.sync !== "queued" || !isSyncableRef.current(currentArticle)) {
            return false;
        }
        onRequestSyncRef.current({
            article: currentArticle,
            revision: state.revision,
        });
        return true;
    }, []);

    useEffect(() => {
        const wasOffline = previousOfflineRef.current;
        previousOfflineRef.current = offline;
        if (!initialNetworkCheckRef.current) {
            initialNetworkCheckRef.current = true;
            requestQueuedSync();
            return;
        }
        if (wasOffline === offline) {
            return;
        }
        const currentState = syncStateRef.current;
        const nextState = transition(
            offline
                ? {
                      type: "networkLost",
                  }
                : {
                      type: "networkRestored",
                      syncable: isSyncableRef.current(articleRef.current),
                  }
        );
        if (nextState !== currentState && nextState.document !== "clean") {
            persistCurrent(nextState);
        }
        if (nextState.sync === "queued") {
            requestQueuedSync();
        }
    }, [offline, persistCurrent, requestQueuedSync, transition]);

    useEffect(() => {
        if (syncState.connectivity !== "online" || syncState.sync !== "retryWaiting") {
            return;
        }
        const retryAt = syncState.nextRetryAt || nowRef.current();
        const timer = window.setTimeout(() => {
            const currentState = syncStateRef.current;
            const nextState = transition({
                type: "retryReady",
            });
            if (nextState === currentState) {
                return;
            }
            persistCurrent(nextState);
            requestQueuedSync();
        }, Math.max(0, retryAt - nowRef.current()));
        return () => window.clearTimeout(timer);
    }, [persistCurrent, requestQueuedSync, syncState.connectivity, syncState.nextRetryAt, syncState.sync, transition]);

    const applyPatch = useCallback(
        (patch: ArticleChangeableValue, force = false): ArticleDraftChange | undefined => {
            if (!force && syncStateRef.current.sync === "conflict") {
                return undefined;
            }
            const currentArticle = articleRef.current;
            const nextArticle = {
                ...currentArticle,
                ...patch,
            };
            if (!force && deepEqualWithSpecialJSON(currentArticle, nextArticle)) {
                return undefined;
            }
            const updatedAt = nowRef.current();
            const revision = syncStateRef.current.revision + 1;
            articleRef.current = nextArticle;
            updatedAtRef.current = updatedAt;
            const nextState = transition({
                type: "edit",
                revision,
                syncable: isSyncableRef.current(nextArticle),
            });
            onPersistRef.current(nextArticle, updatedAt, nextState);
            if (nextState.sync === "queued") {
                onRequestSyncRef.current({
                    article: nextArticle,
                    revision,
                });
            }
            return {
                article: nextArticle,
                revision,
                updatedAt,
            };
        },
        [transition]
    );

    const markSyncing = useCallback(
        (task: ArticleDraftSyncTask) => {
            const currentState = syncStateRef.current;
            const nextState = transition({
                type: "syncStarted",
                revision: task.revision,
            });
            if (nextState === currentState) {
                return false;
            }
            persistCurrent(nextState);
            return true;
        },
        [persistCurrent, transition]
    );

    const markDeferred = useCallback(
        (task: ArticleDraftSyncTask) => {
            const currentState = syncStateRef.current;
            const nextState = transition({
                type: "syncDeferred",
                revision: task.revision,
            });
            if (nextState !== currentState) {
                persistCurrent(nextState);
            }
        },
        [persistCurrent, transition]
    );

    const markFailed = useCallback(
        (task: ArticleDraftSyncTask, error: unknown) => {
            const currentState = syncStateRef.current;
            if (task.revision !== currentState.revision) {
                return false;
            }
            const nextRetryCount = currentState.retryCount + 1;
            const nextState = transition({
                type: "syncFailed",
                revision: task.revision,
                error: toErrorMessage(error),
                nextRetryAt: nowRef.current() + retryDelayRef.current(nextRetryCount),
            });
            if (nextState === currentState) {
                return false;
            }
            persistCurrent(nextState);
            return true;
        },
        [persistCurrent, transition]
    );

    const markConflict = useCallback(
        (task: ArticleDraftSyncTask, error: unknown) => {
            const currentState = syncStateRef.current;
            const nextState = transition({
                type: "syncConflict",
                revision: task.revision,
                error: toErrorMessage(error),
            });
            if (nextState === currentState) {
                return false;
            }
            persistCurrent(nextState);
            return true;
        },
        [persistCurrent, transition]
    );

    const markBlocked = useCallback(
        (task: ArticleDraftSyncTask, error: unknown) => {
            const currentState = syncStateRef.current;
            const nextState = transition({
                type: "syncBlocked",
                revision: task.revision,
                error: toErrorMessage(error),
            });
            if (nextState === currentState) {
                return false;
            }
            persistCurrent(nextState);
            return true;
        },
        [persistCurrent, transition]
    );

    const markSynced = useCallback(
        (task: ArticleDraftSyncTask, savedArticle?: ArticleEntry) => {
            if (savedArticle) {
                articleRef.current = mergeArticleSynchronizationMetadata(articleRef.current, savedArticle);
            }
            const currentState = syncStateRef.current;
            const nextState = transition({
                type: "syncSucceeded",
                revision: task.revision,
            });
            if (nextState === currentState || nextState.document !== "clean") {
                if (savedArticle) {
                    onRemoveRef.current(task.article);
                    persistCurrent(currentState);
                }
                return false;
            }
            onRemoveRef.current(task.article);
            onRemoveRef.current(articleRef.current);
            onSyncedRef.current();
            return true;
        },
        [persistCurrent, transition]
    );

    const markCommitted = useCallback(() => {
        transition({ type: "commit" });
    }, [transition]);

    const resolveConflict = useCallback(
        (resolvedArticle: ArticleEntry) => {
            const updatedAt = nowRef.current();
            const revision = syncStateRef.current.revision + 1;
            articleRef.current = resolvedArticle;
            updatedAtRef.current = updatedAt;
            const nextState = transition({
                type: "resolveConflict",
                revision,
                syncable: isSyncableRef.current(resolvedArticle),
            });
            if (nextState.sync === "conflict") {
                return undefined;
            }
            onPersistRef.current(resolvedArticle, updatedAt, nextState);
            if (nextState.sync === "queued") {
                onRequestSyncRef.current({
                    article: resolvedArticle,
                    revision,
                });
            }
            return {
                article: resolvedArticle,
                revision,
                updatedAt,
            };
        },
        [transition]
    );

    const discard = useCallback(() => {
        transition({ type: "commit" });
        onRemoveRef.current(articleRef.current);
        onSyncedRef.current();
    }, [transition]);

    return {
        applyPatch,
        discard,
        markBlocked,
        markCommitted,
        markConflict,
        markDeferred,
        markFailed,
        markSynced,
        markSyncing,
        resolveConflict,
        state: syncState,
    };
};

export default useArticleDraftSync;
