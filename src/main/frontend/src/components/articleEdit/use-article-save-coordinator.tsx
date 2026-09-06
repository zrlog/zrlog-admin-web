import { RefObject, SetStateAction, useCallback, useEffect, useMemo, useRef, useState } from "react";
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
import { getCacheByKey, getPageDataCacheKeyByPath, removeCacheDataByKey } from "../../utils/cache";
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
import { renderMissingMarkdownContent } from "./article-save-content";
import { markdownToHtml } from "@editor/dist/editor/utils/marked-utils";
import { DraftAiSaveGate, DraftArticleOperationRelease } from "./draft-ai-save-gate";

const ARTICLE_UPDATE_EXPIRED_ERROR = 9094;

type ArticleAiMessagesListener = (messages: AIContent[]) => void;

const articleAiMessagesStore = new Map<string, AIContent[]>();
const articleAiMessagesListeners = new Map<string, Set<ArticleAiMessagesListener>>();

const readArticleAiMessages = (cacheKey: string, fallback: AIContent[]) => {
    const cachedData = getCacheByKey<ArticleEditInfo | undefined>(cacheKey);
    const messages = cachedData?.aiMessages || articleAiMessagesStore.get(cacheKey) || fallback;
    articleAiMessagesStore.set(cacheKey, messages);
    return messages;
};

const publishArticleAiMessages = (cacheKey: string, messages: AIContent[]) => {
    const listeners = articleAiMessagesListeners.get(cacheKey);
    if (!listeners || listeners.size === 0) {
        articleAiMessagesStore.delete(cacheKey);
        return;
    }
    articleAiMessagesStore.set(cacheKey, messages);
    listeners.forEach((listener) => listener(messages));
};

const subscribeArticleAiMessages = (cacheKey: string, listener: ArticleAiMessagesListener) => {
    const listeners = articleAiMessagesListeners.get(cacheKey) || new Set<ArticleAiMessagesListener>();
    listeners.add(listener);
    articleAiMessagesListeners.set(cacheKey, listeners);
    return () => {
        listeners.delete(listener);
        if (listeners.size === 0) {
            articleAiMessagesListeners.delete(cacheKey);
            articleAiMessagesStore.delete(cacheKey);
        }
    };
};

const mergeArticleAiMessages = (...groups: Array<AIContent[] | undefined>) => {
    const merged: AIContent[] = [];
    const messageIds = new Set<string>();
    const messageReferences = new Set<AIContent>();
    const getMessageId = (message: AIContent) => (message as AIContent & { messageId?: string }).messageId;
    const getSemanticKey = (message: AIContent) => {
        const toolAwareMessage = message as AIContent & {
            messageType?: string;
            tool?: string;
        };
        return JSON.stringify([
            toolAwareMessage.role || "",
            toolAwareMessage.content || "",
            toolAwareMessage.messageType || "",
            toolAwareMessage.tool || "",
        ]);
    };

    groups.forEach((messages) => {
        const previousSemanticIndexes = new Map<string, number[]>();
        merged.forEach((message, index) => {
            const key = getSemanticKey(message);
            const indexes = previousSemanticIndexes.get(key) || [];
            indexes.push(index);
            previousSemanticIndexes.set(key, indexes);
        });
        const reconciledIndexes = new Set<number>();

        messages?.forEach((message) => {
            const messageId = (message as AIContent & { messageId?: string }).messageId;
            if (messageId ? messageIds.has(messageId) : messageReferences.has(message)) {
                return;
            }
            const semanticMatch = previousSemanticIndexes.get(getSemanticKey(message))?.find((index) => {
                if (reconciledIndexes.has(index)) {
                    return false;
                }
                const existingMessageId = getMessageId(merged[index]);
                return !messageId || !existingMessageId;
            });
            if (semanticMatch !== undefined) {
                reconciledIndexes.add(semanticMatch);
                messageReferences.add(message);
                if (messageId) {
                    const existingMessage = merged[semanticMatch];
                    merged[semanticMatch] = {
                        ...message,
                        ...existingMessage,
                        messageId,
                    } as AIContent;
                    messageIds.add(messageId);
                }
                return;
            }
            if (messageId) {
                messageIds.add(messageId);
            } else {
                messageReferences.add(message);
            }
            merged.push(message);
        });
    });
    return merged;
};

