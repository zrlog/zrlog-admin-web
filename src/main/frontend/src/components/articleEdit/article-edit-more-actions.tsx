import { App, Button, Dropdown, Grid, Modal } from "antd";
import {
    EllipsisOutlined,
    EyeOutlined,
    FileMarkdownOutlined,
    FilePdfOutlined,
    FolderOpenOutlined,
    FullscreenExitOutlined,
    FullscreenOutlined,
    HistoryOutlined,
    ShareAltOutlined,
} from "@ant-design/icons";
import { ChangeEvent, FunctionComponent, RefObject, useCallback, useEffect, useRef, useState } from "react";
import screenfull from "screenfull";
import { getEnterFullscreen, getExitFullscreen, getRes } from "../../utils/constants";
import ArticleVersionDrawer from "./article-version-drawer";
import { getShortcutTitle, isTouchLikeDevice } from "./shortcut-utils";
import { getAiDrawerOpen } from "@editor/dist/ai/AIDrawer";
import { getAppState } from "../../base/ConfigProviderApp";
import FileManagerPicker from "../file-manager/picker";
import ArticleSocialPreviewDrawer from "./article-social-preview-drawer";
import { ArticleEntry, SocialPreview } from "./index.types";
import { exportArticlePdf } from "../article/ArticlePdfAction";
import { addToCache, getCacheByKey } from "../../utils/cache";
import { useTheme } from "antd-style";
import MarkdownImportModal, { MarkdownImportApplyOptions } from "./markdown-import-modal";
import {
    ArticleTypeOption,
    MarkdownImportError,
    MarkdownImportErrorCode,
    MarkdownImportPreview,
    readMarkdownImportFile,
} from "./markdown-import";

type ArticleEditMoreActionsProps = {
    fullScreen: boolean;
    offline: boolean;
    article: ArticleEntry;
    contentConflict: boolean;
    typeOptions: ArticleTypeOption[];
    logId?: number;
    socialPreview?: SocialPreview;
    currentVersion: number;
    axiosInstance: any;
    containerRef: RefObject<HTMLDivElement>;
    getFullScreenElement: () => HTMLElement | null;
    stateCacheKey: string;
    versionDrawerOpen: boolean;
    onPreview?: () => Promise<void>;
    onRollback: (targetVersion: number) => Promise<void>;
    onVersionOpenChange: (open: boolean) => void;
    onInsertMarkdownFromAsset: (path: string) => void;
    getCurrentMarkdown: () => string;
    onImportMarkdown: (options: MarkdownImportApplyOptions) => Promise<boolean>;
    importMarkdownIntent?: boolean;
    onExitFullScreen: () => void;
    onFullScreen: () => void;
};

