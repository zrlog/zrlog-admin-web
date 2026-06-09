import { Button, InputRef, Space, Tag } from "antd";
import Select from "antd/es/select";
import { LockOutlined, StarFilled } from "@ant-design/icons";
import { FunctionComponent, RefObject } from "react";
import BaseInput from "../../common/BaseInput";
import { getRes } from "../../utils/constants";
import ArticleEditActionBar from "./article-edit-action-bar";
import ArticleEditMoreActions from "./article-edit-more-actions";
import ArticleEditSettingButton from "./article-edit-setting-button";
import { ArticleChangeableValue, ArticleEditState, ArticleEntry } from "./index.types";
import { AIContent } from "@editor/dist/src/ai/AIContentItem";
import { AIStateCache } from "@editor/dist/src/ai/AIStateCache";

type ArticleEditHeaderProps = {
    articleVersion: number;
    dataDigest?: string;
    state: ArticleEditState;
    fullScreen: boolean;
    offline: boolean;
    screens: any;
    editorActionGroupGap: number;
    editCardRef: RefObject<HTMLDivElement>;
    getFullScreenElement: () => HTMLElement | null;
    titleRef: RefObject<InputRef>;
    aliasRef: RefObject<InputRef>;
    digestRef: RefObject<InputRef>;
    titleInputRevision: number;
    aliasInputRevision: number;
    settingsOpen: boolean;
    versionDrawerOpen: boolean;
    axiosInstance: any;
    aiDrawerWidth?: number | "default" | "large";
    aiStateCache?: AIStateCache;
    saving: boolean;
    onValuesChange: (cv: ArticleChangeableValue) => void;
    onApplyAiValues: (cv: ArticleChangeableValue) => void;
    onSettingsOpenChange: (open: boolean) => void;
    onVersionOpenChange: (open: boolean) => void;
    onRollback: (targetVersion: number) => Promise<void>;
    onSubmit: (article: ArticleEntry, release: boolean, preview: boolean, autoSave: boolean) => Promise<boolean>;
    onPreview?: () => Promise<void>;
    onAiMessagesChange: (messages: AIContent[]) => void;
    onAiDrawerSizeChange: (newSize: number) => void;
    onInsertMarkdownFromAsset: (path: string) => void;
    onExitFullScreen: () => void;
    onFullScreen: () => void;
    getSelectStyle: () => Record<string, any>;
};

