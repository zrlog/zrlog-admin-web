import { FunctionComponent, SetStateAction, useCallback, useEffect, useMemo, useRef, useState } from "react";
import { Alert, App, Button, Grid, InputRef, message, Space, Tag } from "antd";
import Divider from "antd/es/divider";
import Card from "antd/es/card";
import {
    createUri,
    getLabelValueSeparator,
    getRealRouteUrl,
    getRes,
    tryAppendBackendServerUrl,
    updateUri,
} from "../../utils/constants";
import { isOffline } from "../../utils/env-utils";
import { useAxiosBaseInstance } from "../../base/AppBase";
import {
    ArticleChangeableValue,
    ArticleEditInfo,
    ArticleEditProps,
    ArticleEditState,
    ArticleEntry,
    PublishCheckTarget,
    PublishStatusPopoverState,
} from "./index.types";
import ArticleEditActionBar from "./article-edit-action-bar";
import ArticleEditHeader from "./article-edit-header";
import useArticleFieldAi from "./use-article-field-ai";
import {
    articleDataToState,
    articleSaveToCache,
    removeArticleCache,
    removeLocalArticleCache,
} from "../../utils/article-cache";
import { auditTime, concatMap, Subject, tap } from "rxjs";
import { Subscription } from "rxjs/internal/Subscription";
import {
    deepEqualWithSpecialJSON,
    disableExitTips,
    enableExitTips,
    getEditorUser,
    updateDocumentTitle,
} from "../../utils/helpers";
import { useLocation } from "react-router";
import { addToCache, getCacheByKey, getPageDataCacheKeyByPath } from "../../utils/cache";
import { getAppState } from "../../base/ConfigProviderApp";
import Editor, { insertTextAtCursor } from "@editor/dist/editor";
import EditorStatusBar from "@editor/dist/editor/editor-statistics-info";
import { toStatisticsByMarkdown } from "@editor/dist/editor/utils/editor-utils";
import { EditorView } from "@uiw/react-codemirror";
import { Locale } from "@editor/dist/editor/lang/editor-lang";
import { AIContent } from "@editor/dist/ai/AIContentItem";
import { AIStateCache } from "@editor/dist/ai/AIStateCache";
import { useNavigate } from "react-router-dom";
import { getStaticProgressText, postRefreshCacheSse } from "../../utils/sse-utils";
import { useTheme } from "antd-style";
import PublishStatusBar from "./publish-status-bar";
import { useArticleAiAssistantConfig } from "./article-ai-assistant/article-ai-assistant-button";
import TimeAgo from "@editor/dist/editor/TimeAgo";

const normalizeConflictValue = (value: unknown) => {
    if (value === undefined || value === null) {
        return "";
    }
    if (typeof value === "string") {
        return value.trim();
    }
    return JSON.stringify(value);
};

const getArticleBodyForConflict = (article: ArticleEntry) => article.markdown || article.content || "";

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