const ArticleEditMoreActions: FunctionComponent<ArticleEditMoreActionsProps> = ({
    fullScreen,
    offline,
    article,
    contentConflict,
    typeOptions,
    logId,
    socialPreview,
    currentVersion,
    axiosInstance,
    containerRef,
    getFullScreenElement,
    stateCacheKey,
    versionDrawerOpen,
    onPreview,
    onRollback,
    onVersionOpenChange,
    onInsertMarkdownFromAsset,
    getCurrentMarkdown,
    onImportMarkdown,
    importMarkdownIntent,
    onExitFullScreen,
    onFullScreen,
}) => {
    const theme = useTheme();
    const { message } = App.useApp();
    const assetPickerOpenCacheKey = `${stateCacheKey}/assetPickerOpen`;
    const socialPreviewOpenCacheKey = `${stateCacheKey}/socialPreviewOpen`;
    const [assetPickerOpen, setAssetPickerOpenState] = useState(
        getCacheByKey<boolean>(assetPickerOpenCacheKey) === true
    );
    const [socialPreviewOpen, setSocialPreviewOpenState] = useState(
        getCacheByKey<boolean>(socialPreviewOpenCacheKey) === true
    );
    const [markdownImportPreview, setMarkdownImportPreview] = useState<MarkdownImportPreview>();
    const [markdownImportSource, setMarkdownImportSource] = useState("");
    const [moreActionsOpen, setMoreActionsOpen] = useState(importMarkdownIntent === true);
    const markdownFileInputRef = useRef<HTMLInputElement>(null);
    const markdownImportReadGenerationRef = useRef(0);
    const screens = Grid.useBreakpoint();
    const narrow = screens.md !== true;
    const assetPickerWidth = narrow ? "100vw" : screens.lg ? 860 : 720;
    const setAssetPickerOpen = useCallback(
        (open: boolean) => {
            setAssetPickerOpenState(open);
            addToCache(assetPickerOpenCacheKey, open);
        },
        [assetPickerOpenCacheKey]
    );
    const setSocialPreviewOpen = useCallback(
        (open: boolean) => {
            setSocialPreviewOpenState(open);
            addToCache(socialPreviewOpenCacheKey, open);
        },
        [socialPreviewOpenCacheKey]
    );

    useEffect(() => {
        setAssetPickerOpenState(getCacheByKey<boolean>(assetPickerOpenCacheKey) === true);
        setSocialPreviewOpenState(getCacheByKey<boolean>(socialPreviewOpenCacheKey) === true);
    }, [assetPickerOpenCacheKey, socialPreviewOpenCacheKey]);

    useEffect(() => {
        if (importMarkdownIntent) {
            setMoreActionsOpen(true);
        }
    }, [importMarkdownIntent]);

    const getMarkdownImportErrorMessage = (code: MarkdownImportErrorCode) => {
        const errors = getRes().articleEdit.markdownImport.errors;
        switch (code) {
            case "invalid-extension":
                return errors.invalidExtension;
            case "too-large":
                return errors.tooLarge;
            case "invalid-utf8":
                return errors.invalidUtf8;
            case "empty-file":
                return errors.emptyFile;
            case "binary-file":
                return errors.binaryFile;
            case "unclosed-front-matter":
                return errors.unclosedFrontMatter;
            case "invalid-front-matter":
                return errors.invalidFrontMatter;
            case "front-matter-root":
                return errors.frontMatterRoot;
            case "front-matter-too-complex":
                return errors.frontMatterTooComplex;
        }
    };

    const selectMarkdownFile = () => {
        markdownFileInputRef.current?.click();
    };

    const readMarkdownFile = async (event: ChangeEvent<HTMLInputElement>) => {
        const file = event.currentTarget.files?.[0];
        event.currentTarget.value = "";
        if (!file) {
            return;
        }
        const readGeneration = ++markdownImportReadGenerationRef.current;
        try {
            const preview = await readMarkdownImportFile(file);
            if (readGeneration !== markdownImportReadGenerationRef.current) {
                return;
            }
            setMarkdownImportSource(getCurrentMarkdown());
            setMarkdownImportPreview(preview);
        } catch (error) {
            if (readGeneration !== markdownImportReadGenerationRef.current) {
                return;
            }
            const errorMessage =
                error instanceof MarkdownImportError
                    ? getMarkdownImportErrorMessage(error.code)
                    : getRes().articleEdit.markdownImport.errors.unknown;
            void message.error(errorMessage);
        }
    };

    const closeMarkdownImport = () => {
        setMarkdownImportPreview(undefined);
        setMarkdownImportSource("");
    };

    const toggleFullScreen = useCallback(() => {
        if (fullScreen) {
            if (screenfull.isEnabled) {
                screenfull.exit().finally(onExitFullScreen);
            } else {
                onExitFullScreen();
            }
            return;
        }
        const fullScreenElement = getFullScreenElement() || containerRef.current;
        if (screenfull.isEnabled && fullScreenElement) {
            screenfull.request(fullScreenElement).finally(onFullScreen);
        } else {
            onFullScreen();
        }
    }, [containerRef, fullScreen, getFullScreenElement, onExitFullScreen, onFullScreen]);

    useEffect(() => {
        const nativeFullscreenChange = () => {
            if (!document.fullscreenElement && fullScreen) {
                onExitFullScreen();
            }
        };
        document.addEventListener("fullscreenchange", nativeFullscreenChange);
        return () => {
            document.removeEventListener("fullscreenchange", nativeFullscreenChange);
        };
    }, [fullScreen, onExitFullScreen]);

    useEffect(() => {
        const handleKeyPress = (event: KeyboardEvent) => {
            if (getAiDrawerOpen() || isTouchLikeDevice()) {
                return;
            }
            if (event.altKey && event.shiftKey && event.key.toLowerCase() === "f") {
                event.preventDefault();
                toggleFullScreen();
            }
        };
        window.addEventListener("keydown", handleKeyPress);
        return () => {
            window.removeEventListener("keydown", handleKeyPress);
        };
    }, [toggleFullScreen]);

    const items = [
        {
            key: "preview",
            icon: <EyeOutlined />,
            label: getRes().preview,
            disabled: offline || !onPreview,
            onClick: () => void onPreview?.(),
        },
        {
            key: "version",
            icon: <HistoryOutlined />,
            label: getRes().articleEdit.version.label,
            disabled: !logId,
            onClick: () => onVersionOpenChange(true),
        },
        {
            key: "social-preview",
            icon: <ShareAltOutlined />,
            label: getRes().articleEdit.socialPreview.title,
            disabled: !logId || !socialPreview,
            onClick: () => setSocialPreviewOpen(true),
        },
        {
            key: "export-pdf",
            icon: <FilePdfOutlined />,
            label: getRes().article.exportPdf,
            onClick: () => exportArticlePdf(article, () => message.warning(getRes().article.exportPdfPopupBlocked)),
        },
        {
            key: "import-markdown",
            icon: <FileMarkdownOutlined />,
            label: getRes().articleEdit.markdownImport.menu,
            onClick: selectMarkdownFile,
        },
        {
            key: "asset",
            icon: <FolderOpenOutlined />,
            label: getRes().articleEdit.actions.chooseFromAssets,
            onClick: () => setAssetPickerOpen(true),
        },
        {
            key: "fullscreen",
            icon: fullScreen ? <FullscreenExitOutlined /> : <FullscreenOutlined />,
            label: fullScreen ? getExitFullscreen() : getEnterFullscreen(),
            title: getShortcutTitle(fullScreen ? getExitFullscreen() : getEnterFullscreen(), {
                alt: true,
                shift: true,
                key: "F",
            }),
            onClick: ({ domEvent }: any) => {
                domEvent.preventDefault();
                domEvent.stopPropagation();
                toggleFullScreen();
            },
        },
    ];

    return (
        <>
            <input
                ref={markdownFileInputRef}
                type="file"
                accept=".md,.markdown,text/markdown"
                hidden
                onChange={(event) => void readMarkdownFile(event)}
            />
            <Dropdown
                menu={{ items, selectedKeys: importMarkdownIntent ? ["import-markdown"] : [] }}
                trigger={["click"]}
                getPopupContainer={() => containerRef.current as HTMLElement}
                placement="bottomRight"
                open={moreActionsOpen}
                onOpenChange={setMoreActionsOpen}
            >
                <Button
                    type="text"
                    aria-label={getRes().articleEdit.actions.more}
                    title={getRes().articleEdit.actions.more}
                    icon={
                        <EllipsisOutlined style={{ fontSize: getAppState().compactMode ? 18 : 24, display: "flex" }} />
                    }
                    style={{
                        border: 0,
                        display: "flex",
                        justifyContent: "center",
                        alignItems: "center",
                        cursor: "pointer",
                        color: theme.colorTextTertiary,
                    }}
                />
            </Dropdown>
            <ArticleVersionDrawer
                logId={logId}
                currentVersion={currentVersion}
                axiosInstance={axiosInstance}
                onRollback={onRollback}
                containerRef={containerRef}
                open={versionDrawerOpen}
                onOpenChange={onVersionOpenChange}
                showTrigger={false}
            />
            <ArticleSocialPreviewDrawer
                preview={socialPreview}
                containerRef={containerRef}
                open={socialPreviewOpen}
                onOpenChange={setSocialPreviewOpen}
            />
            <MarkdownImportModal
                article={article}
                contentConflict={contentConflict}
                containerRef={containerRef}
                offline={offline}
                open={Boolean(markdownImportPreview)}
                preview={markdownImportPreview}
                currentMarkdown={markdownImportSource}
                typeOptions={typeOptions}
                onApply={onImportMarkdown}
                onCancel={closeMarkdownImport}
            />
            <Modal
                title={getRes().articleEdit.actions.chooseFromAssets}
                open={assetPickerOpen}
                onCancel={() => setAssetPickerOpen(false)}
                footer={null}
                width={assetPickerWidth}
                getContainer={() => containerRef.current as HTMLElement}
                destroyOnClose
                style={narrow ? { top: 0, maxWidth: "100vw", paddingBottom: 0 } : undefined}
                styles={{
                    body: {
                        height: narrow ? "calc(100dvh - 112px)" : "70vh",
                        minHeight: narrow ? 360 : 420,
                        padding: 0,
                    },
                }}
            >
                <FileManagerPicker
                    stateCacheKey={`${stateCacheKey}/assetPicker`}
                    style={{ height: "100%" }}
                    onSelectFile={(path) => {
                        onInsertMarkdownFromAsset(path);
                        setAssetPickerOpen(false);
                    }}
                />
            </Modal>
        </>
    );
};

export default ArticleEditMoreActions;
