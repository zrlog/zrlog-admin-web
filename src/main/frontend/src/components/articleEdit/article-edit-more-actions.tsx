import { App, Button, Dropdown, Grid, Modal } from "antd";
import {
    EllipsisOutlined,
    EyeOutlined,
    FilePdfOutlined,
    FolderOpenOutlined,
    FullscreenExitOutlined,
    FullscreenOutlined,
    HistoryOutlined,
    ShareAltOutlined,
} from "@ant-design/icons";
import { FunctionComponent, RefObject, useCallback, useEffect, useState } from "react";
import screenfull from "screenfull";
import { getEnterFullscreen, getExitFullscreen, getRes } from "../../utils/constants";
import ArticleVersionDrawer from "./article-version-drawer";
import { getShortcutTitle, isTouchLikeDevice } from "./shortcut-utils";
import { getAiDrawerOpen } from "@editor/dist/ai/AIDrawer";
import { getAppState } from "../../base/ConfigProviderApp";
import FileManagerPicker from "../file-manager/picker";
import ArticleSocialPreviewDrawer from "./article-social-preview-drawer";
import { SocialPreview } from "./index.types";
import type { ArticlePrintableEntry } from "../article/ArticlePreviewAction";
import { exportArticlePdf } from "../article/ArticlePdfAction";
import { addToCache, getCacheByKey } from "../../utils/cache";

type ArticleEditMoreActionsProps = {
    fullScreen: boolean;
    offline: boolean;
    article: ArticlePrintableEntry;
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
    onExitFullScreen: () => void;
    onFullScreen: () => void;
};

const ArticleEditMoreActions: FunctionComponent<ArticleEditMoreActionsProps> = ({
    fullScreen,
    offline,
    article,
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
    onExitFullScreen,
    onFullScreen,
}) => {
    const { message } = App.useApp();
    const assetPickerOpenCacheKey = `${stateCacheKey}/assetPickerOpen`;
    const socialPreviewOpenCacheKey = `${stateCacheKey}/socialPreviewOpen`;
    const [assetPickerOpen, setAssetPickerOpenState] = useState(
        getCacheByKey<boolean>(assetPickerOpenCacheKey) === true
    );
    const [socialPreviewOpen, setSocialPreviewOpenState] = useState(
        getCacheByKey<boolean>(socialPreviewOpenCacheKey) === true
    );
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
            <Dropdown
                menu={{ items }}
                trigger={["click"]}
                getPopupContainer={() => containerRef.current as HTMLElement}
                placement="bottomRight"
            >
                <Button
                    type="text"
                    icon={
                        <EllipsisOutlined style={{ fontSize: getAppState().compactMode ? 18 : 24, display: "flex" }} />
                    }
                    style={{
                        border: 0,
                        display: "flex",
                        justifyContent: "center",
                        alignItems: "center",
                        cursor: "pointer",
                        color: "rgb(119, 119, 119)",
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