const migrateArticleAiMessageScope = (
    sourceCacheKey: string,
    targetCacheKey: string,
    responseMessages: AIContent[]
) => {
    const sourceCachedMessages = getCacheByKey<ArticleEditInfo | undefined>(sourceCacheKey)?.aiMessages;
    const targetCachedMessages = getCacheByKey<ArticleEditInfo | undefined>(targetCacheKey)?.aiMessages;
    const mergedMessages = mergeArticleAiMessages(
        sourceCachedMessages,
        articleAiMessagesStore.get(sourceCacheKey),
        responseMessages,
        targetCachedMessages,
        articleAiMessagesStore.get(targetCacheKey)
    );
    articleAiMessagesStore.delete(sourceCacheKey);
    articleAiMessagesListeners.delete(sourceCacheKey);
    removeCacheDataByKey(sourceCacheKey);
    publishArticleAiMessages(targetCacheKey, mergedMessages);
    return mergedMessages;
};

type ArticleAutoSaveOutcome =
    | {
          type: "aiPending" | "deferred";
      }
    | {
          type: "blocked" | "conflict";
          message: string;
      };

type UseArticleSaveCoordinatorOptions = {
    aliasRef: RefObject<InputRef>;
    axiosInstance: AxiosInstance;
    data: ArticleEditInfo;
    draftAiPendingCount: number;
    draftAiSaveGate: DraftAiSaveGate;
    digestRef: RefObject<InputRef>;
    editCardRef: RefObject<HTMLDivElement>;
    location: Location;
    messageApi: MessageInstance;
    modal: ModalHookAPI;
    navigate: NavigateFunction;
    offline: boolean;
    preferredTypeId?: number;
    migrateUiStateToArticle: (logId: number) => void;
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
    draftAiPendingCount,
    draftAiSaveGate,
    digestRef,
    editCardRef,
    location,
    messageApi,
    modal,
    navigate,
    offline,
    preferredTypeId,
    migrateUiStateToArticle,
    restoreUiState,
    updateCache,
    updatePublishStatus,
}: UseArticleSaveCoordinatorOptions) => {
    const defaultState = articleDataToState(data, preferredTypeId);
    const [state, setState] = useState<ArticleEditState>(defaultState);
    const [restoreInputRevision, setRestoreInputRevision] = useState(0);
    const savingRef = useRef(state.saving);
    savingRef.current = state.saving;
    const contentSourceRef = useRef(state.contentSource);
    contentSourceRef.current = state.contentSource;
    const loadedArticleRef = useRef<ArticleEntry>(defaultState.article);
    const versionRef = useRef(defaultState.article.version);
    const logIdRef = useRef(defaultState.article.logId || -1);
    const subjectRef = useRef<Subject<ArticleDraftSyncTask> | null>(null);
    const subRef = useRef<Subscription | null>(null);
    const pendingMessagesRef = useRef(0);
    const latestAutoSaveTaskRef = useRef<ArticleDraftSyncTask>();
    const importedDraftCreatePendingRef = useRef(false);
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
    const previousDraftAiPendingCountRef = useRef(draftAiPendingCount);

    const articlePageCacheKey = useMemo(
        () => getPageDataCacheKeyByPath(location.pathname, location.search),
        [location.pathname, location.search]
    );

    const getLocalCacheKey = (url: URL) => getPageDataCacheKeyByPath(url.pathname, "?" + url.searchParams.toString());

    const getArticleRouteUrl = () => new URL(`${location.pathname}${location.search}`, window.location.origin);

    const getLocalContentSource = (article: ArticleEntry): ArticleEditState["contentSource"] =>
        article.logId && article.logId > 0 ? "localEdit" : "localDraft";

    const updateAiMessageCache = useCallback(
        (action: SetStateAction<AIContent[]>, articleId?: number) => {
            const url = getArticleRouteUrl();
            if (articleId !== undefined) {
                if (articleId > 0) {
                    url.searchParams.set("id", String(articleId));
                } else {
                    url.searchParams.delete("id");
                }
            }
            const cacheKey = getLocalCacheKey(url);
            const cachedData = getCacheByKey<ArticleEditInfo | undefined>(cacheKey);
            const currentMessages = readArticleAiMessages(cacheKey, cachedData?.aiMessages || data.aiMessages);
            const aiMessages = typeof action === "function" ? action(currentMessages) : action;
            const newData = {
                ...(cachedData || data),
                aiMessages,
            };
            updateCache?.(newData, cacheKey);
            publishArticleAiMessages(cacheKey, aiMessages);
        },
        [data, location.pathname, location.search, updateCache]
    );

    const postArticleWithTransparentPublish = useTransparentPublish({
        messageApi,
        onAiMessagesChange: updateAiMessageCache,
        updatePublishStatus,
    });

    useEffect(() => {
        const applyMessages = (aiMessages: AIContent[]) => {
            setState((previousState) => ({
                ...previousState,
                aiMessages,
            }));
        };
        const unsubscribe = subscribeArticleAiMessages(articlePageCacheKey, applyMessages);
        applyMessages(readArticleAiMessages(articlePageCacheKey, data.aiMessages));
        return unsubscribe;
    }, [articlePageCacheKey, data.aiMessages]);

    useEffect(() => {
        const newState = articleDataToState(data, preferredTypeId);
        const aiMessages = readArticleAiMessages(articlePageCacheKey, newState.aiMessages);
        const serverArticle = data.article.logId && data.article.logId > 0;
        if (!serverArticle && contentSourceRef.current === "localDraft") {
            setState((previousState) => ({
                ...previousState,
                typeOptions: newState.typeOptions,
                tags: newState.tags,
                aiProvider: newState.aiProvider,
                aiModel: newState.aiModel,
                aiConfigured: newState.aiConfigured,
                aiMessages,
                linkPreviewEnabled: newState.linkPreviewEnabled,
                publishCheckEnabled: newState.publishCheckEnabled,
                articleCoverAspectRatio: newState.articleCoverAspectRatio,
                articleEditAutoSaveInterval: newState.articleEditAutoSaveInterval,
            }));
            return;
        }
        if (!deepEqualWithSpecialJSON(loadedArticleRef.current, newState.article)) {
            loadedArticleRef.current = newState.article;
            setState({ ...newState, aiMessages });
            restoreUiState();
            setRestoreInputRevision((revision) => revision + 1);
            versionRef.current = newState.article.version;
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
            aiMessages,
            linkPreviewEnabled: newState.linkPreviewEnabled,
            publishCheckEnabled: newState.publishCheckEnabled,
            articleCoverAspectRatio: newState.articleCoverAspectRatio,
            articleEditAutoSaveInterval: newState.articleEditAutoSaveInterval,
            editorVersion: newState.editorVersion,
        }));
    }, [articlePageCacheKey, data, preferredTypeId]);

    useEffect(() => {
        const aiMessages = readArticleAiMessages(articlePageCacheKey, data.aiMessages);
        setState((previousState) => ({
            ...previousState,
            aiProvider: data.aiProvider,
            aiModel: data.aiModel,
            aiConfigured: data.aiConfigured === true,
            aiMessages,
        }));
    }, [articlePageCacheKey, data.aiConfigured, data.aiMessages, data.aiModel, data.aiProvider]);

    const finishPendingAutoSave = (preserveExitTips = false) => {
        pendingMessagesRef.current = Math.max(0, pendingMessagesRef.current - 1);
        if (pendingMessagesRef.current === 0 && !preserveExitTips) {
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

    const finishFailedManualSave = () => {
        setState((previousState) => ({
            ...previousState,
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
        const url = getArticleRouteUrl();
        const sourceCacheKey = getLocalCacheKey(url);
        let nextArticle: ArticleEntry;
        if (create) {
            logIdRef.current = responseArticle.logId;
            url.searchParams.set("id", responseArticle.logId);
            nextArticle = { ...baseArticle, ...responseArticle };
            if (!autoSave) {
                removeLocalArticleCache();
            }
            migrateUiStateToArticle(responseArticle.logId);
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
        const cacheKey = getLocalCacheKey(url);
        const aiMessages = create
            ? migrateArticleAiMessageScope(sourceCacheKey, cacheKey, response.data.aiMessages)
            : readArticleAiMessages(cacheKey, response.data.aiMessages);
        updateCache?.(
            {
                ...response.data,
                aiMessages,
            },
            cacheKey
        );
        return nextArticle;
    };

    let resetAutoSaveQueue = () => undefined;

    const onSubmit = async (
        article: ArticleEntry,
        release: boolean,
        preview: boolean,
        autoSave: boolean,
        acquiredCreateRelease?: DraftArticleOperationRelease
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
            acquiredCreateRelease?.();
            messageApi.error({ content: getRes().articleEdit.requireTitle });
            return false;
        }
        if (article.typeId === undefined || article.typeId === null || article.typeId <= 0) {
            acquiredCreateRelease?.();
            messageApi.error(getRes().articleEdit.requireType);
            return false;
        }
        if (isOffline()) {
            acquiredCreateRelease?.();
            if (autoSave) {
                autoSaveOutcomeRef.current = { type: "deferred" };
                persistToCache(newArticle);
                return false;
            }
            if (release) {
                messageApi.error(getRes().articleEdit.publishReview.offline);
                return false;
            }
            persistToCache(newArticle);
            return true;
        }
        const create = article.logId === undefined || article.logId === null || article.logId <= 0;
        const releaseCreate = acquiredCreateRelease || draftAiSaveGate.tryBeginCreate(article.logId);
        if (!releaseCreate) {
            if (autoSave) {
                autoSaveOutcomeRef.current = { type: "aiPending" };
            } else {
                void messageApi.error(getRes().articleEdit.aiRequestPending);
            }
            return false;
        }
        if (!autoSave) {
            resetAutoSaveQueue();
        }
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
            newArticle = await renderMissingMarkdownContent(newArticle, markdownToHtml);
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
                } else {
                    latestAutoSaveTaskRef.current = undefined;
                }
                return true;
            }
            return false;
        } finally {
            if (autoSave) {
                finishAutoSave(saveSucceeded ? newArticle : undefined, create);
            } else if (saveSucceeded) {
                if (release) {
                    updateReleaseState(newArticle, create);
                } else {
                    updateRubbishState(newArticle, create);
                }
            } else {
                finishFailedManualSave();
            }
            releaseCreate();
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
                    const nextArticle = {
                        ...task.article,
                        logId: logIdRef.current,
                    };
                    const releaseCreate = draftAiSaveGate.tryBeginCreate(nextArticle.logId);
                    if (!releaseCreate) {
                        finishPendingAutoSave(true);
                        return;
                    }
                    if (!markDraftSyncingRef.current(task)) {
                        releaseCreate();
                        finishPendingAutoSave();
                        return;
                    }
                    try {
                        const saved = await onSubmit(nextArticle, false, false, true, releaseCreate);
                        if (saved) {
                            markDraftSyncedRef.current(task, autoSaveAcknowledgedArticleRef.current);
                            if (latestAutoSaveTaskRef.current?.revision === task.revision) {
                                latestAutoSaveTaskRef.current = undefined;
                            }
                            return;
                        }
                        const outcome = autoSaveOutcomeRef.current;
                        if (outcome?.type === "aiPending") {
                            return;
                        } else if (outcome?.type === "deferred") {
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

    useEffect(() => {
        const previousCount = previousDraftAiPendingCountRef.current;
        previousDraftAiPendingCountRef.current = draftAiPendingCount;
        if (previousCount <= 0 || draftAiPendingCount !== 0 || logIdRef.current > 0) {
            return;
        }
        const latestTask = latestAutoSaveTaskRef.current;
        if (latestTask) {
            subjectRef.current?.next(latestTask);
        }
    }, [draftAiPendingCount]);

    const isSyncable = (article: ArticleEntry) =>
        Boolean(article.title) && article.typeId !== undefined && article.typeId !== null && article.typeId > 0;

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
        onRequestSync: (task) => {
            latestAutoSaveTaskRef.current = task;
            if (!importedDraftCreatePendingRef.current) {
                subjectRef.current?.next(task);
            }
        },
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
            return undefined;
        }
        setState((previousState) => ({
            ...previousState,
            article: change.article,
            contentSource: getLocalContentSource(change.article),
            contentSourceUpdatedAt: change.updatedAt,
        }));
        return change;
    };

    const applyImportedArticle = (changeableValue: ArticleChangeableValue) => {
        const saving = savingRef.current;
        if (
            importedDraftCreatePendingRef.current ||
            pendingMessagesRef.current > 0 ||
            saving.rubbishSaving ||
            saving.releaseSaving ||
            saving.previewIng
        ) {
            return false;
        }
        return Boolean(handleValuesChange(changeableValue));
    };

    const createImportedDraft = async (article: ArticleEntry): Promise<boolean> => {
        const res = getRes().articleEdit.markdownImport;
        if (
            offline ||
            importedDraftCreatePendingRef.current ||
            pendingMessagesRef.current > 0 ||
            state.saving.rubbishSaving ||
            state.saving.releaseSaving ||
            state.saving.previewIng
        ) {
            void messageApi.warning(offline ? res.offlineCreateUnavailable : res.waitForCurrentSave);
            return false;
        }
        if (!article.title || !article.typeId || article.typeId <= 0) {
            void messageApi.error(
                !article.title ? getRes().articleEdit.requireTitle : getRes().articleEdit.requireType
            );
            return false;
        }

        const releaseCreate = draftAiSaveGate.tryBeginCreate(0);
        if (!releaseCreate) {
            void messageApi.warning(
                draftAiSaveGate.getPendingAiCount() > 0 ? getRes().articleEdit.aiRequestPending : res.waitForCurrentSave
            );
            return false;
        }
        let succeeded = false;
        try {
            importedDraftCreatePendingRef.current = true;
            subRef.current?.unsubscribe();
            subjectRef.current = null;
            const { data: response } = await axiosInstance.post<ApiResponse<ArticleEditInfo>>(
                createUri,
                {
                    title: article.title,
                    alias: article.alias,
                    digest: article.digest,
                    keywords: article.keywords,
                    markdown: article.markdown,
                    content: article.content,
                    typeId: article.typeId,
                    thumbnail: article.thumbnail,
                    canComment: article.canComment !== false,
                    privacy: false,
                    recommended: false,
                    rubbish: true,
                    editorType: "markdown",
                    transparentPublish: false,
                    preserveDraftAiMessages: true,
                },
                { showError: false } as any
            );
            const logId = response.data?.article?.logId;
            if (response.error || !logId || logId <= 0) {
                void messageApi.error(response.message || res.createFailed);
                return false;
            }

            const url = new URL(window.location.href);
            url.searchParams.set("id", String(logId));
            updateCache?.(response.data, getLocalCacheKey(url));
            latestAutoSaveTaskRef.current = undefined;
            disableExitTips();
            navigate(location.pathname + url.search, { replace: false });
            succeeded = true;
            return true;
        } catch (_error) {
            void messageApi.error(res.createResultUnknown);
            return false;
        } finally {
            try {
                if (!succeeded) {
                    importedDraftCreatePendingRef.current = false;
                    resetAutoSaveQueue();
                    const queuedTask = latestAutoSaveTaskRef.current;
                    const retrySubject = subjectRef.current as Subject<ArticleDraftSyncTask> | null;
                    if (queuedTask && retrySubject) {
                        retrySubject.next(queuedTask);
                    }
                }
            } finally {
                releaseCreate();
            }
        }
    };

    const applyGeneratedCover = async (cover?: {
        dataUrl: string;
        extension?: string;
        messageId?: string;
    }): Promise<string | undefined> => {
        if (!cover?.dataUrl) {
            return undefined;
        }
        const articleId = state.article.logId || 0;
        const releaseRequest = draftAiSaveGate.tryBeginAiRequest(articleId);
        if (!releaseRequest) {
            void messageApi.warning(getRes().articleEdit.assistant.saveInProgress);
            return undefined;
        }
        try {
            const { data } = await axiosInstance.post(`/api/admin/article/cover/apply?id=${articleId}`, {
                dataUrl: cover.dataUrl,
                extension: cover.extension,
                messageId: cover.messageId,
            });
            if (data.error) {
                await messageApi.error(data.message);
                return undefined;
            }
            handleValuesChange({ thumbnail: data.data.url });
            await messageApi.success(getRes().articleEdit.coverApplySuccess);
            return data.data.url;
        } catch (error) {
            await messageApi.error(error instanceof Error ? error.message : getRes().error.unknown);
            return undefined;
        } finally {
            releaseRequest();
        }
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
        applyGeneratedCover,
        applyImportedArticle,
        getLocalCacheKey,
        createImportedDraft,
        handleValuesChange,
        isSaving: state.saving.rubbishSaving || state.saving.releaseSaving || state.saving.previewIng,
        keepServerConflictContent,
        onRollback,
        onSubmit,
        restoreInputRevision,
        state,
        updateAiMessageCache,
        useLocalConflictContent,
    };
};

export default useArticleSaveCoordinator;
