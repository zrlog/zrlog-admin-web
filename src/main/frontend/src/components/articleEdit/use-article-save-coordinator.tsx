import { RefObject, useEffect, useRef, useState } from "react";
import { InputRef, Space } from "antd";
import { MessageInstance } from "antd/es/message/interface";
import { HookAPI as ModalHookAPI } from "antd/es/modal/useModal";
import { AxiosInstance } from "axios";
import { Location, NavigateFunction } from "react-router";
import { auditTime, concatMap, Subject, tap } from "rxjs";
import { Subscription } from "rxjs/internal/Subscription";
import { createUri, getRes, updateUri } from "../../utils/constants";
import { isOffline } from "../../utils/env-utils";
import {
    articleDataToState,
    articleSaveToCache,
    getArticleDraftSyncState,
    removeArticleCache,
    removeLocalArticleCache,
} from "../../utils/article-cache";
import { deepEqualWithSpecialJSON, disableExitTips, enableExitTips, updateDocumentTitle } from "../../utils/helpers";
import { getCacheByKey, getPageDataCacheKeyByPath } from "../../utils/cache";
import { ApiResponse } from "../../type";
import {
    ArticleChangeableValue,
    ArticleEditInfo,
    ArticleEditState,
    ArticleEntry,
    PublishStatusPopoverState,
} from "./index.types";
import useArticleDraftSync, { ArticleDraftSyncTask } from "./draft-sync/use-article-draft-sync";
import {
    isRetryableArticleSyncError,
    mergeArticleSynchronizationMetadata,
} from "./draft-sync/article-draft-sync-helpers";
import useTransparentPublish from "./use-transparent-publish";
import { AIContent } from "@editor/dist/ai/AIContentItem";

const ARTICLE_UPDATE_EXPIRED_ERROR = 9094;

type ArticleAutoSaveOutcome =
    | {
          type: "deferred";
      }
    | {
          type: "blocked" | "conflict";
          message: string;
      };

type UseArticleSaveCoordinatorOptions = {
    aliasRef: RefObject<InputRef>;
    axiosInstance: AxiosInstance;
    data: ArticleEditInfo;
    digestRef: RefObject<InputRef>;
    editCardRef: RefObject<HTMLDivElement>;
    location: Location;
    messageApi: MessageInstance;
    modal: ModalHookAPI;
    navigate: NavigateFunction;
    offline: boolean;
    preferredTypeId?: number;
    restoreUiState: () => void;
    updateCache?: (cache: ArticleEditInfo, cacheKey: string) => void;
    updatePublishStatus: (
        action: PublishStatusPopoverState | ((previousState: PublishStatusPopoverState) => PublishStatusPopoverState)
    ) => void;
};

