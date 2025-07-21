import { FunctionComponent, useEffect, useRef, useState } from "react";
import { App, Button, Grid, InputRef, message, Space, Tag } from "antd";
import Row from "antd/es/grid/row";
import Col from "antd/es/grid/col";
import Divider from "antd/es/divider";
import Card from "antd/es/card";
import { createUri, getRealRouteUrl, getRes, tryAppendBackendServerUrl, updateUri } from "../../utils/constants";
import Select from "antd/es/select";
import BaseInput from "../../common/BaseInput";
import { isOffline } from "../../utils/env-utils";
import { useAxiosBaseInstance } from "../../base/AppBase";
import ArticleEditSettingButton from "./article-edit-setting-button";
import {
    ArticleChangeableValue,
    ArticleEditInfo,
    ArticleEditProps,
    ArticleEditState,
    ArticleEntry,
} from "./index.types";
import ArticleEditActionBar from "./article-edit-action-bar";
import ArticleVersionDrawer from "./article-version-drawer";
import {
    articleDataToState,
    articleSaveToCache,
    removeArticleCache,
    removeLocalArticleCache,
} from "../../utils/article-cache";
import ArticleEditFullscreenButton from "./article-edit-fullscreen-button";
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
import { LockOutlined } from "@ant-design/icons";
import { getAppState } from "../../base/ConfigProviderApp";
import Editor from "@editor/dist/src/editor";
import EditorStatistics from "@editor/dist/src/editor/editor-statistics-info";
import { toStatisticsByMarkdown } from "@editor/dist/src/editor/utils/editor-utils";
import { Locale } from "@editor/dist/src/editor/lang/editor-lang";
import { AIContent } from "@editor/dist/src/ai/AIContentItem";
import { useNavigate } from "react-router-dom";
import { postRefreshCacheSse } from "../../utils/sse-utils";
import { useTheme } from "antd-style";

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

    const defaultState = articleDataToState(data);
    const [state, setState] = useState<ArticleEditState>(defaultState);
    const [settingsOpen, setSettingsOpen] = useState(false);
    const [versionDrawerOpen, setVersionDrawerOpen] = useState(false);
    const isNewArticle = !state.article.logId;
    const articleStatusText = isNewArticle
        ? getRes().articleEdit.new
        : state.rubbish
        ? getRes().articleEdit.status.draft
        : state.article.privacy
        ? getRes().articleEdit.status.private
        : getRes().articleEdit.status.published;

    const aliasRef = useRef<InputRef>(null);
    const digestRef = useRef<InputRef>(null);
    const versionRef = useRef<number>(defaultState.article.version);
    const previewUrlRef = useRef<string | undefined>(defaultState.article.previewUrl);
    const logIdRef = useRef<number>(defaultState.article.logId ? defaultState.article.logId : -1);

    // 当由于路由跳转导致外部传入的 data 变化时，重新初始化表单状态
    useEffect(() => {
        const newState = articleDataToState(data);
        setState(newState);
        setSettingsOpen(false);
        setVersionDrawerOpen(false);
        versionRef.current = newState.article.version;
        previewUrlRef.current = newState.article.previewUrl;
        logIdRef.current = newState.article.logId ? newState.article.logId : -1;
    }, [data]);

    const subjectRef = useRef<Subject<ArticleEntry> | null>(null);
    const subRef = useRef<Subscription | null>(null);
    let pendingMessages = 0;

    const [messageApi, messageContextHolder] = message.useMessage({
        maxCount: 3,
        getContainer: () => editCardRef.current as HTMLElement,
    });
    const { modal } = App.useApp();
    const axiosInstance = useAxiosBaseInstance(() => editCardRef.current as HTMLElement);
    const navigate = useNavigate();

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
        articleSaveToCache(newArticle);
        setState((prevState) => {
            return {
                ...prevState,
                article: newArticle,
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
        if (pendingMessages === 0) {
            disableExitTips();
        }
    };

    const getLocalCacheKey = (url: URL) => {
        return getPageDataCacheKeyByPath(location.pathname, "?" + url.searchParams.toString());
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
            persistToCache(article);
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
                    messageApi.error({
                        key: "transparentPublish",
                        content: e instanceof Error ? e.message : getRes().articleEdit.saveFailed,
                    });
                    modal.error({
                        title: getRes().articleEdit.saveFailed,
                        content: e instanceof Error ? e.message : String(e),
                        getContainer: () => editCardRef.current as HTMLElement,
                    });
                    return false;
                }
                throw e;
            } finally {
                //@ts-ignore
            }
            const data = responseData;
            if (data.error === 0) {
                newArticle = handleArticleResponse(data, newArticle, create, autoSave);
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
        messageApi.open({
            key: "transparentPublish",
            type: "loading",
            content: getRes().staticSite.publishStart,
            duration: 0,
        });
        let refreshResponse: any;
        let articleResponse: any;
        await postRefreshCacheSse(uri, {
            body: article,
            messageApi,
            messageKey: "transparentPublish",
            responseEvents: ["article"],
            backgroundTaskTitle: getRes().backgroundTask.title + " · " + getRes().articleEdit.actions.release,
            onResponse: (data) => {
                refreshResponse = data;
            },
            onEvent: (event) => {
                if (event.event === "article") {
                    articleResponse = event.data;
                }
                if (event.event === "publish-start") {
                    messageApi.open({
                        key: "transparentPublish",
                        type: "loading",
                        content: getRes().staticSite.publishStart,
                        duration: 0,
                    });
                }
                if (event.event === "publish-complete") {
                    messageApi.success({
                        key: "transparentPublish",
                        content: getRes().staticSite.publishComplete,
                    });
                }
                if (event.event === "publish-error") {
                    messageApi.error({
                        key: "transparentPublish",
                        content: event.data?.message || getRes().staticSite.syncFailed,
                    });
                    throw new Error(event.data?.message || getRes().articleEdit.saveFailed);
                }
            },
        });
        return articleResponse || refreshResponse;
    };

    const handleArticleResponse = (data: any, baseArticle: ArticleEntry, create: boolean, autoSave: boolean) => {
        if (data.documentTitle) {
            updateDocumentTitle(data.documentTitle);
        }
        if (pendingMessages === 0) {
            disableExitTips();
        }
        if (!autoSave) {
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
            modal.error({
                title: getRes().articleEdit.rollbackFailed,
                content: data.message,
                getContainer: () => editCardRef.current as HTMLElement,
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
        const newState = articleDataToState(data);
        //如果文章内容没有变更，不更新 state，避免触发更新导致文章状态不对
        if (deepEqualWithSpecialJSON(newState.article, data.article)) {
            console.info("Skip article data useEffect() " + JSON.stringify(data.article.logId));
            return;
        }

        //仅设置状态，同时覆盖版本信息
        versionRef.current = newState.article.version;
        if (newState.article.logId) {
            logIdRef.current = newState.article.logId;
        } else {
            logIdRef.current = -1;
        }
        handleValuesChange(newState.article);
    }, [data.article]);

    useEffect(() => {
        setState((prevState) => {
            return {
                ...prevState,
                aiProvider: data.aiProvider,
                aiMessages: data.aiMessages,
            };
        });
    }, [data.aiProvider, data.aiMessages]);

    useEffect(() => {
        //如果文章内容没有变更，不更新 state，避免触发更新导致文章状态不对
        if (deepEqualWithSpecialJSON(state.article, data.article)) {
            console.info("Skip article data useEffect() " + JSON.stringify(data.article.logId));
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
        subjectRef.current = new Subject();
        // 订阅 Subject，只在 2 秒内没有新事件时更新状态
        const subscription = subjectRef.current
            .pipe(
                tap(() => {
                    enableExitTips(getRes().articleEdit.editExitWithoutSave);
                }),
                auditTime(2000),
                tap(() => {
                    pendingMessages += 1; // 有新消息进入，标记为“待处理”
                }),
                concatMap(async (article) => {
                    // 确保顺序执行
                    pendingMessages -= 1;
                    //console.log("Submitting:", nextValue);
                    article.logId = logIdRef.current;
                    try {
                        await onSubmit(article, false, false, true);
                    } catch (e) {
                        console.error(e);
                    }
                })
            )
            .subscribe();
        if (subRef.current) {
            subRef.current = subscription;
        }
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
    }, []);

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
                persistToCache(newArticle);
            } else {
                const sub = subjectRef.current;
                if (sub) {
                    sub.next(newArticle);
                }
            }
            return { ...prev, article: newArticle };
        });
    };

    const { useBreakpoint } = Grid;
    const screens = useBreakpoint();

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

    const updateAiMessageCache = (aiMessages: AIContent[]) => {
        const url = new URL(window.location.href);
        const cacheKey = getLocalCacheKey(url);
        const newData = getCacheByKey(cacheKey) as ArticleEditInfo;
        newData.aiMessages = aiMessages;
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

    const aiDrawerCacheKey = "ai/chat/drawer/width";
    const editorPreviewStateKey = "editor/preview";

    const getDefaultAiDrawerWidth = () => {
        const width = getCacheByKey(aiDrawerCacheKey);
        if (!width) {
            return "large";
        }
        return width;
    };

    const getEditorPreviewState = (): boolean => {
        const open = getCacheByKey(editorPreviewStateKey);
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
        return {};
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
                            backgroundColor: getAppState().dark ? "rgba(255,255,255,0.08)" : "rgba(15,23,42,0.05)",
                            color: getAppState().dark ? "rgba(255,255,255,0.72)" : "rgba(15,23,42,0.72)",
                        }}
                    >
                        {articleStatusText}
                    </Tag>
                ) : null}
                {!fullScreen && (
                    <ArticleEditActionBar
                        previewUrl={previewUrlRef.current}
                        key={data.article.logId + "actionbar_offline:" + offline}
                        fullScreen={fullScreen}
                        offline={offline}
                        data={state}
                        onSubmit={onSubmit}
                        onPreview={onPreview}
                        onOpenSettings={() => setSettingsOpen(true)}
                        onOpenVersionHistory={() => setVersionDrawerOpen(true)}
                        canOpenVersionHistory={Boolean(state.article.logId)}
                        onAiMessagesChange={(messages) => updateAiMessageCache(messages)}
                        onAiDrawerSizeChange={updateAiDrawerWidth}
                        aiDrawerWidth={getDefaultAiDrawerWidth()}
                    />
                )}
            </div>
            {!fullScreen && <Divider style={{ marginTop: 16, marginBottom: 16 }} />}
            {messageContextHolder}
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
                <Row
                    gutter={[8, 0]}
                    style={{
                        position: "relative",
                        marginInline: 0,
                    }}
                >
                    <Col
                        md={fullScreen ? 4 : 8}
                        xl={9}
                        xxl={12}
                        xs={16}
                        sm={fullScreen ? 4 : 6}
                        style={{ paddingInline: 0 }}
                    >
                        <BaseInput
                            suffix={
                                <div style={{ display: "flex", gap: 4, height: 32, alignItems: "center" }}>
                                    {state.article.rubbish && (
                                        <Button disabled={true} style={{ padding: 0, fontSize: 16 }} type={"text"}>
                                            {getRes().articleEdit.status.draft}
                                        </Button>
                                    )}
                                    {state.article.privacy && (
                                        <LockOutlined style={{ color: "rgb(119, 119, 119)", fontSize: 16 }} />
                                    )}
                                </div>
                            }
                            maxLength={100}
                            variant={"borderless"}
                            size={"large"}
                            key={data.article.version}
                            placeholder={getRes().articleEdit.inputTitle}
                            defaultValue={state.article.title ? state.article.title : undefined}
                            onChange={(e) => {
                                handleValuesChange({ title: e });
                            }}
                            style={{ fontSize: 22, fontWeight: 500, textOverflow: "ellipsis" }}
                        />
                    </Col>
                    <Col
                        md={fullScreen ? 8 : 13}
                        xxl={fullScreen ? 7 : 10}
                        xs={24}
                        sm={fullScreen ? 8 : 12}
                        style={{ display: "flex", alignItems: "center" }}
                    >
                        <Space.Compact style={{ display: "flex", width: "100%" }} hidden={fullScreen}>
                            <Select
                                getPopupContainer={(triggerNode) => triggerNode.parentElement}
                                variant={"borderless"}
                                style={{
                                    minWidth: 156,
                                    display: "flex",
                                    zIndex: 20,
                                    ...getSelectStyle(),
                                }}
                                size={"large"}
                                value={state.article.typeId}
                                showSearch={{
                                    optionFilterProp: "children",
                                    filterOption: (input, option) => (option?.label ?? "").includes(input),
                                    filterSort: (optionA, optionB) =>
                                        (optionA?.label ?? "")
                                            .toLowerCase()
                                            .localeCompare((optionB?.label ?? "").toLowerCase()),
                                }}
                                onChange={(value) => {
                                    handleValuesChange({ typeId: value });
                                }}
                                options={state.typeOptions}
                                placeholder={getRes().pleaseChoose + getRes().articleType.title}
                            />
                            <BaseInput
                                ref={aliasRef}
                                defaultValue={state.article.alias}
                                onChange={(e) => {
                                    handleValuesChange({ alias: e });
                                }}
                                key={data.article.version}
                                maxLength={256}
                                size={"large"}
                                variant={"borderless"}
                                placeholder={getRes().articleEdit.inputAlias}
                                style={{ fontSize: 16, minWidth: 48, paddingLeft: 0, textOverflow: "ellipsis" }}
                            />
                        </Space.Compact>
                    </Col>
                    <Col
                        md={fullScreen ? 6 : 4}
                        style={{
                            display: "flex",
                            alignItems: "center",
                            justifyContent: "flex-end",
                            position: "absolute",
                            right: 0,
                            gap: 2,
                            top: 0,
                            bottom: 0,
                        }}
                    >
                        {fullScreen && (
                            <Col xxl={9} md={12} sm={18} xs={16} style={{ padding: 0 }}>
                                <ArticleEditActionBar
                                    getContainer={() => editCardRef.current as HTMLElement}
                                    offline={offline}
                                    fullScreen={fullScreen}
                                    data={state}
                                    onPreview={onPreview}
                                    onOpenSettings={() => setSettingsOpen(true)}
                                    onOpenVersionHistory={() => setVersionDrawerOpen(true)}
                                    canOpenVersionHistory={Boolean(state.article.logId)}
                                    onAiMessagesChange={(messages) => updateAiMessageCache(messages)}
                                    onSubmit={onSubmit}
                                    previewUrl={previewUrlRef.current}
                                />
                            </Col>
                        )}
                        <ArticleEditSettingButton
                            initDigest={data.article.digest ? data.article.digest : ""}
                            digestRef={digestRef}
                            article={state.article}
                            saving={isSaving()}
                            tags={state.tags}
                            containerRef={editCardRef}
                            open={settingsOpen}
                            onOpenChange={setSettingsOpen}
                            handleValuesChange={handleValuesChange}
                        />
                        <ArticleVersionDrawer
                            logId={state.article.logId}
                            currentVersion={state.article.version}
                            axiosInstance={axiosInstance}
                            onRollback={onRollback}
                            containerRef={editCardRef}
                            open={versionDrawerOpen}
                            onOpenChange={setVersionDrawerOpen}
                        />
                        <ArticleEditFullscreenButton
                            fullScreen={fullScreen}
                            fullScreenElement={editCardRef.current as HTMLDivElement}
                            onExitFullScreen={onExitFullScreen}
                            onFullScreen={onFullScreen}
                        />
                    </Col>
                </Row>
                <Divider style={{ padding: 0, margin: 0 }} />
                <Editor
                    config={{
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
                            aiProvider: state.aiProvider,
                            aiMessages: state.aiMessages ? state.aiMessages : [],
                            onAiMessagesChange: updateAiMessageCache,
                            drawerWidth: getDefaultAiDrawerWidth(),
                            user: getEditorUser(),
                            onSizeChange: updateAiDrawerWidth,
                            sessionId: state.article.logId ? state.article.logId : 0,
                        },
                    }}
                    fullscreen={fullScreen}
                    placeholder={getRes().articleEdit.editor.placeholder}
                    height={getEditorHeight()}
                    loadSuccess={() => {
                        //ignore
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
                <EditorStatistics
                    rubbish={state.article.rubbish}
                    offline={offline}
                    lastUpdateDate={state.article.lastUpdateDate ? state.article.lastUpdateDate : 0}
                    data={toStatisticsByMarkdown(state.article.markdown)}
                    fullScreen={fullScreen}
                    dark={getAppState().dark}
                />
            </Card>
        </>
    );
};

export default Index;
