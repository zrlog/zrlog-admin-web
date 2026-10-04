import { hasAction } from "../../utils/account-access";
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
    getArticleDraftBase,
    getArticleDraftSyncState,
    removeArticleCache,
    removeLocalArticleCache,
    restoreLocalArticleCache,
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
import { AIContent } from "@zrlog/editor/dist/ai/AIContentItem";
import { renderMissingMarkdownContent } from "./article-save-content";
import { markdownToHtml } from "@zrlog/editor/dist/editor/utils/marked-utils";
import { DraftAiSaveGate, DraftArticleOperationRelease } from "./draft-ai-save-gate";
import { ArticleUpdatedEvent } from "./article-ai-assistant/article-ai-assistant.types";

import useArticleVersionSync from "./version-sync/use-article-version-sync";

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
          type: "blocked";
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
    const initialStateRef = useRef<ArticleEditState>();
    if (!initialStateRef.current) {
        restoreLocalArticleCache(new URLSearchParams(location.search).get("localDraft"));
        initialStateRef.current = articleDataToState(data, preferredTypeId);
    }
    const defaultState = initialStateRef.current;
    const [state, setState] = useState<ArticleEditState>(defaultState);
    const [restoreInputRevision, setRestoreInputRevision] = useState(0);
    const [articleUpdate, setArticleUpdate] = useState<ArticleUpdatedEvent>();
    // A notification is a refresh target; versionRef advances only after accepting server data.
    const latestArticleUpdateRef = useRef<ArticleUpdatedEvent>();
    const createdArticleRef = useRef<number>();
    const articleRefreshContextRef = useRef({ axiosInstance, data, updateCache, messageApi });
    articleRefreshContextRef.current = { axiosInstance, data, updateCache, messageApi };
    const savingRef = useRef(state.saving);
    savingRef.current = state.saving;
    const contentSourceRef = useRef(state.contentSource);
    contentSourceRef.current = state.contentSource;
    const loadedArticleRef = useRef<ArticleEntry>(defaultState.article);
    const acknowledgedArticleRef = useRef<ArticleEntry>(
        getArticleDraftBase(defaultState.contentConflict?.localArticle || defaultState.article) || data.article
    );
    const versionRef = useRef(defaultState.article.version);
    const logIdRef = useRef(defaultState.article.logId || -1);
    const articleWriteRef = useRef<Promise<void>>();
    const localEditRevisionRef = useRef(0);
    const conflictRef = useRef(state.contentConflict);
    conflictRef.current = state.contentConflict;
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
    const markDraftBlockedRef = useRef<(task: ArticleDraftSyncTask, error: unknown) => boolean>(() => false);
    const markDraftCommittedRef = useRef<() => void>(() => undefined);
    const autoSaveOutcomeRef = useRef<ArticleAutoSaveOutcome>();
    const previousDraftAiPendingCountRef = useRef(draftAiPendingCount);

    const articlePageCacheKey = useMemo(
        () => getPageDataCacheKeyByPath(location.pathname, location.search),
        [location.pathname, location.search]
    );

    const getLocalCacheKey = (url: URL) => getPageDataCacheKeyByPath(url.pathname, "?" + url.searchParams.toString());

    const getArticleRouteUrl = () => new URL(`${location.pathname}${location.search}`, window.location.origin);

    const replaceCreatedArticleRoute = (url: URL) =>
        navigate(location.pathname + url.search, {
            replace: true,
            state: { articleCreatedFrom: location.key },
        });

    const getLocalContentSource = (article: ArticleEntry): ArticleEditState["contentSource"] =>
        article.logId && article.logId > 0 ? "localEdit" : "localDraft";

    const runArticleWrite = async <T,>(write: () => Promise<T>): Promise<T> => {
        const previous = articleWriteRef.current;
        let release!: () => void;
        const current = new Promise<void>((resolve) => {
            release = resolve;
        });
        articleWriteRef.current = current;
        try {
            // Unsubscribing the autosave queue does not cancel an HTTP request already sent.
            // Read the acknowledged version only after that request has settled.
            if (previous) await previous;
            return await write();
        } finally {
            if (articleWriteRef.current === current) articleWriteRef.current = undefined;
            release();
        }
    };

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
        if (data.article.logId === logIdRef.current && data.article.version < versionRef.current) return;
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
            acknowledgedArticleRef.current =
                getArticleDraftBase(newState.contentConflict?.localArticle || newState.article) || data.article;
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

    const updateRubbishState = (newArticle: ArticleEntry, create: boolean, preserveLocalEdits = false) => {
        setState((previousState) => ({
            ...previousState,
            rubbish: true,
            article: preserveLocalEdits
                ? { ...mergeArticleSynchronizationMetadata(previousState.article, newArticle, create), rubbish: true }
                : mergeArticleResponse(previousState.article, newArticle, create),
            saving: {
                ...previousState.saving,
                rubbishSaving: false,
                previewIng: false,
                autoSaving: false,
            },
        }));
    };

    const updateReleaseState = (newArticle: ArticleEntry, create: boolean, preserveLocalEdits = false) => {
        setState((previousState) => ({
            ...previousState,
            rubbish: false,
            article: preserveLocalEdits
                ? { ...mergeArticleSynchronizationMetadata(previousState.article, newArticle, create), rubbish: false }
                : mergeArticleResponse(previousState.article, newArticle, create),
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
            rubbish: savedArticle ? savedArticle.rubbish : previousState.rubbish,
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
        articleSaveToCache(newArticle, updatedAt, undefined, acknowledgedArticleRef.current);
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
        showMessage = true,
        preserveLocalEdits = false
    ) => {
        if (response.documentTitle) {
            updateDocumentTitle(response.documentTitle);
        }
        if (pendingMessagesRef.current === 0 && !preserveLocalEdits) {
            disableExitTips();
        }
        if (!autoSave && showMessage) {
            messageApi.info(response.message);
        }
        const responseArticle = response.data.article;
        acknowledgedArticleRef.current = responseArticle;
        versionRef.current = responseArticle.version;
        logIdRef.current = responseArticle.logId;
        const url = getArticleRouteUrl();
        const sourceCacheKey = getLocalCacheKey(url);
        // A save queued before creation still holds the draft route in its closure.
        // Write its response to the persisted article cache after the create has completed.
        if (create || Number(url.searchParams.get("id")) <= 0) {
            url.searchParams.set("id", responseArticle.logId);
        }
        let nextArticle: ArticleEntry;
        if (create) {
            nextArticle = { ...baseArticle, ...responseArticle };
            if (!autoSave && !preserveLocalEdits) {
                removeLocalArticleCache();
            }
            migrateUiStateToArticle(responseArticle.logId);
        } else {
            nextArticle = {
                ...baseArticle,
                ...responseArticle,
                thumbnail: responseArticle.thumbnail,
                lastUpdateDate: responseArticle.lastUpdateDate,
                version: responseArticle.version,
            };
            if (!autoSave && !preserveLocalEdits) {
                removeArticleCache(nextArticle);
            }
        }
        if (!autoSave && !preserveLocalEdits) {
            markDraftCommittedRef.current();
            setState((previousState) => ({
                ...previousState,
                contentSource: "server",
                contentSourceUpdatedAt: undefined,
            }));
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
        if (create) replaceCreatedArticleRoute(url);
        return nextArticle;
    };

    let resetAutoSaveQueue = () => undefined;

    const submitArticle = async (
        submittedRevision: number,
        article: ArticleEntry,
        release: boolean,
        preview: boolean,
        autoSave: boolean,
        acquiredCreateRelease?: DraftArticleOperationRelease
    ): Promise<boolean> => {
        if (conflictRef.current) {
            acquiredCreateRelease?.();
            return false;
        }
        // A preceding save may have turned this local draft into a persisted article.
        if (!article.logId || article.logId <= 0) {
            article = { ...article, logId: logIdRef.current > 0 ? logIdRef.current : article.logId };
        }
        if (!hasAction("article.publish") && (release || (article.logId && !article.rubbish))) {
            acquiredCreateRelease?.();
            return false;
        }
        if (autoSave) {
            autoSaveOutcomeRef.current = undefined;
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
        let preserveLocalEdits = false;
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
                    if (response.error === ARTICLE_UPDATE_EXPIRED_ERROR) {
                        if (newArticle.transparentPublish)
                            updatePublishStatus((previous) => ({ ...previous, open: false, visible: false }));
                        await loadServerArticleForConflict(newArticle);
                        return false;
                    }
                    if (autoSave) {
                        autoSaveOutcomeRef.current = {
                            type: "blocked",
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
                acknowledgedArticleRef.current = responseData.data.article;
                preserveLocalEdits = !autoSave && localEditRevisionRef.current !== submittedRevision;
                if (autoSave || preserveLocalEdits) {
                    // Acknowledge this revision before publishing the response to the page cache.
                    // Otherwise a rerender can compare the new server version with the old local draft
                    // and manufacture a conflict. Newer input stays dirty on the acknowledged version.
                    markDraftSyncedRef.current({ article, revision: submittedRevision }, responseData.data.article);
                }
                newArticle = handleArticleResponse(
                    responseData,
                    newArticle,
                    create,
                    autoSave,
                    !newArticle.transparentPublish,
                    preserveLocalEdits
                );
                saveSucceeded = true;
                if (!autoSave && !preserveLocalEdits) {
                    latestAutoSaveTaskRef.current = undefined;
                } else if (!autoSave && latestAutoSaveTaskRef.current?.revision === localEditRevisionRef.current) {
                    // The manual save may have reset the debounce queue while waiting for an earlier write.
                    subjectRef.current?.next(latestAutoSaveTaskRef.current);
                }
                return true;
            }
            return false;
        } finally {
            if (autoSave) {
                finishAutoSave(saveSucceeded ? newArticle : undefined, create);
            } else if (saveSucceeded) {
                if (release) {
                    updateReleaseState(newArticle, create, preserveLocalEdits);
                } else {
                    updateRubbishState(newArticle, create, preserveLocalEdits);
                }
            } else {
                finishFailedManualSave();
            }
            releaseCreate();
        }
    };

    const onSubmit = (
        article: ArticleEntry,
        release: boolean,
        preview: boolean,
        autoSave: boolean,
        acquiredCreateRelease?: DraftArticleOperationRelease
    ) => {
        const submittedRevision = localEditRevisionRef.current;
        return runArticleWrite(() =>
            submitArticle(submittedRevision, article, release, preview, autoSave, acquiredCreateRelease)
        );
    };

    const loadServerArticleForConflict = async (submittedArticle: ArticleEntry) => {
        const logId = submittedArticle.logId || logIdRef.current;
        if (!logId || logId <= 0) {
            return;
        }
        if (conflictRef.current?.loading) return;
        const existingConflict = conflictRef.current;
        resetAutoSaveQueue();
        const local = draftSync.pauseForConflict(submittedArticle);
        const conflict: NonNullable<ArticleEditState["contentConflict"]> = {
            source: "localEdit",
            localArticle: {
                ...local.article,
                logId,
                version: submittedArticle.version,
                rubbish: submittedArticle.rubbish,
            },
            localVersion: submittedArticle.version,
            localUpdatedAt: local.updatedAt,
            serverVersion: submittedArticle.version,
            baseArticle:
                existingConflict?.baseArticle ||
                getArticleDraftBase(submittedArticle) ||
                (acknowledgedArticleRef.current.version === submittedArticle.version
                    ? acknowledgedArticleRef.current
                    : undefined),
            loading: true,
        };
        conflictRef.current = conflict;
        setState((previous) => ({ ...previous, contentConflict: conflict }));
        try {
            const { data: response } = await axiosInstance.get<ApiResponse<ArticleEditInfo>>(
                "/api/admin/article-edit",
                {
                    params: { id: logId },
                    showError: false,
                } as any
            );
            if (response.error || !response.data?.article) {
                throw new Error(response.message);
            }
            const serverArticle = response.data.article;
            if (serverArticle.logId !== logId) throw new Error("Unexpected article identity");
            if (logIdRef.current !== logId || conflictRef.current !== conflict) return;
            if (
                !Number.isSafeInteger(serverArticle.version) ||
                serverArticle.version < Math.max(versionRef.current, submittedArticle.version)
            )
                throw new Error("Stale article version");
            versionRef.current = serverArticle.version;
            loadedArticleRef.current = serverArticle;
            conflictRef.current = { ...conflict, serverVersion: serverArticle.version, loading: false };
            setState((previousState) => ({
                ...previousState,
                article: serverArticle,
                rubbish: serverArticle.rubbish === true,
                editorVersion: serverArticle.version,
                contentConflict: conflictRef.current,
            }));
            setRestoreInputRevision((revision) => revision + 1);
        } catch (error) {
            if (logIdRef.current !== logId || conflictRef.current !== conflict) return;
            conflictRef.current = { ...conflict, loading: false, loadError: true };
            setState((previous) => ({ ...previous, contentConflict: conflictRef.current }));
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
                        const saved = await onSubmit(nextArticle, !nextArticle.rubbish, false, true, releaseCreate);
                        if (saved) {
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
        article: state.contentConflict?.localArticle || state.article,
        initialDirty: defaultState.contentSource !== "server" || Boolean(defaultState.contentConflict),
        initialConflict: Boolean(defaultState.contentConflict),
        initialState: getArticleDraftSyncState(defaultState.article),
        initialUpdatedAt: defaultState.contentSourceUpdatedAt,
        offline,
        isSyncable,
        onPersist: (article, updatedAt, syncState) =>
            articleSaveToCache(article, updatedAt, syncState, acknowledgedArticleRef.current),
        onRemove: removeArticleCache,
        onRequestSync: (task) => {
            localEditRevisionRef.current = task.revision;
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
    markDraftBlockedRef.current = draftSync.markBlocked;
    markDraftCommittedRef.current = draftSync.markCommitted;

    const onArticleUpdated = useCallback((event: ArticleUpdatedEvent) => {
        if (
            !Number.isSafeInteger(event.articleId) ||
            event.articleId <= 0 ||
            !Number.isSafeInteger(event.version) ||
            event.version < 0
        )
            return;
        if (event.created && logIdRef.current <= 0 && event.articleId > 0) {
            // Reserve the saved identity before any deferred draft autosave can create a duplicate.
            createdArticleRef.current = event.articleId;
            logIdRef.current = event.articleId;
            versionRef.current = -1;
            setState((previous) => ({ ...previous, article: { ...previous.article, logId: event.articleId } }));
        }
        if (event.articleId !== logIdRef.current || event.version <= versionRef.current) return;
        const latest = latestArticleUpdateRef.current;
        if (latest?.articleId === event.articleId && latest.version > event.version) return;
        latestArticleUpdateRef.current = event;
        setArticleUpdate((previous) =>
            previous?.articleId === event.articleId && previous.version >= event.version ? previous : event
        );
    }, []);

    const receiveServerArticle = draftSync.receiveServerArticle;
    const saving = Object.values(state.saving).some(Boolean);
    useEffect(() => {
        if (!articleUpdate || saving || articleUpdate.articleId !== state.article.logId) return;
        if (articleUpdate.version <= versionRef.current) {
            setArticleUpdate((current) => (current === articleUpdate ? undefined : current));
            return;
        }
        let cancelled = false;
        const refresh = async () => {
            try {
                const { data: response } = await articleRefreshContextRef.current.axiosInstance.get<
                    ApiResponse<ArticleEditInfo>
                >("/api/admin/article-edit", { params: { id: articleUpdate.articleId } });
                // A write can start after this read, before React runs its effect cleanup.
                if (articleWriteRef.current) await articleWriteRef.current;
                if (cancelled || articleUpdate.articleId !== logIdRef.current) return;
                if (response.error) {
                    void articleRefreshContextRef.current.messageApi.error(response.message || getRes().error.unknown);
                    return;
                }
                const serverArticle = response.data?.article;
                const latest = latestArticleUpdateRef.current;
                if (
                    !serverArticle ||
                    serverArticle.logId !== articleUpdate.articleId ||
                    !Number.isSafeInteger(serverArticle.version) ||
                    serverArticle.version < articleUpdate.version ||
                    serverArticle.version <= versionRef.current ||
                    (latest?.articleId === serverArticle.logId && serverArticle.version < latest.version)
                )
                    return;
                const local = receiveServerArticle(serverArticle);
                const previousConflict = conflictRef.current;
                const nextConflict: ArticleEditState["contentConflict"] = local
                    ? {
                          source: "localEdit",
                          localArticle: previousConflict?.localArticle || local.article,
                          localVersion: previousConflict?.localVersion ?? local.article.version,
                          localUpdatedAt: previousConflict?.localUpdatedAt ?? local.updatedAt,
                          serverVersion: serverArticle.version,
                          baseArticle: previousConflict?.baseArticle || getArticleDraftBase(local.article),
                      }
                    : undefined;
                // Queued writes must see the decision before React renders the refreshed article.
                conflictRef.current = nextConflict;
                if (!local) acknowledgedArticleRef.current = serverArticle;
                latestAutoSaveTaskRef.current = undefined;
                versionRef.current = serverArticle.version;
                loadedArticleRef.current = serverArticle;
                const currentContext = articleRefreshContextRef.current;
                const created = createdArticleRef.current === serverArticle.logId;
                const url = getArticleRouteUrl();
                if (created) url.searchParams.set("id", String(serverArticle.logId));
                const cacheKey = created ? getLocalCacheKey(url) : articlePageCacheKey;
                const aiMessages = created
                    ? migrateArticleAiMessageScope(articlePageCacheKey, cacheKey, response.data.aiMessages)
                    : readArticleAiMessages(articlePageCacheKey, currentContext.data.aiMessages);
                currentContext.updateCache?.({ ...response.data, aiMessages }, cacheKey);
                if (created) {
                    createdArticleRef.current = undefined;
                    removeLocalArticleCache();
                    migrateUiStateToArticle(serverArticle.logId!);
                    replaceCreatedArticleRoute(url);
                }
                setState((previous) => ({
                    ...previous,
                    article: serverArticle,
                    rubbish: serverArticle.rubbish === true,
                    editorVersion: serverArticle.version,
                    aiMessages,
                    contentSource: local ? "localEdit" : "server",
                    contentSourceUpdatedAt: local?.updatedAt,
                    contentConflict: nextConflict,
                }));
                setRestoreInputRevision((revision) => revision + 1);
            } catch (error) {
                // The normal request handler reports read failures; the tool write has already succeeded.
                console.error(error);
            } finally {
                if (!cancelled) setArticleUpdate((current) => (current === articleUpdate ? undefined : current));
            }
        };
        void refresh();
        // A page switch, newer event or in-flight save invalidates this read. Retry after saving settles.
        return () => {
            cancelled = true;
        };
    }, [articleUpdate, saving, state.article.logId, articlePageCacheKey, receiveServerArticle]);

    const handleValuesChange = (changeableValue: ArticleChangeableValue) => {
        const change = draftSync.applyPatch(changeableValue);
        if (!change) {
            return undefined;
        }
        localEditRevisionRef.current = change.revision;
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
            // Upload only. The skill card revalidates its context before applying the returned URL.
            return data.data.url;
        } catch (error) {
            await messageApi.error(error instanceof Error ? error.message : getRes().error.unknown);
            return undefined;
        } finally {
            releaseRequest();
        }
    };

    const onRollback = async (targetVersion: number) =>
        runArticleWrite(async () => {
            if (logIdRef.current <= 0 || conflictRef.current) {
                return;
            }
            resetAutoSaveQueue();
            setState((previous) => ({ ...previous, saving: { ...previous.saving, rubbishSaving: true } }));
            try {
                const { data: response } = await axiosInstance.post("/api/admin/article-version/rollback", {
                    logId: logIdRef.current,
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
            } finally {
                finishFailedManualSave();
            }
        });

    const saveMergedConflict = async (article: ArticleEntry): Promise<boolean> => {
        const conflict = conflictRef.current;
        if (
            !conflict ||
            conflict.loading ||
            conflict.loadError ||
            article.logId !== logIdRef.current ||
            article.version !== conflict.serverVersion
        )
            return false;
        resetAutoSaveQueue();
        acknowledgedArticleRef.current = state.article;
        const change = draftSync.resolveConflict(article, false);
        if (!change) return false;
        versionRef.current = conflict.serverVersion;
        localEditRevisionRef.current = change.revision;
        conflictRef.current = undefined;
        enableExitTips(getRes().articleEdit.editExitWithoutSave);
        setState((previousState) => ({
            ...previousState,
            article,
            rubbish: article.rubbish === true,
            editorVersion: article.version,
            contentSource: conflict.source,
            contentSourceUpdatedAt: change.updatedAt,
            contentConflict: undefined,
        }));
        setRestoreInputRevision((revision) => revision + 1);
        return onSubmit(article, !article.rubbish, false, false);
    };

    const versionSync = useArticleVersionSync({
        conflict: state.contentConflict,
        serverArticle: state.article,
        axiosInstance,
        offline,
        onResolve: saveMergedConflict,
    });

    const retryConflictRead = () => {
        if (conflictRef.current) void loadServerArticleForConflict(conflictRef.current.localArticle);
    };

    return {
        getCurrentArticle: draftSync.getCurrentArticle,
        applyGeneratedCover,
        applyImportedArticle,
        getLocalCacheKey,
        createImportedDraft,
        handleValuesChange,
        isSaving: state.saving.rubbishSaving || state.saving.releaseSaving || state.saving.previewIng,
        retryConflictRead,
        saveMergedConflict,
        versionSync,
        onRollback,
        onSubmit,
        onArticleUpdated,
        restoreInputRevision,
        state,
        updateAiMessageCache,
    };
};

export default useArticleSaveCoordinator;