const ArticleEditHeader: FunctionComponent<ArticleEditHeaderProps> = ({
    articleVersion,
    dataDigest,
    state,
    fullScreen,
    offline,
    screens,
    editorActionGroupGap,
    editCardRef,
    getFullScreenElement,
    titleRef,
    aliasRef,
    digestRef,
    titleInputRevision,
    aliasInputRevision,
    settingsOpen,
    versionDrawerOpen,
    axiosInstance,
    aiDrawerWidth,
    aiStateCache,
    saving,
    onValuesChange,
    onApplyAiValues,
    onSettingsOpenChange,
    onVersionOpenChange,
    onRollback,
    onSubmit,
    onPreview,
    onAiMessagesChange,
    onAiDrawerSizeChange,
    onInsertMarkdownFromAsset,

    onExitFullScreen,
    onFullScreen,
    getSelectStyle,
}) => {
    const getContainer = () => editCardRef.current as HTMLElement;
    const contentSourceText = (() => {
        if (state.contentSource === "localDraft") {
            return getRes().articleEdit.contentSource.localDraft;
        }
        if (state.contentSource === "localEdit") {
            return getRes().articleEdit.contentSource.localEdit;
        }
        return getRes().articleEdit.contentSource.server;
    })();
    const contentSourceColor = state.contentSource === "localEdit" ? "warning" : "processing";
    const categorySelect = (
        <Select
            getPopupContainer={(triggerNode) => triggerNode.parentElement}
            variant={"borderless"}
            style={{
                minWidth: screens.sm ? 156 : 120,
                flex: screens.sm ? undefined : "0 0 120px",
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
                    (optionA?.label ?? "").toLowerCase().localeCompare((optionB?.label ?? "").toLowerCase()),
            }}
            onChange={(value) => onValuesChange({ typeId: value })}
            options={state.typeOptions}
            placeholder={getRes().pleaseChoose + getRes().articleType.title}
        />
    );

    return (
        <div
            style={{
                display: "flex",
                alignItems: "center",
                gap: editorActionGroupGap,
                minHeight: screens.sm ? undefined : 78,
                flexWrap: screens.sm ? "nowrap" : "wrap",
            }}
        >
            <div
                style={{
                    display: "flex",
                    alignItems: "center",
                    flex: screens.sm ? "0 0 50%" : "1 1 100%",
                    maxWidth: screens.sm ? "50%" : "100%",
                    minWidth: 0,
                }}
            >
                <div style={{ flex: "1 1 0", minWidth: 0 }}>
                    <BaseInput
                        ref={titleRef}
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
                                {state.article.recommended && (
                                    <Tag
                                        color="gold"
                                        bordered={false}
                                        icon={<StarFilled />}
                                        style={{ marginInlineEnd: 0 }}
                                    >
                                        {getRes().articleEdit.recommended}
                                    </Tag>
                                )}
                                {fullScreen && state.contentSource !== "server" && (
                                    <Tag color={contentSourceColor} bordered={false} style={{ marginInlineEnd: 0 }}>
                                        {contentSourceText}
                                    </Tag>
                                )}
                            </div>
                        }
                        maxLength={100}
                        variant={"borderless"}
                        size={"large"}
                        key={`${articleVersion}-${titleInputRevision}`}
                        placeholder={getRes().articleEdit.inputTitle}
                        defaultValue={state.article.title ? state.article.title : undefined}
                        onChange={(e) => onValuesChange({ title: e })}
                        style={{
                            fontSize: 22,
                            fontWeight: 500,
                            paddingInline: screens.sm ? undefined : 6,
                            textOverflow: "ellipsis",
                        }}
                    />
                </div>
                {!screens.sm && categorySelect}
            </div>
            <div
                style={{
                    display: "flex",
                    flex: screens.sm ? "1 1 0" : "1 1 100%",
                    minWidth: 0,
                    alignItems: "center",
                    gap: editorActionGroupGap,
                    paddingInlineStart: screens.sm ? 0 : editorActionGroupGap,
                }}
            >
                <Space.Compact style={{ display: "flex", flex: "1 1 0", minWidth: 0 }}>
                    {screens.sm && categorySelect}
                    <BaseInput
                        ref={aliasRef}
                        defaultValue={state.article.alias}
                        onChange={(e) => onValuesChange({ alias: e })}
                        key={`${articleVersion}-${aliasInputRevision}`}
                        maxLength={256}
                        size={"large"}
                        variant={"borderless"}
                        placeholder={getRes().articleEdit.inputAlias}
                        style={{ fontSize: 16, minWidth: 48, paddingLeft: 0, textOverflow: "ellipsis" }}
                    />
                </Space.Compact>
                <div
                    style={{
                        display: "flex",
                        alignItems: "center",
                        justifyContent: "flex-end",
                        flex: "none",
                        gap: editorActionGroupGap,
                    }}
                >
                    {fullScreen && (
                        <ArticleEditActionBar
                            getContainer={getContainer}
                            offline={offline}
                            fullScreen={fullScreen}
                            data={state}
                            onPreview={onPreview}
                            onOpenSettings={() => onSettingsOpenChange(true)}
                            onOpenVersionHistory={() => onVersionOpenChange(true)}
                            canOpenVersionHistory={Boolean(state.article.logId)}
                            onAiMessagesChange={onAiMessagesChange}
                            onSubmit={onSubmit}
                            aiDrawerWidth={aiDrawerWidth}
                            aiStateCache={aiStateCache}
                            onAiDrawerSizeChange={onAiDrawerSizeChange}
                            onApplyAiValues={onApplyAiValues}
                        />
                    )}
                    <div
                        style={{
                            alignItems: "center",
                            display: "flex",
                            gap: 2,
                            justifyContent: "flex-end",
                            paddingRight: 4,
                        }}
                    >
                        <ArticleEditSettingButton
                            initDigest={dataDigest ? dataDigest : ""}
                            digestRef={digestRef}
                            article={state.article}
                            saving={saving}
                            tags={state.tags}
                            containerRef={editCardRef}
                            open={settingsOpen}
                            onOpenChange={onSettingsOpenChange}
                            handleValuesChange={onValuesChange}
                            coverAspectRatio={state.articleCoverAspectRatio}
                        />
                        <ArticleEditMoreActions
                            fullScreen={fullScreen}
                            offline={offline}
                            logId={state.article.logId}
                            socialPreview={state.article.socialPreview}
                            currentVersion={state.article.version}
                            axiosInstance={axiosInstance}
                            containerRef={editCardRef}
                            getFullScreenElement={getFullScreenElement}
                            versionDrawerOpen={versionDrawerOpen}
                            onPreview={onPreview}
                            onRollback={onRollback}
                            onVersionOpenChange={onVersionOpenChange}
                            onInsertMarkdownFromAsset={onInsertMarkdownFromAsset}
                            onExitFullScreen={onExitFullScreen}
                            onFullScreen={onFullScreen}
                        />
                    </div>
                </div>
            </div>
        </div>
    );
};

export default ArticleEditHeader;
