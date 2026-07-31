import { FunctionComponent, useMemo, useRef, useState } from "react";
import { App, Grid, InputRef, message, Tag } from "antd";
import Divider from "antd/es/divider";
import Card from "antd/es/card";
import { getRealRouteUrl, getRes, tryAppendBackendServerUrl } from "../../utils/constants";
import { useAxiosBaseInstance } from "../../base/AppBase";
import { ArticleEditProps, PublishCheckTarget } from "./index.types";
import ArticleEditActionBar from "./article-edit-action-bar";
import ArticleEditHeader from "./article-edit-header";
import useArticleFieldAi from "./use-article-field-ai";
import { getEditorUser } from "../../utils/helpers";
import { useLocation } from "react-router";
import { addToCache, getCacheByKey } from "../../utils/cache";
import { getAppState } from "../../base/ConfigProviderApp";
import Editor, { insertTextAtCursor } from "@editor/dist/editor";
import EditorStatusBar from "@editor/dist/editor/editor-statistics-info";
import { toStatisticsByMarkdown } from "@editor/dist/editor/utils/editor-utils";
import { EditorView } from "@uiw/react-codemirror";
import { Locale } from "@editor/dist/editor/lang/editor-lang";
import { AIStateCache } from "@editor/dist/ai/AIStateCache";
import { useNavigate } from "react-router-dom";
import PublishStatusBar from "./publish-status-bar";
import { useArticleAiAssistantConfig } from "./article-ai-assistant/article-ai-assistant-button";
import useArticleEditUiState from "./use-article-edit-ui-state";
import ArticleContentConflictAlert from "./article-content-conflict-alert";
import useArticleSaveCoordinator from "./use-article-save-coordinator";
import { markdownToHtml } from "@editor/dist/editor/utils/marked-utils";
import { buildMarkdownImportedArticle, buildMarkdownImportedPatch } from "./markdown-import";
import { MarkdownImportApplyOptions } from "./markdown-import-modal";

