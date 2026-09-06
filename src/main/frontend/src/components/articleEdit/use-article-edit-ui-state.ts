import { SetStateAction, useCallback, useEffect, useMemo, useRef, useState } from "react";
import { addToCache, getCacheByKey, removeCacheDataByKey } from "../../utils/cache";
import { PublishStatusPopoverState } from "./index.types";

type ArticleEditUiState = {
    settingsOpen?: boolean;
    versionDrawerOpen?: boolean;
    articleAssistantOpen?: boolean;
    publishStatus?: PublishStatusPopoverState;
};

type ArticleEditUiStateListener = (state: ArticleEditUiState) => void;

const articleEditUiStateListeners = new Map<string, Set<ArticleEditUiStateListener>>();

const notifyArticleEditUiState = (cacheKey: string, state: ArticleEditUiState) => {
    articleEditUiStateListeners.get(cacheKey)?.forEach((listener) => listener(state));
};

const subscribeArticleEditUiState = (cacheKey: string, listener: ArticleEditUiStateListener) => {
    const listeners = articleEditUiStateListeners.get(cacheKey) || new Set<ArticleEditUiStateListener>();
    listeners.add(listener);
    articleEditUiStateListeners.set(cacheKey, listeners);
    return () => {
        listeners.delete(listener);
        if (listeners.size === 0) {
            articleEditUiStateListeners.delete(cacheKey);
        }
    };
};

const getDefaultPublishStatus = (): PublishStatusPopoverState => ({
    open: false,
    visible: false,
    publishState: "idle",
    staticStatus: "idle",
    checkStatus: "idle",
});

const getArticleEditUiStateCacheKey = (scope: string) => `articleEdit/ui/${scope}`;
const draftCacheKey = getArticleEditUiStateCacheKey("draft");

const getCachedArticleEditUiState = (cacheKey: string): ArticleEditUiState =>
    getCacheByKey<ArticleEditUiState>(cacheKey) || {};

const writeCachedArticleEditUiState = (cacheKey: string, state: ArticleEditUiState) => {
    addToCache(cacheKey, state);
    notifyArticleEditUiState(cacheKey, state);
};

const normalizePublishState = (value: unknown): PublishStatusPopoverState["publishState"] =>
    value === "running" || value === "success" || value === "failed" ? value : "idle";

const normalizeStaticStatus = (value: unknown): PublishStatusPopoverState["staticStatus"] =>
    value === "running" || value === "success" || value === "failed" || value === "not-required" ? value : "idle";

const normalizeCachedPublishStatus = (value: unknown): PublishStatusPopoverState => {
    if (!value || typeof value !== "object") {
        return getDefaultPublishStatus();
    }
    const status = value as Partial<PublishStatusPopoverState>;
    if (
        status.checkStatus !== "idle" &&
        status.checkStatus !== "running" &&
        status.checkStatus !== "success" &&
        status.checkStatus !== "error"
    ) {
        return getDefaultPublishStatus();
    }
    return {
        open: status.open === true,
        visible: status.visible === true,
        updatedAt: typeof status.updatedAt === "number" ? status.updatedAt : undefined,
        publishState: normalizePublishState(status.publishState),
        publishText: typeof status.publishText === "string" ? status.publishText : undefined,
        publishError: typeof status.publishError === "string" ? status.publishError : undefined,
        publicUrl: typeof status.publicUrl === "string" ? status.publicUrl : undefined,
        staticStatus: normalizeStaticStatus(status.staticStatus),
        staticText: typeof status.staticText === "string" ? status.staticText : undefined,
        staticError: typeof status.staticError === "string" ? status.staticError : undefined,
        checkStatus: status.checkStatus,
        checkError: typeof status.checkError === "string" ? status.checkError : undefined,
        checkPayload: status.checkPayload,
    };
};

