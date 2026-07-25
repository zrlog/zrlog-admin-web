import { SetStateAction, useCallback, useEffect, useMemo, useState } from "react";
import { addToCache, getCacheByKey } from "../../utils/cache";
import { PublishStatusPopoverState } from "./index.types";

type ArticleEditUiState = {
    settingsOpen?: boolean;
    versionDrawerOpen?: boolean;
    articleAssistantOpen?: boolean;
    publishStatus?: PublishStatusPopoverState;
};

const getDefaultPublishStatus = (): PublishStatusPopoverState => ({
    open: false,
    visible: false,
    checkStatus: "idle",
});

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
        publishText: typeof status.publishText === "string" ? status.publishText : undefined,
        publishError: typeof status.publishError === "string" ? status.publishError : undefined,
        staticText: typeof status.staticText === "string" ? status.staticText : undefined,
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
    const cacheKey = useMemo(() => `articleEdit/ui/${scope}`, [scope]);
    const getCachedState = useCallback(() => getCacheByKey<ArticleEditUiState>(cacheKey) || {}, [cacheKey]);
    const persistState = useCallback(
        (patch: ArticleEditUiState) => {
            addToCache(cacheKey, {
                ...getCachedState(),
                ...patch,
            });
        },
        [cacheKey, getCachedState]
    );
    const cachedState = getCachedState();
    const [settingsOpen, setSettingsOpenState] = useState(cachedState.settingsOpen === true);
    const [versionDrawerOpen, setVersionDrawerOpenState] = useState(cachedState.versionDrawerOpen === true);
    const [articleAssistantOpen, setArticleAssistantOpenState] = useState(cachedState.articleAssistantOpen === true);
    const [publishStatus, setPublishStatusState] = useState<PublishStatusPopoverState>(() =>
        normalizeCachedPublishStatus(cachedState.publishStatus)
    );

    const restore = useCallback(() => {
        const nextState = getCachedState();
        setSettingsOpenState(nextState.settingsOpen === true);
        setVersionDrawerOpenState(nextState.versionDrawerOpen === true);
        setArticleAssistantOpenState(nextState.articleAssistantOpen === true);
        setPublishStatusState(normalizeCachedPublishStatus(nextState.publishStatus));
    }, [getCachedState]);

    useEffect(() => {
        restore();
    }, [restore]);

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
            setPublishStatusState((previousState) => {
                const nextState = typeof action === "function" ? action(previousState) : action;
                persistState({ publishStatus: nextState });
                return nextState;
            });
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
        updateSettingsOpen,
        updateVersionDrawerOpen,
        updateArticleAssistantOpen,
        updatePublishStatus,
    };
};

export default useArticleEditUiState;