const useArticleSaveCoordinator = ({
    aliasRef,
    axiosInstance,
    data,
    digestRef,
    editCardRef,
    location,
    messageApi,
    modal,
    navigate,
    offline,
    preferredTypeId,
    restoreUiState,
    updateCache,
    updatePublishStatus,
}: UseArticleSaveCoordinatorOptions) => {
    const defaultState = articleDataToState(data, preferredTypeId);
    const [state, setState] = useState<ArticleEditState>(defaultState);
    const [restoreInputRevision, setRestoreInputRevision] = useState(0);
    const contentSourceRef = useRef(state.contentSource);
    contentSourceRef.current = state.contentSource;
    const loadedArticleRef = useRef<ArticleEntry>(defaultState.article);
    const versionRef = useRef(defaultState.article.version);
    const previewUrlRef = useRef(defaultState.article.previewUrl);
    const logIdRef = useRef(defaultState.article.logId || -1);
    const subjectRef = useRef<Subject<ArticleDraftSyncTask> | null>(null);
    const subRef = useRef<Subscription | null>(null);
    const pendingMessagesRef = useRef(0);
    const markDraftSyncingRef = useRef<(task: ArticleDraftSyncTask) => boolean>(() => false);
    const markDraftSyncedRef = useRef<(task: ArticleDraftSyncTask, savedArticle?: ArticleEntry) => boolean>(
        () => false
    );
    const markDraftDeferredRef = useRef<(task: ArticleDraftSyncTask) => void>(() => undefined);
    const markDraftFailedRef = useRef<(task: ArticleDraftSyncTask, error: unknown) => boolean>(() => false);
    const markDraftConflictRef = useRef<(task: ArticleDraftSyncTask, error: unknown) => boolean>(() => false);
    const markDraftBlockedRef = useRef<(task: ArticleDraftSyncTask, error: unknown) => boolean>(() => false);
    const markDraftCommittedRef = useRef<() => void>(() => undefined);
    const autoSaveOutcomeRef = useRef<ArticleAutoSaveOutcome>();
    const autoSaveAcknowledgedArticleRef = useRef<ArticleEntry>();

    const getLocalCacheKey = (url: URL) =>
        getPageDataCacheKeyByPath(location.pathname, "?" + url.searchParams.toString());

    const getLocalContentSource = (article: ArticleEntry): ArticleEditState["contentSource"] =>
        article.logId && article.logId > 0 ? "localEdit" : "localDraft";

    const updateAiMessageCache = (aiMessages: AIContent[]) => {
        const url = new URL(window.location.href);
        const cacheKey = getLocalCacheKey(url);
        const cachedData = getCacheByKey(cacheKey) as ArticleEditInfo | undefined;
        const newData = {
            ...(cachedData || data),
            aiMessages,
        };
        updateCache?.(newData, cacheKey);
        setState((previousState) => ({
            ...previousState,
            aiMessages,
        }));
    };

    const postArticleWithTransparentPublish = useTransparentPublish({
        aiMessages: state.aiMessages,
        messageApi,
        onAiMessagesChange: updateAiMessageCache,
        updatePublishStatus,
    });

    useEffect(() => {
        const newState = articleDataToState(data, preferredTypeId);
        const serverArticle = data.article.logId && data.article.logId > 0;
        if (!serverArticle && contentSourceRef.current === "localDraft") {
            setState((previousState) => ({
                ...previousState,
                typeOptions: newState.typeOptions,
                tags: newState.tags,
                aiProvider: newState.aiProvider,
                aiModel: newState.aiModel,
                aiConfigured: newState.aiConfigured,
                aiMessages: newState.aiMessages,
                linkPreviewEnabled: newState.linkPreviewEnabled,
                publishCheckEnabled: newState.publishCheckEnabled,
                articleCoverAspectRatio: newState.articleCoverAspectRatio,
                articleEditAutoSaveInterval: newState.articleEditAutoSaveInterval,
            }));
            return;
        }
        if (!deepEqualWithSpecialJSON(loadedArticleRef.current, newState.article)) {
            loadedArticleRef.current = newState.article;
            setState(newState);
            restoreUiState();
            setRestoreInputRevision((revision) => revision + 1);
            versionRef.current = newState.article.version;
            previewUrlRef.current = newState.article.previewUrl;
            logIdRef.current = newState.article.logId || -1;
            return;
        }
        setState((previousState) => ({
            ...previousState,
            typeOptions: newState.typeOptions,
            tags: newState.tags,
            aiProvider: newState.aiProvider,
            aiModel: newState.aiModel,
            aiConfigured: newState.aiConfigured,
            aiMessages: newState.aiMessages,
            linkPreviewEnabled: newState.linkPreviewEnabled,
            publishCheckEnabled: newState.publishCheckEnabled,
            articleCoverAspectRatio: newState.articleCoverAspectRatio,
            articleEditAutoSaveInterval: newState.articleEditAutoSaveInterval,
            editorVersion: newState.editorVersion,
        }));
    }, [data, preferredTypeId]);

    useEffect(() => {
        setState((previousState) => ({
            ...previousState,
            aiProvider: data.aiProvider,
            aiModel: data.aiModel,
            aiConfigured: data.aiConfigured === true,
            aiMessages: data.aiMessages,
        }));
    }, [data.aiConfigured, data.aiMessages, data.aiModel, data.aiProvider]);

    const finishPendingAutoSave = () => {
        pendingMessagesRef.current = Math.max(0, pendingMessagesRef.current - 1);
        if (pendingMessagesRef.current === 0) {
            disableExitTips();
        }
    };

    const mergeArticleResponse = (
        stateArticle: ArticleEntry,
        responseArticle: ArticleEntry,
        create: boolean
    ): ArticleEntry => {
        const mergedArticle = {
            ...responseArticle,
            logId: responseArticle.logId,
            lastUpdateDate: responseArticle.lastUpdateDate,
            version: responseArticle.version,
            thumbnail:
                stateArticle.thumbnail && stateArticle.thumbnail.trim().length > 0
                    ? stateArticle.thumbnail
                    : responseArticle.thumbnail,
        };
        if (aliasRef.current?.input) {
            if (aliasRef.current.input.value.trim().length === 0 && create) {
                mergedArticle.alias = responseArticle.alias;
                aliasRef.current.input.value = responseArticle.alias as string;
            } else {
                mergedArticle.alias = aliasRef.current.input.value;
            }
        }
        if (digestRef.current?.input && digestRef.current.input.value.trim().length === 0 && create) {
            mergedArticle.digest = responseArticle.digest;
            digestRef.current.input.value = responseArticle.digest as string;
        }
        return mergedArticle;
    };

    const updateRubbishState = (newArticle: ArticleEntry, create: boolean) => {
        setState((previousState) => ({
            ...previousState,
            rubbish: true,
            article: mergeArticleResponse(previousState.article, newArticle, create),
            saving: {
                ...previousState.saving,
                rubbishSaving: false,
                previewIng: false,
                autoSaving: false,
            },
        }));
    };

    const updateReleaseState = (newArticle: ArticleEntry, create: boolean) => {
        setState((previousState) => ({
            ...previousState,
            rubbish: false,
            article: mergeArticleResponse(previousState.article, newArticle, create),
            saving: {
                ...previousState.saving,
                releaseSaving: false,
                rubbishSaving: false,
                previewIng: false,
                autoSaving: false,
            },
        }));
    };

    const finishAutoSave = (savedArticle?: ArticleEntry, create = false) => {
        setState((previousState) => ({
            ...previousState,
            rubbish: true,
            article: savedArticle
                ? mergeArticleSynchronizationMetadata(previousState.article, savedArticle, create)
                : previousState.article,
            saving: {
                ...previousState.saving,
                rubbishSaving: false,
                previewIng: false,
                autoSaving: false,
            },
        }));
    };

    const persistToCache = (newArticle: ArticleEntry) => {
        const updatedAt = Date.now();
        articleSaveToCache(newArticle, updatedAt);
        setState((previousState) => ({
            ...previousState,
            article: newArticle,
            contentSource: getLocalContentSource(newArticle),
            contentSourceUpdatedAt: updatedAt,
            saving: {
                ...previousState.saving,
                releaseSaving: false,
                rubbishSaving: false,
                previewIng: false,
                autoSaving: false,
            },
        }));
        if (pendingMessagesRef.current === 0) {
            disableExitTips();
        }
    };

    const handleArticleResponse = (
        response: any,
        baseArticle: ArticleEntry,
        create: boolean,
        autoSave: boolean,
        showMessage = true
    ) => {
        if (response.documentTitle) {
            updateDocumentTitle(response.documentTitle);
        }
        if (pendingMessagesRef.current === 0) {
            disableExitTips();
        }
        if (!autoSave && showMessage) {
            messageApi.info(response.message);
        }
        const responseArticle = response.data.article;
        previewUrlRef.current = responseArticle.previewUrl;
        const url = new URL(window.location.href);
        let nextArticle: ArticleEntry;
        if (create) {
            logIdRef.current = responseArticle.logId;
            url.searchParams.set("id", responseArticle.logId);
            nextArticle = { ...baseArticle, ...responseArticle };
            if (!autoSave) {
                removeLocalArticleCache();
            }
            navigate(location.pathname + url.search, { replace: true });
        } else {
            nextArticle = {
                ...baseArticle,
                ...responseArticle,
                thumbnail: responseArticle.thumbnail,
                lastUpdateDate: responseArticle.lastUpdateDate,
                version: responseArticle.version,
            };
            if (!autoSave) {
                removeArticleCache(nextArticle);
            }
        }
        if (!autoSave) {
            markDraftCommittedRef.current();
        }
        updateCache?.(response.data, getLocalCacheKey(url));
        return nextArticle;
    };

    let resetAutoSaveQueue = () => undefined;

    const onSubmit = async (
        article: ArticleEntry,
        release: boolean,
        preview: boolean,
        autoSave: boolean
    ): Promise<boolean> => {
        if (autoSave) {
            autoSaveOutcomeRef.current = undefined;
            autoSaveAcknowledgedArticleRef.current = undefined;
        }
        let newArticle: ArticleEntry = {
            ...article,
            version: versionRef.current,
            rubbish: !release,
            transparentPublish: release && !autoSave && article.privacy !== true,
        };
        if (!article.title) {
            messageApi.error({ content: getRes().articleEdit.requireTitle });
            return false;
        }
        if (article.typeId === undefined || article.typeId === null || article.typeId < 0) {
            messageApi.error(getRes().articleEdit.requireType);
            return false;
        }
        if (isOffline()) {
            if (autoSave) {
                autoSaveOutcomeRef.current = { type: "deferred" };
            }
            persistToCache(newArticle);
            return !autoSave;
        }
        if (!autoSave) {
            resetAutoSaveQueue();
        }
        const create = article.logId === undefined || article.logId === null || article.logId <= 0;
        const uri = create ? createUri : updateUri;
        setState((previousState) => ({
            ...previousState,
            saving: release
                ? {
                      ...previousState.saving,
                      releaseSaving: true,
                      autoSaving: autoSave,
                  }
                : {
                      ...previousState.saving,
                      rubbishSaving: true,
                      previewIng: preview,
                      autoSaving: autoSave,
                  },
        }));
        enableExitTips(getRes().articleEdit.editExitWithoutSave);
        let saveSucceeded = false;
        try {
            let responseData;
            try {
                const response = newArticle.transparentPublish
                    ? await postArticleWithTransparentPublish(uri, newArticle)
                    : (await axiosInstance.post(uri, newArticle, autoSave ? ({ showError: false } as any) : undefined))
                          .data;
                responseData = response;
                if (response.error) {
                    if (autoSave) {
                        autoSaveOutcomeRef.current = {
                            type: response.error === ARTICLE_UPDATE_EXPIRED_ERROR ? "conflict" : "blocked",
                            message: response.message || getRes().articleEdit.saveFailed,
                        };
                    } else {
                        modal.error({
                            title: getRes().articleEdit.saveFailed,
                            content: response.message,
                            getContainer: () => editCardRef.current as HTMLElement,
                        });
                    }
                    return false;
                }
                if (response.data) {
                    versionRef.current = response.data.article.version;
                }
            } catch (error) {
                if (newArticle.transparentPublish) {
                    updatePublishStatus((previousState) => ({
                        ...previousState,
                        open: true,
                        visible: true,
                        updatedAt: Date.now(),
                        publishError: error instanceof Error ? error.message : getRes().articleEdit.saveFailed,
                    }));
                    return false;
                }
                throw error;
            }
            if (responseData.error === 0) {
                newArticle = handleArticleResponse(
                    responseData,
                    newArticle,
                    create,
                    autoSave,
                    !newArticle.transparentPublish
                );
                saveSucceeded = true;
                if (autoSave) {
                    autoSaveAcknowledgedArticleRef.current = newArticle;
                }
                return true;
            }
            return false;
        } finally {
            if (autoSave) {
                finishAutoSave(saveSucceeded ? newArticle : undefined, create);
            } else if (release) {
                updateReleaseState(newArticle, create);
            } else {
                updateRubbishState(newArticle, create);
            }
        }
    };

    const onPreview = async () => {
        const saved = await onSubmit(state.article, false, true, false);
        if (saved && previewUrlRef.current) {
            window.open(previewUrlRef.current, "_blank", "noopener,noreferrer");
        }
    };

    const loadServerArticleForConflict = async (task: ArticleDraftSyncTask) => {
        const logId = task.article.logId || logIdRef.current;
        if (!logId || logId <= 0) {
            return;
        }
        try {
            const { data: response } = await axiosInstance.get<ApiResponse<ArticleEditInfo>>(
                "/api/admin/article-edit",
                {
                    params: { id: logId },
                    showError: false,
                } as any
            );
            if (response.error || !response.data?.article) {
                return;
            }
            const serverArticle = response.data.article;
            versionRef.current = serverArticle.version;
            loadedArticleRef.current = serverArticle;
            setState((previousState) => ({
                ...previousState,
                article: serverArticle,
                rubbish: serverArticle.rubbish === true,
                editorVersion: serverArticle.version,
                contentConflict: {
                    source: "localEdit",
                    localArticle: task.article,
                    localVersion: task.article.version,
                    localUpdatedAt: previousState.contentSourceUpdatedAt,
                    serverVersion: serverArticle.version,
                },
            }));
            setRestoreInputRevision((revision) => revision + 1);
        } catch (error) {
            console.error(error);
        }
    };

    resetAutoSaveQueue = () => {
        subRef.current?.unsubscribe();
        pendingMessagesRef.current = 0;
        const autoSaveInterval = [2, 5, 10].includes(state.articleEditAutoSaveInterval)
            ? state.articleEditAutoSaveInterval
            : 5;
        subjectRef.current = new Subject();
        subRef.current = subjectRef.current
            .pipe(
                tap(() => enableExitTips(getRes().articleEdit.editExitWithoutSave)),
                auditTime(autoSaveInterval * 1000),
                tap(() => {
                    pendingMessagesRef.current += 1;
                }),
                concatMap(async (task) => {
                    if (!markDraftSyncingRef.current(task)) {
                        finishPendingAutoSave();
                        return;
                    }
                    const nextArticle = {
                        ...task.article,
                        logId: logIdRef.current,
                    };
                    try {
                        const saved = await onSubmit(nextArticle, false, false, true);
                        if (saved) {
                            markDraftSyncedRef.current(task, autoSaveAcknowledgedArticleRef.current);
                            return;
                        }
                        const outcome = autoSaveOutcomeRef.current;
                        if (outcome?.type === "deferred") {
                            markDraftDeferredRef.current(task);
                        } else if (outcome?.type === "conflict") {
                            if (markDraftConflictRef.current(task, outcome.message)) {
                                await loadServerArticleForConflict(task);
                            }
                        } else if (outcome?.type === "blocked") {
                            if (markDraftBlockedRef.current(task, outcome.message)) {
                                void messageApi.error(outcome.message);
                            }
                        } else {
                            markDraftFailedRef.current(task, getRes().articleEdit.saveFailed);
                        }
                    } catch (error) {
                        if (isOffline()) {
                            markDraftDeferredRef.current(task);
                        } else if (isRetryableArticleSyncError(error)) {
                            markDraftFailedRef.current(task, error);
                        } else {
                            markDraftBlockedRef.current(task, error);
                        }
                    } finally {
                        finishPendingAutoSave();
                    }
                })
            )
            .subscribe();
    };

    useEffect(() => {
        resetAutoSaveQueue();
        return () => subRef.current?.unsubscribe();
    }, [state.articleEditAutoSaveInterval]);

    const isSyncable = (article: ArticleEntry) =>
        Boolean(article.title) && article.typeId !== undefined && article.typeId !== null && article.typeId >= 0;

    const draftSync = useArticleDraftSync({
        article: state.article,
        initialDirty: defaultState.contentSource !== "server" || Boolean(defaultState.contentConflict),
        initialConflict: Boolean(defaultState.contentConflict),
        initialState: getArticleDraftSyncState(defaultState.article),
        initialUpdatedAt: defaultState.contentSourceUpdatedAt,
        offline,
        isSyncable,
        onPersist: articleSaveToCache,
        onRemove: removeArticleCache,
        onRequestSync: (task) => subjectRef.current?.next(task),
        onSynced: () => {
            setState((previousState) => ({
                ...previousState,
                contentSource: "server",
                contentSourceUpdatedAt: undefined,
            }));
        },
    });

    markDraftSyncingRef.current = draftSync.markSyncing;
    markDraftSyncedRef.current = draftSync.markSynced;
    markDraftDeferredRef.current = draftSync.markDeferred;
    markDraftFailedRef.current = draftSync.markFailed;
    markDraftConflictRef.current = draftSync.markConflict;
    markDraftBlockedRef.current = draftSync.markBlocked;
    markDraftCommittedRef.current = draftSync.markCommitted;

    const handleValuesChange = (changeableValue: ArticleChangeableValue) => {
        const change = draftSync.applyPatch(changeableValue);
        if (!change) {
            return;
        }
        setState((previousState) => ({
            ...previousState,
            article: change.article,
            contentSource: getLocalContentSource(change.article),
            contentSourceUpdatedAt: change.updatedAt,
        }));
    };

    const onRollback = async (targetVersion: number) => {
        if (!state.article.logId) {
            return;
        }
        const { data: response } = await axiosInstance.post("/api/admin/article-version/rollback", {
            logId: state.article.logId,
            version: versionRef.current,
            targetVersion,
        });
        if (response.error) {
            modal.confirm({
                title: getRes().articleEdit.rollbackFailed,
                content: (
                    <Space direction="vertical" size={8}>
                        <span>{response.message}</span>
                        <span>{getRes().articleEdit.rollbackConflictTip}</span>
                    </Space>
                ),
                okText: getRes().articleEdit.rollbackRefresh,
                cancelText: getRes().cancel,
                getContainer: () => editCardRef.current as HTMLElement,
                onOk: () => window.location.reload(),
            });
            return;
        }
        const mergedArticle = handleArticleResponse(response, state.article, false, false);
        if (mergedArticle.rubbish) {
            updateRubbishState(mergedArticle, false);
        } else {
            updateReleaseState(mergedArticle, false);
        }
    };

    const useLocalConflictContent = () => {
        const conflict = state.contentConflict;
        if (!conflict) {
            return;
        }
        const localArticle = {
            ...conflict.localArticle,
            logId: state.article.logId,
            lastUpdateDate: state.article.lastUpdateDate,
            previewUrl: state.article.previewUrl,
            version: conflict.serverVersion,
        };
        versionRef.current = conflict.serverVersion;
        enableExitTips(getRes().articleEdit.editExitWithoutSave);
        draftSync.resolveConflict(localArticle);
        setState((previousState) => ({
            ...previousState,
            article: localArticle,
            rubbish: localArticle.rubbish === true,
            editorVersion: localArticle.version,
            contentSource: conflict.source,
            contentSourceUpdatedAt: conflict.localUpdatedAt,
            contentConflict: undefined,
        }));
        setRestoreInputRevision((revision) => revision + 1);
    };

    const keepServerConflictContent = () => {
        draftSync.discard();
        setState((previousState) => ({
            ...previousState,
            contentConflict: undefined,
        }));
    };

    return {
        getLocalCacheKey,
        handleValuesChange,
        isSaving: state.saving.rubbishSaving || state.saving.releaseSaving || state.saving.previewIng,
        keepServerConflictContent,
        onPreview,
        onRollback,
        onSubmit,
        restoreInputRevision,
        state,
        updateAiMessageCache,
        useLocalConflictContent,
    };
};

export default useArticleSaveCoordinator;