const Index: FunctionComponent<ArticleEditProps> = ({
    offline,
    data,
    onExitFullScreen,
    onFullScreen,
    fullScreen,
    updateCache,
}) => {
    const location = useLocation();
    const editCardRef = useRef<HTMLDivElement>(null);
    const editorViewRef = useRef<EditorView | null>(null);
    const suppressedEditorMarkdownRef = useRef<string>();
    const preferredTypeId = useMemo(() => {
        const rawTypeId = new URLSearchParams(location.search).get("typeId");
        const typeId = rawTypeId ? Number(rawTypeId) : undefined;
        return typeId && Number.isFinite(typeId) && typeId > 0 ? typeId : undefined;
    }, [location.search]);
    const {
        cacheKey: articleEditUiStateCacheKey,
        settingsOpen,
        versionDrawerOpen,
        articleAssistantOpen,
        publishStatus,
        restore: applyCachedArticleEditUiState,
        updateSettingsOpen,
        updateVersionDrawerOpen,
        updateArticleAssistantOpen,
        updatePublishStatus,
    } = useArticleEditUiState(data.article.logId, location.search);
    const titleRef = useRef<InputRef>(null);
    const aliasRef = useRef<InputRef>(null);
    const digestRef = useRef<InputRef>(null);
    const [messageApi, messageContextHolder] = message.useMessage({
        maxCount: 3,
        getContainer: () => editCardRef.current as HTMLElement,
    });
    const { modal } = App.useApp();
    const axiosInstance = useAxiosBaseInstance(() => editCardRef.current as HTMLElement);
    const navigate = useNavigate();
    const {
        applyImportedArticle,
        getLocalCacheKey,
        createImportedDraft,
        handleValuesChange,
        isSaving,
        keepServerConflictContent,
        onPreview,
        onRollback,
        onSubmit,
        restoreInputRevision,
        state,
        updateAiMessageCache,
        useLocalConflictContent,
    } = useArticleSaveCoordinator({
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
        restoreUiState: applyCachedArticleEditUiState,
        updateCache,
        updatePublishStatus,
    });
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

    const updatePublishStatusOpen = (open: boolean) => {
        updatePublishStatus((previousState) => ({
            ...previousState,
            open,
        }));
    };

    const closePublishStatus = () => {
        updatePublishStatus((previousState) => ({
            ...previousState,
            open: false,
        }));
    };

    const focusInputRef = (inputRef: { current: InputRef | null }) => {
        window.setTimeout(() => inputRef.current?.focus(), 160);
    };

    const locatePublishCheckTarget = (target: PublishCheckTarget) => {
        closePublishStatus();
        if (target === "markdown") {
            updateSettingsOpen(false);
            editCardRef.current?.scrollIntoView({ behavior: "smooth", block: "center" });
            window.setTimeout(() => editorViewRef.current?.focus(), 160);
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

    const fieldAi = useArticleFieldAi({
        onValuesChange: handleValuesChange,
        onApplied: () => {
            void messageApi.success(getRes().articleEdit.assistant.applySuccess);
        },
    });
    const [markdownImportInputRevision, setMarkdownImportInputRevision] = useState(0);

    const getCurrentMarkdown = () => editorViewRef.current?.state.doc.toString() ?? state.article.markdown ?? "";

    const importMarkdown = async ({
        preview,
        selectedFields,
        selectedTypeId,
        target,
        expectedCurrentMarkdown,
    }: MarkdownImportApplyOptions): Promise<boolean> => {
        try {
            const html = await markdownToHtml(preview.markdown, {
                linkPreview: false,
            });
            if (target === "current" && getCurrentMarkdown() !== expectedCurrentMarkdown) {
                await messageApi.warning(getRes().articleEdit.markdownImport.stateChanged);
                return false;
            }
            const importedArticle = buildMarkdownImportedArticle({
                article: {
                    ...state.article,
                    markdown: getCurrentMarkdown(),
                },
                preview,
                selectedFields,
                selectedTypeId,
                target,
                html,
            });
            if (target === "newDraft") {
                return createImportedDraft(importedArticle);
            }
            const importedPatch = buildMarkdownImportedPatch({
                preview,
                selectedFields,
                selectedTypeId,
                html,
            });
            if (!applyImportedArticle(importedPatch)) {
                await messageApi.warning(getRes().articleEdit.markdownImport.stateChanged);
                return false;
            }
            const editorView = editorViewRef.current;
            if (getCurrentMarkdown() !== preview.markdown) {
                suppressedEditorMarkdownRef.current = preview.markdown;
                if (editorView) {
                    editorView.dispatch({
                        changes: {
                            from: 0,
                            to: editorView.state.doc.length,
                            insert: preview.markdown,
                        },
                    });
                }
            }
            if (selectedFields.has("digest") && digestRef.current?.input) {
                digestRef.current.input.value = importedPatch.digest || "";
            }
            setMarkdownImportInputRevision((revision) => revision + 1);
            await messageApi.success(getRes().articleEdit.markdownImport.importedCurrent);
            return true;
        } catch (error) {
            await messageApi.error(
                error instanceof Error ? error.message : getRes().articleEdit.markdownImport.errors.unknown
            );
            return false;
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
                        <Tag bordered={false} style={{ marginInlineEnd: 0 }}>
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
            {state.contentConflict ? (
                <ArticleContentConflictAlert
                    conflict={state.contentConflict}
                    serverArticle={state.article}
                    onKeepServer={keepServerConflictContent}
                    onUseLocal={useLocalConflictContent}
                />
            ) : null}
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
                    titleInputRevision={fieldAi.titleInputRevision + restoreInputRevision + markdownImportInputRevision}
                    aliasInputRevision={fieldAi.aliasInputRevision + restoreInputRevision + markdownImportInputRevision}
                    settingsOpen={settingsOpen}
                    versionDrawerOpen={versionDrawerOpen}
                    axiosInstance={axiosInstance}
                    aiDrawerWidth={getDefaultAiDrawerWidth()}
                    aiStateCache={articleAiStateCache}
                    articleAssistantOpen={articleAssistantOpen}
                    onArticleAssistantOpenChange={updateArticleAssistantOpen}
                    stateCacheKey={articleEditUiStateCacheKey}
                    saving={isSaving}
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
                    getCurrentMarkdown={getCurrentMarkdown}
                    onImportMarkdown={importMarkdown}
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
                        if (suppressedEditorMarkdownRef.current !== undefined) {
                            const suppress = suppressedEditorMarkdownRef.current === v.value;
                            suppressedEditorMarkdownRef.current = undefined;
                            if (suppress) {
                                return;
                            }
                        }
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
