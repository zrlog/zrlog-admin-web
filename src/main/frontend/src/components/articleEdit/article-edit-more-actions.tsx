import { Button, Dropdown, Grid, Modal } from "antd";
import {
    EllipsisOutlined,
    EyeOutlined,
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
import { getAiDrawerOpen } from "@editor/dist/src/ai/AIDrawer";
import { getAppState } from "../../base/ConfigProviderApp";
import FileManagerPicker from "../file-manager/picker";
import ArticleSocialPreviewDrawer from "./article-social-preview-drawer";
import { SocialPreview } from "./index.types";

type ArticleEditMoreActionsProps = {
    fullScreen: boolean;
    offline: boolean;
    logId?: number;
    socialPreview?: SocialPreview;
    currentVersion: number;
    axiosInstance: any;
    containerRef: RefObject<HTMLDivElement>;
    getFullScreenElement: () => HTMLElement | null;
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
    logId,
    socialPreview,
    currentVersion,
    axiosInstance,
    containerRef,
    getFullScreenElement,
    versionDrawerOpen,
    onPreview,
    onRollback,
    onVersionOpenChange,
    onInsertMarkdownFromAsset,
    onExitFullScreen,
    onFullScreen,
}) => {
    const [assetPickerOpen, setAssetPickerOpen] = useState(false);
    const [socialPreviewOpen, setSocialPreviewOpen] = useState(false);
    const screens = Grid.useBreakpoint();
    const narrow = screens.md !== true;
    const assetPickerWidth = narrow ? "100vw" : screens.lg ? 860 : 720;
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