const Index: FunctionComponent<ArticleEditProps> = ({
    offline,
    data,
    onExitFullScreen,
    onFullScreen,
    fullScreen,
    updateCache,
}) => {
    const theme = useTheme();
    const location = useLocation();
    const editCardRef = useRef<HTMLDivElement>(null);
    const editorViewRef = useRef<EditorView | null>(null);
    const preferredTypeId = useMemo(() => {
        const rawTypeId = new URLSearchParams(location.search).get("typeId");
        const typeId = rawTypeId ? Number(rawTypeId) : undefined;
        return typeId && Number.isFinite(typeId) && typeId > 0 ? typeId : undefined;
    }, [location.search]);
    const articleEditUiStateScope = useMemo(() => {
        const rawLogId = new URLSearchParams(location.search).get("id");
        const urlLogId = rawLogId ? Number(rawLogId) : undefined;
        const logId =
            data.article.logId && data.article.logId > 0
                ? data.article.logId
                : urlLogId && Number.isFinite(urlLogId) && urlLogId > 0
                ? urlLogId
                : undefined;
        if (logId) {
            return `article/${logId}`;
        }
        return "draft";
    }, [data.article.logId, location.search]);
    const articleEditUiStateCacheKey = useMemo(
        () => `articleEdit/ui/${articleEditUiStateScope}`,
        [articleEditUiStateScope]
    );
    const getCachedArticleEditUiState = useCallback(
        () => getCacheByKey<ArticleEditUiState>(articleEditUiStateCacheKey) || {},
        [articleEditUiStateCacheKey]
    );
    const persistArticleEditUiState = useCallback(
        (patch: ArticleEditUiState) => {
            addToCache(articleEditUiStateCacheKey, {
                ...getCachedArticleEditUiState(),
                ...patch,
            });
        },
        [articleEditUiStateCacheKey, getCachedArticleEditUiState]
    );
    const cachedArticleEditUiState = getCachedArticleEditUiState();

    const defaultState = articleDataToState(data, preferredTypeId);
    const [state, setState] = useState<ArticleEditState>(defaultState);
    const [settingsOpen, setSettingsOpenState] = useState(cachedArticleEditUiState.settingsOpen === true);
    const [versionDrawerOpen, setVersionDrawerOpenState] = useState(
        cachedArticleEditUiState.versionDrawerOpen === true
    );
    const [articleAssistantOpen, setArticleAssistantOpenState] = useState(
        cachedArticleEditUiState.articleAssistantOpen === true
    );
    const [restoreInputRevision, setRestoreInputRevision] = useState(0);
    const [publishStatus, setPublishStatusState] = useState<PublishStatusPopoverState>(() =>
        normalizeCachedPublishStatus(cachedArticleEditUiState.publishStatus)
    );
    const applyCachedArticleEditUiState = useCallback(() => {
        const cachedUiState = getCachedArticleEditUiState();
        setSettingsOpenState(cachedUiState.settingsOpen === true);
        setVersionDrawerOpenState(cachedUiState.versionDrawerOpen === true);
        setArticleAssistantOpenState(cachedUiState.articleAssistantOpen === true);
        setPublishStatusState(normalizeCachedPublishStatus(cachedUiState.publishStatus));
    }, [getCachedArticleEditUiState]);
    const updateSettingsOpen = useCallback(
        (open: boolean) => {
            setSettingsOpenState(open);
            persistArticleEditUiState({ settingsOpen: open });
        },
        [persistArticleEditUiState]
    );
    const updateVersionDrawerOpen = useCallback(
        (open: boolean) => {
            setVersionDrawerOpenState(open);
            persistArticleEditUiState({ versionDrawerOpen: open });
        },
        [persistArticleEditUiState]
    );
    const updateArticleAssistantOpen = useCallback(
        (open: boolean) => {
            setArticleAssistantOpenState(open);
            persistArticleEditUiState({ articleAssistantOpen: open });
        },
        [persistArticleEditUiState]
    );
    const updatePublishStatus = useCallback(
        (action: SetStateAction<PublishStatusPopoverState>) => {
            setPublishStatusState((prevState) => {
                const nextState = typeof action === "function" ? action(prevState) : action;
                persistArticleEditUiState({ publishStatus: nextState });
                return nextState;
            });
        },
        [persistArticleEditUiState]
    );

    useEffect(() => {
        applyCachedArticleEditUiState();
    }, [applyCachedArticleEditUiState]);
    const isNewArticle = !state.article.logId;
    const articleStatusText = isNewArticle
        ? getRes().articleEdit.new
        : state.rubbish
        ? getRes().articleEdit.status.draft
        : state.article.privacy
        ? getRes().articleEdit.status.private
        : getRes().articleEdit.status.published;
    const contentSourceText = (() => {
        if (state.contentSource === "localDraft") {
            return getRes().articleEdit.contentSource.localDraft;
        }
        if (state.contentSource === "localEdit") {
            return getRes().articleEdit.contentSource.localEdit;
        }
        return getRes().articleEdit.contentSource.server;
    })();
    const contentSourceColor = state.contentSource === "localEdit" ? "warning" : undefined;
    const showContentSourceTag = state.contentSource !== "server";
    const statusBarLastUpdateDate =
        state.contentSource !== "server" && state.contentSourceUpdatedAt
            ? state.contentSourceUpdatedAt
            : state.article.lastUpdateDate
            ? state.article.lastUpdateDate
            : 0;

    const titleRef = useRef<InputRef>(null);
    const aliasRef = useRef<InputRef>(null);
    const digestRef = useRef<InputRef>(null);
    const loadedArticleRef = useRef<ArticleEntry>(defaultState.article);
    const versionRef = useRef<number>(defaultState.article.version);
    const previewUrlRef = useRef<string | undefined>(defaultState.article.previewUrl);
    const logIdRef = useRef<number>(defaultState.article.logId ? defaultState.article.logId : -1);

    // 当由于路由跳转导致外部传入的 data 变化时，重新初始化表单状态
    useEffect(() => {
        const newState = articleDataToState(data, preferredTypeId);
        const serverArticle = data.article.logId && data.article.logId > 0;
        if (!serverArticle && state.contentSource === "localDraft") {
            setState((prevState) => ({
                ...prevState,
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
        const articleChanged = !deepEqualWithSpecialJSON(loadedArticleRef.current, newState.article);
        if (articleChanged) {
            loadedArticleRef.current = newState.article;
            setState(newState);
            applyCachedArticleEditUiState();
            setRestoreInputRevision((revision) => revision + 1);
            versionRef.current = newState.article.version;
            previewUrlRef.current = newState.article.previewUrl;
            logIdRef.current = newState.article.logId ? newState.article.logId : -1;
            return;
        }
        setState((prevState) => ({
            ...prevState,
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
    }, [data, preferredTypeId, state.contentSource]);

    const subjectRef = useRef<Subject<ArticleEntry> | null>(null);
    const subRef = useRef<Subscription | null>(null);

    const [messageApi, messageContextHolder] = message.useMessage({
        maxCount: 3,
        getContainer: () => editCardRef.current as HTMLElement,
    });
    const { modal } = App.useApp();
    const axiosInstance = useAxiosBaseInstance(() => editCardRef.current as HTMLElement);
    const navigate = useNavigate();

    const pendingMessagesRef = useRef(0);

    const finishPendingAutoSave = () => {
        pendingMessagesRef.current = Math.max(0, pendingMessagesRef.current - 1);
        if (pendingMessagesRef.current === 0) {
            disableExitTips();
        }
    };

    const updateRubbishState = (newArticle: ArticleEntry, create: boolean) => {
        setState((prevState) => ({
            ...prevState,
            rubbish: true,
            article: doMergeArticle(prevState.article, newArticle, create),
            saving: {
                ...prevState.saving,
                rubbishSaving: false,
                previewIng: false,
                autoSaving: false,
            },
        }));
    };

    const updateReleaseState = (newArticle: ArticleEntry, create: boolean) => {
        setState((prevState) => ({
            ...prevState,
            rubbish: false,
            article: doMergeArticle(prevState.article, newArticle, create),
            saving: {
                ...prevState.saving,
                releaseSaving: false,
                rubbishSaving: false,
                previewIng: false,
                autoSaving: false,
            },
        }));
    };

    const persistToCache = (newArticle: ArticleEntry) => {
        const updatedAt = Date.now();
        articleSaveToCache(newArticle, updatedAt);
        setState((prevState) => {
            return {
                ...prevState,
                article: newArticle,
                contentSource: getLocalContentSource(newArticle),
                contentSourceUpdatedAt: updatedAt,
                saving: {
                    ...prevState.saving,
                    releaseSaving: false,
                    rubbishSaving: false,
                    previewIng: false,
                    autoSaving: false,
                },
            };
        });

        //没有堆积的消息了，才能触发移除强制离开页面的提示
        if (pendingMessagesRef.current === 0) {
            disableExitTips();
        }
    };

    const getLocalCacheKey = (url: URL) => {
        return getPageDataCacheKeyByPath(location.pathname, "?" + url.searchParams.toString());
    };

    const getLocalContentSource = (article: ArticleEntry): ArticleEditState["contentSource"] => {
        return article.logId && article.logId > 0 ? "localEdit" : "localDraft";
    };

    const onSubmit = async (
        article: ArticleEntry,
        release: boolean,
        preview: boolean,
        autoSave: boolean
    ): Promise<boolean> => {
        let newArticle: ArticleEntry = {
            ...article,
            version: versionRef.current,
            rubbish: !release,
            transparentPublish: release && !autoSave && article.privacy !== true,
        };
        if (isOffline()) {
            persistToCache(newArticle);
            return true;
        }
        //do check
        if (isTitleError(article)) {
            messageApi.error({ content: getRes().articleEdit.requireTitle });
            return false;
        }
        if (isTypeError(article)) {
            messageApi.error(getRes().articleEdit.requireType);
            return false;
        }
        //非自动保存的情况下，需要清空当前缓存队列
        if (!autoSave) {
            setSubject();
        }
        let uri;
        const create = article.logId === undefined || article.logId === null || article.logId <= 0;
        if (create) {
            uri = createUri;
        } else {
            uri = updateUri;
        }
        if (release) {
            setState((prevState) => {
                return {
                    ...prevState,
                    saving: {
                        ...prevState.saving,
                        releaseSaving: true,
                        autoSaving: autoSave,
                    },
                };
            });
        } else {
            setState((prevState) => {
                return {
                    ...prevState,
                    saving: {
                        ...prevState.saving,
                        rubbishSaving: true,
                        previewIng: preview,
                        autoSaving: autoSave,
                    },
                };
            });
        }

        enableExitTips(getRes().articleEdit.editExitWithoutSave);
        try {
            let responseData;
            try {
                const data = newArticle.transparentPublish
                    ? await postArticleWithTransparentPublish(uri, newArticle)
                    : (await axiosInstance.post(uri, newArticle)).data;
                responseData = data;
                if (data.error) {
                    modal.error({
                        title: getRes().articleEdit.saveFailed,
                        content: data.message,
                        getContainer: () => editCardRef.current as HTMLElement,
                    });
                    return false;
                }
                if (data.data) {
                    versionRef.current = data.data.article.version;
                }
            } catch (e) {
                if (newArticle.transparentPublish) {
                    updatePublishStatus((prevState) => ({
                        ...prevState,
                        open: true,
                        visible: true,
                        updatedAt: Date.now(),
                        publishError: e instanceof Error ? e.message : getRes().articleEdit.saveFailed,
                    }));
                    return false;
                }
                throw e;
            }
            const data = responseData;
            if (data.error === 0) {
                newArticle = handleArticleResponse(data, newArticle, create, autoSave, !newArticle.transparentPublish);
                return true;
            }
            return false;
        } finally {
            // 根据 release 的值调用对应的状态更新回调函数
            release ? updateReleaseState(newArticle, create) : updateRubbishState(newArticle, create);
        }
    };

    const onPreview = async () => {
        const ok = await onSubmit(state.article, false, true, false);
        if (ok && previewUrlRef.current) {
            window.open(previewUrlRef.current, "_blank", "noopener,noreferrer");
        }
    };

    const postArticleWithTransparentPublish = async (uri: string, article: ArticleEntry) => {
        updatePublishStatus({
            open: true,
            visible: true,
            updatedAt: Date.now(),
            publishText: getRes().staticSite.publishStart,
            publishError: undefined,
            checkStatus: "idle",
        });
        let refreshResponse: any;
        let articleResponse: any;
        await postRefreshCacheSse(uri, {
            body: article,
            messageApi,
            messageKey: "transparentPublish",
            responseEvents: ["article"],
            backgroundTaskTitle: getRes().backgroundTask.title + " · " + getRes().articleEdit.actions.release,
            showErrorMessage: false,
            onResponse: (data) => {
                refreshResponse = data;
            },
            onEvent: (event) => {
                if (event.event === "article") {
                    articleResponse = event.data;
                }
                if (event.event === "publish-start") {
                    updatePublishStatus((prevState) => ({
                        ...prevState,
                        visible: true,
                        updatedAt: Date.now(),
                        publishText: event.data?.message || getRes().staticSite.publishStart,
                    }));
                }
                if (event.event === "static-sync-start" || event.event === "static-progress") {
                    updatePublishStatus((prevState) => ({
                        ...prevState,
                        visible: true,
                        updatedAt: Date.now(),
                        staticText: getStaticProgressText(event.data),
                    }));
                }
                if (event.event === "static-sync-complete") {
                    updatePublishStatus((prevState) => ({
                        ...prevState,
                        visible: true,
                        updatedAt: Date.now(),
                        staticText: undefined,
                    }));
                }
                if (event.event === "publish-complete") {
                    updatePublishStatus((prevState) => ({
                        ...prevState,
                        visible: true,
                        updatedAt: Date.now(),
                        publishText: event.data?.message || getRes().staticSite.publishComplete,
                    }));
                }
                if (event.event === "publish-check-start") {
                    updatePublishStatus((prevState) => ({
                        ...prevState,
                        visible: true,
                        updatedAt: Date.now(),
                        checkStatus: "running",
                    }));
                }
                if (event.event === "publish-check-complete") {
                    if (event.data?.aiMessages) {
                        updateAiMessageCache([...state.aiMessages, ...event.data.aiMessages]);
                    }
                    updatePublishStatus((prevState) => ({
                        ...prevState,
                        visible: true,
                        updatedAt: Date.now(),
                        checkStatus: "success",
                        checkPayload: event.data?.toolPayload,
                    }));
                }
                if (event.event === "publish-check-error") {
                    updatePublishStatus((prevState) => ({
                        ...prevState,
                        visible: true,
                        updatedAt: Date.now(),
                        checkStatus: "error",
                        checkError: event.data?.message || getRes().articleEdit.publishCheck.failed,
                    }));
                }
                if (event.event === "publish-error") {
                    updatePublishStatus((prevState) => ({
                        ...prevState,
                        open: true,
                        visible: true,
                        updatedAt: Date.now(),
                        publishError: event.data?.message || getRes().articleEdit.saveFailed,
                    }));
                }
            },
        });
        return articleResponse || refreshResponse;
    };

    const updatePublishStatusOpen = (open: boolean) => {
        updatePublishStatus((prevState) => ({
            ...prevState,
            open,
        }));
    };

    const closePublishStatus = () => {
        updatePublishStatus((prevState) => ({
            ...prevState,
            open: false,
        }));
    };

    const focusInputRef = (inputRef: { current: InputRef | null }) => {
        window.setTimeout(() => {
            inputRef.current?.focus();
        }, 160);
    };

    const locatePublishCheckTarget = (target: PublishCheckTarget) => {
        updatePublishStatus((prevState) => ({
            ...prevState,
            open: false,
        }));
        if (target === "markdown") {
            updateSettingsOpen(false);
            editCardRef.current?.scrollIntoView({ behavior: "smooth", block: "center" });
            window.setTimeout(() => {
                editorViewRef.current?.focus();
            }, 160);
            return;
        }
        if (target === "title") {
            updateSettingsOpen(false);
            editCardRef.current?.scrollIntoView({ behavior: "smooth", block: "start" });
            focusInputRef(titleRef);
            return;
        }
        if (target === "alias") {
            updateSettingsOpen(false);
            editCardRef.current?.scrollIntoView({ behavior: "smooth", block: "start" });
            focusInputRef(aliasRef);
            return;
        }
        updateSettingsOpen(true);
        if (target === "digest") {
            focusInputRef(digestRef);
        }
    };

    const handleArticleResponse = (
        data: any,
        baseArticle: ArticleEntry,
        create: boolean,
        autoSave: boolean,
        showMessage: boolean = true
    ) => {
        if (data.documentTitle) {
            updateDocumentTitle(data.documentTitle);
        }
        if (pendingMessagesRef.current === 0) {
            disableExitTips();
        }
        if (!autoSave && showMessage) {
            messageApi.info(data.message);
        }
        const responseArticle = data.data.article;
        previewUrlRef.current = responseArticle.previewUrl;
        const url = new URL(window.location.href);
        let nextArticle: ArticleEntry;
        if (create) {
            logIdRef.current = responseArticle.logId;
            url.searchParams.set("id", responseArticle.logId);
            nextArticle = { ...baseArticle, ...responseArticle };
            removeLocalArticleCache();
            navigate(location.pathname + url.search, { replace: true });
        } else {
            nextArticle = {
                ...baseArticle,
                ...responseArticle,
                thumbnail: responseArticle.thumbnail,
                lastUpdateDate: responseArticle.lastUpdateDate,
                version: responseArticle.version,
            };
            removeArticleCache(nextArticle);
        }
        if (updateCache) {
            updateCache(data.data, getLocalCacheKey(url));
        }
        return nextArticle;
    };

    const onRollback = async (targetVersion: number) => {
        if (!state.article.logId) {
            return;
        }
        const { data } = await axiosInstance.post("/api/admin/article-version/rollback", {
            logId: state.article.logId,
            version: versionRef.current,
            targetVersion,
        });
        if (data.error) {
            modal.confirm({
                title: getRes().articleEdit.rollbackFailed,
                content: (
                    <Space direction="vertical" size={8}>
                        <span>{data.message}</span>
                        <span>{getRes().articleEdit.rollbackConflictTip}</span>
                    </Space>
                ),
                okText: getRes().articleEdit.rollbackRefresh,
                cancelText: getRes().cancel,
                getContainer: () => editCardRef.current as HTMLElement,
                onOk: () => {
                    window.location.reload();
                },
            });
            return;
        }
        const mergedArticle = handleArticleResponse(data, state.article, false, false);
        if (mergedArticle.rubbish) {
            updateRubbishState(mergedArticle, false);
        } else {
            updateReleaseState(mergedArticle, false);
        }
    };

    const doMergeArticle = (
        stateArticle: ArticleEntry,
        updateResponseArticle: ArticleEntry,
        create: boolean
    ): ArticleEntry => {
        const mergeArticle = {
            ...updateResponseArticle,
            logId: updateResponseArticle.logId,
            lastUpdateDate: updateResponseArticle.lastUpdateDate,
            version: updateResponseArticle.version,
            thumbnail:
                stateArticle.thumbnail && stateArticle.thumbnail.trim().length > 0
                    ? stateArticle.thumbnail
                    : updateResponseArticle.thumbnail,
        };
        //处理文章别名
        if (aliasRef.current && aliasRef.current.input) {
            if (aliasRef.current.input.value.trim().length === 0 && create) {
                mergeArticle.alias = updateResponseArticle.alias;
                aliasRef.current.input.value = updateResponseArticle.alias as string;
            } else {
                mergeArticle.alias = aliasRef.current.input.value;
            }
        }
        //处理摘要
        if (digestRef.current && digestRef.current.input) {
            if (digestRef.current.input.value.trim().length === 0 && create) {
                mergeArticle.digest = updateResponseArticle.digest;
                digestRef.current.input.value = updateResponseArticle.digest as string;
            } else {
                //mergeArticle.digest = digestRef.current.input.value;
            }
        }
        return mergeArticle;
    };

    const isSaving = () => {
        return state.saving.rubbishSaving || state.saving.releaseSaving || state.saving.previewIng;
    };

    useEffect(() => {
        setState((prevState) => {
            return {
                ...prevState,
                aiProvider: data.aiProvider,
                aiModel: data.aiModel,
                aiConfigured: data.aiConfigured === true,
                aiMessages: data.aiMessages,
            };
        });
    }, [data.aiConfigured, data.aiModel, data.aiProvider, data.aiMessages]);

    useEffect(() => {
        //如果文章内容没有变更，不更新 state，避免触发更新导致文章状态不对
        if (deepEqualWithSpecialJSON(state.article, data.article)) {
            return;
        }
        if (offline) {
            articleSaveToCache(state.article);
            return;
        } else {
            //覆盖版本信息
            versionRef.current = state.article.version;
            handleValuesChange(state.article);
        }
    }, [offline]);

    const setSubject = () => {
        if (subRef.current) {
            subRef.current.unsubscribe();
        }
        pendingMessagesRef.current = 0;
        const autoSaveInterval = [2, 5, 10].includes(state.articleEditAutoSaveInterval)
            ? state.articleEditAutoSaveInterval
            : 5;
        subjectRef.current = new Subject();
        // 订阅 Subject，只在配置的时间内没有新事件时更新状态
        const subscription = subjectRef.current
            .pipe(
                tap(() => {
                    enableExitTips(getRes().articleEdit.editExitWithoutSave);
                }),
                auditTime(autoSaveInterval * 1000),
                tap(() => {
                    pendingMessagesRef.current += 1; // 有新消息进入，标记为“待处理”
                }),
                concatMap(async (article) => {
                    // 确保顺序执行
                    const nextArticle = {
                        ...article,
                        logId: logIdRef.current,
                    };
                    try {
                        await onSubmit(nextArticle, false, false, true);
                    } catch (e) {
                        console.error(e);
                    } finally {
                        finishPendingAutoSave();
                    }
                })
            )
            .subscribe();
        subRef.current = subscription;
    };

    useEffect(() => {
        // 初始化 Subject，仅在组件挂载时创建一次
        setSubject();
        // 在组件卸载时清理订阅
        return () => {
            if (subRef.current) {
                subRef.current.unsubscribe();
            }
        };
    }, [state.articleEditAutoSaveInterval]);

    const isTitleError = (changedArticle: ArticleEntry) => {
        return changedArticle.title === undefined || changedArticle.title === null || changedArticle.title === "";
    };

    const isTypeError = (changedArticle: ArticleEntry) => {
        return changedArticle.typeId === undefined || changedArticle.typeId === null || changedArticle.typeId < 0;
    };

    const validForm = (changedArticle: ArticleEntry): boolean => {
        const titleError = isTitleError(changedArticle);
        const typeError = isTypeError(changedArticle);
        return !(titleError || typeError);
    };

    const handleValuesChange = (cv: ArticleChangeableValue) => {
        setState((prev) => {
            const newArticle = { ...prev.article, ...cv };
            //没有验证通过的情况下，保存本地缓存
            if (!validForm(newArticle)) {
                const updatedAt = Date.now();
                articleSaveToCache(newArticle, updatedAt);
                return {
                    ...prev,
                    article: newArticle,
                    contentSource: getLocalContentSource(newArticle),
                    contentSourceUpdatedAt: updatedAt,
                };
            } else {
                const sub = subjectRef.current;
                if (sub) {
                    sub.next(newArticle);
                }
            }
            return { ...prev, article: newArticle };
        });
    };

    const fieldAi = useArticleFieldAi({
        onValuesChange: handleValuesChange,
        onApplied: () => {
            void messageApi.success(getRes().articleEdit.assistant.applySuccess);
        },
    });

    const applyGeneratedCover = async (cover?: {
        dataUrl: string;
        extension?: string;
        messageId?: string;
    }): Promise<string | undefined> => {
        if (!cover?.dataUrl) {
            return undefined;
        }
        try {
            const { data } = await axiosInstance.post(
                `/api/admin/article/cover/apply?id=${state.article.logId ? state.article.logId : 0}`,
                {
                    dataUrl: cover.dataUrl,
                    extension: cover.extension,
                    messageId: cover.messageId,
                }
            );
            if (data.error) {
                await messageApi.error(data.message);
                return undefined;
            }
            handleValuesChange({ thumbnail: data.data.url });
            // setGeneratedCover(undefined);
            await messageApi.success(getRes().articleEdit.coverApplySuccess);
            return data.data.url;
        } catch (e) {
            await messageApi.error(e instanceof Error ? e.message : getRes().error.unknown);
            return undefined;
        }
    };

    const { useBreakpoint } = Grid;
    const rawScreens = useBreakpoint();
    const screens =
        Object.keys(rawScreens).length === 0
            ? { xs: false, sm: true, md: true, lg: true, xl: true, xxl: true }
            : rawScreens;
    const editorActionGroupGap = screens.sm ? 8 : 6;

    // header + bar + hr + bottom
    const rawBaseHeight = 64 + 64 + 32 + 14;

    const getBaseHeight = () => {
        if (fullScreen) {
            return 0;
        }
        if (screens.md) {
            return rawBaseHeight;
        }
        if (screens.sm) {
            return rawBaseHeight + 38;
        }
        return rawBaseHeight + 38 + 58;
    };

    const getEditorHeight = () => {
        const baseHeight = 58 + 48 + 32 + getBaseHeight();
        return `calc(100vh - ${baseHeight}px)`;
    };

    const insertAssetToMarkdown = (path: string) => {
        const fileName = path.split("/").pop() || "file";
        const isImage = /\.(png|jpe?g|gif|webp|svg|bmp|ico|avif)$/i.test(fileName);
        const insertMarkdown = isImage ? `![${fileName}](${path})` : `[${fileName}](${path})`;
        const editorView = editorViewRef.current;
        if (editorView) {
            insertTextAtCursor(insertMarkdown, insertMarkdown.length, editorView);
            return;
        }
        const currentMarkdown = state.article.markdown || "";
        const needLeadingBreak = currentMarkdown.length > 0 && !currentMarkdown.endsWith("\n");
        const nextMarkdown = `${currentMarkdown}${needLeadingBreak ? "\n\n" : ""}${insertMarkdown}`;
        handleValuesChange({ markdown: nextMarkdown });
    };

    const useLocalConflictContent = () => {
        setState((prevState) => {
            const conflict = prevState.contentConflict;
            if (!conflict) {
                return prevState;
            }
            const localArticle = {
                ...conflict.localArticle,
                logId: prevState.article.logId,
                lastUpdateDate: prevState.article.lastUpdateDate,
                previewUrl: prevState.article.previewUrl,
                version: conflict.serverVersion,
            };
            versionRef.current = conflict.serverVersion;
            enableExitTips(getRes().articleEdit.editExitWithoutSave);
            return {
                ...prevState,
                article: localArticle,
                rubbish: localArticle.rubbish === true,
                editorVersion: localArticle.version,
                contentSource: conflict.source,
                contentSourceUpdatedAt: conflict.localUpdatedAt,
                contentConflict: undefined,
            };
        });
        setRestoreInputRevision((revision) => revision + 1);
    };

    const keepServerConflictContent = () => {
        removeArticleCache(state.article);
        setState((prevState) => ({
            ...prevState,
            contentConflict: undefined,
        }));
    };

    const renderContentConflictAlert = () => {
        const conflict = state.contentConflict;
        if (!conflict) {
            return null;
        }
        const detail = getRes()
            .articleEdit.contentConflict.serverUpdatedWithLocalEditDetail.replace(
                "{serverVersion}",
                `${conflict.serverVersion}`
            )
            .replace("{localVersion}", `${conflict.localVersion}`);
        const fieldLabels = getRes().articleEdit.version.fields;
        const conflictFields = [
            {
                key: "title",
                label: fieldLabels.title,
                serverValue: state.article.title,
                localValue: conflict.localArticle.title,
            },
            {
                key: "digest",
                label: fieldLabels.digest,
                serverValue: state.article.digest,
                localValue: conflict.localArticle.digest,
            },
            {
                key: "keywords",
                label: fieldLabels.keywords,
                serverValue: state.article.keywords,
                localValue: conflict.localArticle.keywords,
            },
            {
                key: "alias",
                label: fieldLabels.alias,
                serverValue: state.article.alias,
                localValue: conflict.localArticle.alias,
            },
            {
                key: "thumbnail",
                label: fieldLabels.thumbnail,
                serverValue: state.article.thumbnail,
                localValue: conflict.localArticle.thumbnail,
            },
            {
                key: "typeId",
                label: fieldLabels.typeId,
                serverValue: state.article.typeId,
                localValue: conflict.localArticle.typeId,
            },
            {
                key: "canComment",
                label: fieldLabels.canComment,
                serverValue: state.article.canComment,
                localValue: conflict.localArticle.canComment,
            },
            {
                key: "recommended",
                label: fieldLabels.recommended,
                serverValue: state.article.recommended,
                localValue: conflict.localArticle.recommended,
            },
            {
                key: "privacy",
                label: fieldLabels.privacy,
                serverValue: state.article.privacy,
                localValue: conflict.localArticle.privacy,
            },
            {
                key: "rubbish",
                label: fieldLabels.rubbish,
                serverValue: state.article.rubbish,
                localValue: conflict.localArticle.rubbish,
            },
            {
                key: "markdown",
                label: fieldLabels.markdown,
                serverValue: getArticleBodyForConflict(state.article),
                localValue: getArticleBodyForConflict(conflict.localArticle),
            },
        ].filter((field) => normalizeConflictValue(field.serverValue) !== normalizeConflictValue(field.localValue));
        const localBodyLength = getArticleBodyForConflict(conflict.localArticle).trim().length;
        const serverBodyLength = getArticleBodyForConflict(state.article).trim().length;
        const bodyLengthDetail = getRes()
            .articleEdit.contentConflict.bodyLengthDetail.replace("{localLength}", `${localBodyLength}`)
            .replace("{serverLength}", `${serverBodyLength}`);
        return (
            <Alert
                type="warning"
                showIcon
                style={{ marginBottom: 12 }}
                message={getRes().articleEdit.contentConflict.serverUpdatedWithLocalEditTitle}
                description={
                    <Space direction="vertical" size={4}>
                        <span>{detail}</span>
                        {conflict.localUpdatedAt ? (
                            <span>
                                {getRes().articleEdit.contentConflict.localSavedAt}
                                {getLabelValueSeparator()}
                                <TimeAgo timestamp={conflict.localUpdatedAt} />
                            </span>
                        ) : null}
                        {conflictFields.length > 0 ? (
                            <Space size={[4, 4]} wrap>
                                <span>{getRes().articleEdit.contentConflict.changedFields}</span>
                                {conflictFields.map((field) => (
                                    <Tag key={field.key}>{field.label}</Tag>
                                ))}
                            </Space>
                        ) : null}
                        {localBodyLength !== serverBodyLength ? <span>{bodyLengthDetail}</span> : null}
                    </Space>
                }
                action={
                    <Space size={8}>
                        <Button size="small" onClick={keepServerConflictContent}>
                            {getRes().articleEdit.contentConflict.keepServer}
                        </Button>
                        <Button size="small" type="primary" onClick={useLocalConflictContent}>
                            {getRes().articleEdit.contentConflict.useLocalEdit}
                        </Button>
                    </Space>
                }
            />
        );
    };

    const updateAiMessageCache = (aiMessages: AIContent[]) => {
        const url = new URL(window.location.href);
        const cacheKey = getLocalCacheKey(url);
        const cachedData = getCacheByKey(cacheKey) as ArticleEditInfo | undefined;
        const newData = {
            ...(cachedData || data),
            aiMessages: aiMessages,
        };
        if (updateCache) {
            updateCache(newData, cacheKey);
        }
        setState((prevState) => {
            return {
                ...prevState,
                aiMessages: aiMessages,
            };
        });
    };

    const assistantConfig = useArticleAiAssistantConfig({
        data: state,
        offline,
        axiosInstance,
        onAiMessagesChange: updateAiMessageCache,
        onApplyValues: fieldAi.applyGeneratedValues,
        onApplyGeneratedCover: applyGeneratedCover,
    });

    const aiDrawerCacheKey = "ai/chat/drawer/width";
    const editorPreviewStateKey = "editor/preview";
    const articleAiStateCacheKey = useMemo(() => {
        if (state.article.logId) {
            return `ai/chat/state/${state.article.logId}`;
        }
        const url = new URL(window.location.href);
        return `ai/chat/state/${getLocalCacheKey(url)}`;
    }, [location.pathname, location.search, state.article.logId]);
    const articleAiStateCache = useMemo<AIStateCache>(
        () => ({
            key: articleAiStateCacheKey,
            read: getCacheByKey,
            write: addToCache,
        }),
        [articleAiStateCacheKey]
    );

    const getDefaultAiDrawerWidth = () => {
        const width = getCacheByKey(aiDrawerCacheKey);
        if (!width) {
            return "large";
        }
        return width;
    };

    const getEditorPreviewState = (): boolean => {
        const open = getCacheByKey<boolean>(editorPreviewStateKey);
        if (open === null || open === undefined) {
            return window.innerWidth > 600;
        }
        return open;
    };

    const updateAiDrawerWidth = (size: number) => {
        addToCache(aiDrawerCacheKey, size);
    };

    const getCardStyle = () => {
        if (fullScreen) {
            return {
                borderRadius: 0,
                overflow: "hidden",
            };
        }
        return { overflow: "hidden" };
    };

    const getSelectStyle = () => {
        if (
            getAppState().theme === "bootstrap" ||
            getAppState().theme === "shadcn" ||
            getAppState().theme === "default"
        ) {
            return { border: "none" };
        }
        return {};
    };

    return (
        <>
            <div
                style={{
                    gap: 8,
                    display: "flex",
                    justifyContent: "space-between",
                    alignItems: "center",
                }}
            >
                {!fullScreen ? (
                    <div style={{ display: "flex", alignItems: "center", gap: 8, flexWrap: "wrap" }}>
                        <Tag
                            bordered={false}
                            style={{
                                marginInlineEnd: 0,
                                borderRadius: theme.borderRadiusLG,
                                paddingInline: 10,
                                height: 28,
                                lineHeight: "28px",
                                fontSize: 12,
                                fontWeight: 500,
                                backgroundColor: theme.colorFillSecondary,
                                color: theme.colorTextSecondary,
                            }}
                        >
                            {articleStatusText}
                        </Tag>
                        {showContentSourceTag && (
                            <Tag color={contentSourceColor} bordered={false} style={{ marginInlineEnd: 0 }}>
                                {contentSourceText}
                            </Tag>
                        )}
                    </div>
                ) : null}
                {!fullScreen && (
                    <ArticleEditActionBar
                        key={data.article.logId + "actionbar_offline:" + offline}
                        fullScreen={fullScreen}
                        offline={offline}
                        data={state}
                        onSubmit={onSubmit}
                        onPreview={onPreview}
                        onOpenSettings={() => updateSettingsOpen(true)}
                        onOpenVersionHistory={() => updateVersionDrawerOpen(true)}
                        canOpenVersionHistory={Boolean(state.article.logId)}
                        onAiMessagesChange={(messages) => updateAiMessageCache(messages)}
                        onAiDrawerSizeChange={updateAiDrawerWidth}
                        aiDrawerOpen={articleAssistantOpen}
                        onAiDrawerOpenChange={updateArticleAssistantOpen}
                        aiDrawerWidth={getDefaultAiDrawerWidth()}
                        aiStateCache={articleAiStateCache}
                        onApplyAiValues={fieldAi.applyGeneratedValues}
                        onApplyGeneratedCover={applyGeneratedCover}
                    />
                )}
            </div>
            {!fullScreen && <Divider style={{ marginTop: 16, marginBottom: 16 }} />}
            {messageContextHolder}
            {renderContentConflictAlert()}
            <Card
                title={""}
                ref={editCardRef}
                style={getCardStyle()}
                styles={{
                    body: {
                        padding: 0,
                    },
                }}
            >
                <ArticleEditHeader
                    articleVersion={state.article.version}
                    dataDigest={state.article.digest}
                    state={state}
                    fullScreen={fullScreen}
                    offline={offline}
                    screens={screens}
                    editorActionGroupGap={editorActionGroupGap}
                    editCardRef={editCardRef}
                    getFullScreenElement={() => editCardRef.current}
                    titleRef={titleRef}
                    aliasRef={aliasRef}
                    digestRef={digestRef}
                    titleInputRevision={fieldAi.titleInputRevision + restoreInputRevision}
                    aliasInputRevision={fieldAi.aliasInputRevision + restoreInputRevision}
                    settingsOpen={settingsOpen}
                    versionDrawerOpen={versionDrawerOpen}
                    axiosInstance={axiosInstance}
                    aiDrawerWidth={getDefaultAiDrawerWidth()}
                    aiStateCache={articleAiStateCache}
                    articleAssistantOpen={articleAssistantOpen}
                    onArticleAssistantOpenChange={updateArticleAssistantOpen}
                    stateCacheKey={articleEditUiStateCacheKey}
                    saving={isSaving()}
                    onValuesChange={handleValuesChange}
                    onApplyAiValues={fieldAi.applyGeneratedValues}
                    onSettingsOpenChange={updateSettingsOpen}
                    onVersionOpenChange={updateVersionDrawerOpen}
                    onRollback={onRollback}
                    onSubmit={onSubmit}
                    onPreview={onPreview}
                    onAiMessagesChange={updateAiMessageCache}
                    onAiDrawerSizeChange={updateAiDrawerWidth}
                    onInsertMarkdownFromAsset={insertAssetToMarkdown}
                    onExitFullScreen={onExitFullScreen}
                    onFullScreen={onFullScreen}
                    getSelectStyle={getSelectStyle}
                />
                <Divider style={{ padding: 0, margin: 0 }} />
                <Editor
                    config={
                        {
                            lang: getAppState().lang as Locale,
                            dark: getAppState().dark,
                            onPreviewChange: (preview: boolean) => {
                                addToCache(editorPreviewStateKey, preview);
                            },
                            preview: getEditorPreviewState(),
                            colorPrimary: getAppState().colorPrimary,
                            uploadConfig: {
                                buildUploadUrl: (type: string) => {
                                    return "/api/admin/upload?dir=" + type;
                                },
                                axiosInstance: axiosInstance,
                                formName: "imgFile",
                                tryAppendBackendServerUrl: tryAppendBackendServerUrl,
                            },
                            aiConfig: {
                                aiApiUri: "/api/admin/article/ai",
                                configUrl: getRealRouteUrl("/website/ai"),
                                subject: state.article.title,
                                aiProvider: state.aiConfigured === true ? state.aiProvider : undefined,
                                aiMessages: state.aiMessages ? state.aiMessages : [],
                                messages: assistantConfig.messages,
                                contentMaxWidth: assistantConfig.contentMaxWidth,
                                renderMessage: assistantConfig.renderMessage,
                                renderFooter: ({ selectedText }: { selectedText?: string }) =>
                                    assistantConfig.renderFooter(selectedText),
                                overlays: assistantConfig.overlays,
                                onAiMessagesChange: updateAiMessageCache,
                                drawerWidth: getDefaultAiDrawerWidth(),
                                stateCache: articleAiStateCache,
                                user: getEditorUser(),
                                onSizeChange: updateAiDrawerWidth,
                                sessionId: state.article.logId ? state.article.logId : 0,
                            },
                            linkPreview: {
                                enabled: state.linkPreviewEnabled,
                                apiUrl: "/api/admin/link-preview",
                            },
                        } as any
                    }
                    fullscreen={fullScreen}
                    placeholder={getRes().articleEdit.editor.placeholder}
                    height={getEditorHeight()}
                    loadSuccess={(editor) => {
                        editorViewRef.current = editor as EditorView;
                    }}
                    previewContent={state.article.content ? state.article.content : ""}
                    getContainer={() => {
                        return editCardRef.current as HTMLDivElement;
                    }}
                    axiosInstance={axiosInstance}
                    value={state.article.markdown}
                    onChange={(v) => {
                        if (
                            v.value === "" &&
                            (state.article.markdown === "" ||
                                state.article.markdown === undefined ||
                                state.article.markdown === null)
                        ) {
                            return;
                        }
                        //不检查 content，避免因为 markdown 渲染库升级，载入文章时自动更新为草稿
                        if (v.value === state.article.markdown) {
                            return;
                        }
                        handleValuesChange({ markdown: v.value, content: v.previewContent });
                    }}
                />
                <EditorStatusBar
                    rubbish={state.article.rubbish}
                    offline={offline}
                    lastUpdateDate={statusBarLastUpdateDate}
                    data={toStatisticsByMarkdown(state.article.markdown)}
                    fullScreen={fullScreen}
                    dark={getAppState().dark}
                    extra={
                        <PublishStatusBar
                            saving={state.saving}
                            publishStatus={publishStatus}
                            onOpenChange={updatePublishStatusOpen}
                            onClose={closePublishStatus}
                            onOpenAssistant={!fullScreen ? () => updateArticleAssistantOpen(true) : undefined}
                            onLocatePublishCheckTarget={locatePublishCheckTarget}
                            getContainer={() => editCardRef.current as HTMLDivElement}
                        />
                    }
                    extraPlacement="right"
                />
            </Card>
        </>
    );
};

export default Index;