const useArticleEditUiState = (logId: number | undefined, search: string) => {
    const scope = useMemo(() => {
        const rawLogId = new URLSearchParams(search).get("id");
        const urlLogId = rawLogId ? Number(rawLogId) : undefined;
        const articleLogId =
            logId && logId > 0 ? logId : urlLogId && Number.isFinite(urlLogId) && urlLogId > 0 ? urlLogId : undefined;
        return articleLogId ? `article/${articleLogId}` : "draft";
    }, [logId, search]);
    const cacheKey = useMemo(() => getArticleEditUiStateCacheKey(scope), [scope]);
    const activeCacheKeyRef = useRef(cacheKey);
    const persistState = useCallback((patch: ArticleEditUiState) => {
        const activeCacheKey = activeCacheKeyRef.current;
        writeCachedArticleEditUiState(activeCacheKey, {
            ...getCachedArticleEditUiState(activeCacheKey),
            ...patch,
        });
    }, []);
    const cachedState = getCachedArticleEditUiState(cacheKey);
    const [settingsOpen, setSettingsOpenState] = useState(cachedState.settingsOpen === true);
    const [versionDrawerOpen, setVersionDrawerOpenState] = useState(cachedState.versionDrawerOpen === true);
    const [articleAssistantOpen, setArticleAssistantOpenState] = useState(cachedState.articleAssistantOpen === true);
    const [publishStatus, setPublishStatusState] = useState<PublishStatusPopoverState>(() =>
        normalizeCachedPublishStatus(cachedState.publishStatus)
    );
    const publishStatusRef = useRef(publishStatus);

    const applyState = useCallback((nextState: ArticleEditUiState) => {
        const nextPublishStatus = normalizeCachedPublishStatus(nextState.publishStatus);
        setSettingsOpenState(nextState.settingsOpen === true);
        setVersionDrawerOpenState(nextState.versionDrawerOpen === true);
        setArticleAssistantOpenState(nextState.articleAssistantOpen === true);
        publishStatusRef.current = nextPublishStatus;
        setPublishStatusState(nextPublishStatus);
    }, []);

    const restore = useCallback(() => {
        applyState(getCachedArticleEditUiState(activeCacheKeyRef.current));
    }, [applyState]);

    useEffect(() => {
        activeCacheKeyRef.current = cacheKey;
        const unsubscribe = subscribeArticleEditUiState(cacheKey, applyState);
        restore();
        return unsubscribe;
    }, [applyState, cacheKey, restore]);

    const migrateToArticle = useCallback((logId: number) => {
        if (!Number.isFinite(logId) || logId <= 0) {
            return;
        }
        const currentCacheKey = activeCacheKeyRef.current;
        const nextCacheKey = getArticleEditUiStateCacheKey(`article/${logId}`);
        if (currentCacheKey === nextCacheKey) {
            return;
        }
        writeCachedArticleEditUiState(nextCacheKey, {
            ...getCachedArticleEditUiState(nextCacheKey),
            ...getCachedArticleEditUiState(currentCacheKey),
        });
        if (currentCacheKey === draftCacheKey) {
            removeCacheDataByKey(currentCacheKey);
            notifyArticleEditUiState(currentCacheKey, {});
        }
        activeCacheKeyRef.current = nextCacheKey;
    }, []);

    const updateSettingsOpen = useCallback(
        (open: boolean) => {
            setSettingsOpenState(open);
            persistState({ settingsOpen: open });
        },
        [persistState]
    );
    const updateVersionDrawerOpen = useCallback(
        (open: boolean) => {
            setVersionDrawerOpenState(open);
            persistState({ versionDrawerOpen: open });
        },
        [persistState]
    );
    const updateArticleAssistantOpen = useCallback(
        (open: boolean) => {
            setArticleAssistantOpenState(open);
            persistState({ articleAssistantOpen: open });
        },
        [persistState]
    );
    const updatePublishStatus = useCallback(
        (action: SetStateAction<PublishStatusPopoverState>) => {
            const nextState = typeof action === "function" ? action(publishStatusRef.current) : action;
            publishStatusRef.current = nextState;
            persistState({ publishStatus: nextState });
        },
        [persistState]
    );

    return {
        scope,
        cacheKey,
        settingsOpen,
        versionDrawerOpen,
        articleAssistantOpen,
        publishStatus,
        restore,
        migrateToArticle,
        updateSettingsOpen,
        updateVersionDrawerOpen,
        updateArticleAssistantOpen,
        updatePublishStatus,
    };
};

export default useArticleEditUiState;
