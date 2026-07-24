import { useCallback, useEffect, useRef } from "react";
import { deepEqualWithSpecialJSON } from "../../../utils/helpers";
import { ArticleChangeableValue, ArticleEntry } from "../index.types";

export type ArticleOfflineSyncTask = {
    article: ArticleEntry;
    revision: number;
};

export type ArticleOfflineChange = ArticleOfflineSyncTask & {
    updatedAt: number;
};

type UseArticleOfflineDraftOptions = {
    article: ArticleEntry;
    initialDirty: boolean;
    isSyncable: (article: ArticleEntry) => boolean;
    now?: () => number;
    offline: boolean;
    onPersist: (article: ArticleEntry, updatedAt: number) => void;
    onRemove: (article: ArticleEntry) => void;
    onRequestSync: (task: ArticleOfflineSyncTask) => void;
    onSynced: () => void;
};

const useArticleOfflineDraft = ({
    article,
    initialDirty,
    isSyncable,
    now = Date.now,
    offline,
    onPersist,
    onRemove,
    onRequestSync,
    onSynced,
}: UseArticleOfflineDraftOptions) => {
    const articleRef = useRef(article);
    const renderedArticleRef = useRef(article);
    const dirtyRef = useRef(initialDirty);
    const revisionRef = useRef(initialDirty ? 1 : 0);
    const offlineRef = useRef(offline);
    const previousOfflineRef = useRef(offline);
    const initialNetworkCheckRef = useRef(false);
    const isSyncableRef = useRef(isSyncable);
    const nowRef = useRef(now);
    const onPersistRef = useRef(onPersist);
    const onRemoveRef = useRef(onRemove);
    const onRequestSyncRef = useRef(onRequestSync);
    const onSyncedRef = useRef(onSynced);

    offlineRef.current = offline;
    isSyncableRef.current = isSyncable;
    nowRef.current = now;
    onPersistRef.current = onPersist;
    onRemoveRef.current = onRemove;
    onRequestSyncRef.current = onRequestSync;
    onSyncedRef.current = onSynced;

    if (renderedArticleRef.current !== article) {
        renderedArticleRef.current = article;
        articleRef.current = article;
    }

    const requestCurrentSync = useCallback(() => {
        const currentArticle = articleRef.current;
        if (!dirtyRef.current || offlineRef.current || !isSyncableRef.current(currentArticle)) {
            return;
        }
        onRequestSyncRef.current({
            article: currentArticle,
            revision: revisionRef.current,
        });
    }, []);

    useEffect(() => {
        const wasOffline = previousOfflineRef.current;
        previousOfflineRef.current = offline;
        if (!initialNetworkCheckRef.current) {
            initialNetworkCheckRef.current = true;
            requestCurrentSync();
            return;
        }
        if (wasOffline && !offline) {
            requestCurrentSync();
        }
    }, [offline, requestCurrentSync]);

    const applyPatch = useCallback((patch: ArticleChangeableValue): ArticleOfflineChange | undefined => {
        const currentArticle = articleRef.current;
        const nextArticle = {
            ...currentArticle,
            ...patch,
        };
        if (deepEqualWithSpecialJSON(currentArticle, nextArticle)) {
            return undefined;
        }
        const updatedAt = nowRef.current();
        const revision = revisionRef.current + 1;
        revisionRef.current = revision;
        dirtyRef.current = true;
        articleRef.current = nextArticle;
        onPersistRef.current(nextArticle, updatedAt);
        if (!offlineRef.current && isSyncableRef.current(nextArticle)) {
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
    }, []);

    const markSynced = useCallback((task: ArticleOfflineSyncTask) => {
        if (task.revision !== revisionRef.current) {
            return false;
        }
        dirtyRef.current = false;
        onRemoveRef.current(task.article);
        onSyncedRef.current();
        return true;
    }, []);

    const markCommitted = useCallback(() => {
        dirtyRef.current = false;
    }, []);

    return {
        applyPatch,
        markCommitted,
        markSynced,
    };
};

export default useArticleOfflineDraft;
