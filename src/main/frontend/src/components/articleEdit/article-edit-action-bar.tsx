import { Button, Grid } from "antd";
import { EyeOutlined, SaveOutlined, SendOutlined } from "@ant-design/icons";
import { getRealRouteUrl, getRes } from "../../utils/constants";
import { ArticleEditState, ArticleEntry } from "./index.types";
import { FunctionComponent, useEffect, useRef } from "react";
import styled from "styled-components";
import { getAppState } from "../../base/ConfigProviderApp";
import { AIContent } from "@editor/dist/src/ai/AIContentItem";
import AIButton from "@editor/dist/src/ai/AIButton";
import AIIcon from "@editor/dist/src/ai/AIIcon";
import { useAxiosBaseInstance } from "../../base/AppBase";
import { getAiDrawerOpen } from "@editor/dist/src/ai/AIDrawer";
import { getEditorUser } from "../../utils/helpers";
import { getShortcutTitle, isMacLikeDevice, isTouchLikeDevice } from "./shortcut-utils";

type ArticleEditActionBarProps = {
    data: ArticleEditState;
    fullScreen: boolean;
    offline: boolean;
    onSubmit: (article: ArticleEntry, release: boolean, preview: boolean, autoSave: boolean) => Promise<boolean>;
    onPreview?: () => Promise<void>;
    onOpenSettings?: () => void;
    onOpenVersionHistory?: () => void;
    canOpenVersionHistory?: boolean;
    getContainer?: () => HTMLElement;
    onAiMessagesChange?: (messages: AIContent[]) => void;
    onAiDrawerSizeChange?: (newSize: number) => void;
    aiDrawerWidth?: number | "default" | "large";
    previewUrl?: string | undefined;
};

const StyledActionBar = styled(`div`)`
    .btn {
        width: 120px;
    }

    @media screen and (max-width: 576px) {
        .btn {
            width: 40px;
        }
    }
`;

const ArticleEditActionBar: FunctionComponent<ArticleEditActionBarProps> = ({
    data,
    offline,
    fullScreen,
    onSubmit,
    onPreview,
    onOpenSettings,
    onOpenVersionHistory,
    canOpenVersionHistory,
    getContainer,
    onAiMessagesChange,
    onAiDrawerSizeChange,
    aiDrawerWidth,
    previewUrl,
}) => {
    const enterBtnRef = useRef<HTMLAnchorElement | HTMLButtonElement>(null);
    const saveDraftBtnRef = useRef<HTMLButtonElement>(null);

    const { useBreakpoint } = Grid;
    const screens = useBreakpoint();

    const axiosInstance = useAxiosBaseInstance(getContainer);

    useEffect(() => {
        const handleKeyPress = (event: KeyboardEvent) => {
            const isMac = isMacLikeDevice();
            const metaPressed = isMac ? event.metaKey : event.ctrlKey;
            const key = event.key.toLowerCase();

            if (getAiDrawerOpen() || isTouchLikeDevice()) {
                return;
            }

            if (metaPressed && key === "s") {
                event.preventDefault();
                if (saveDraftBtnRef.current && !offline && !(data.saving.rubbishSaving && !data.saving.autoSaving)) {
                    saveDraftBtnRef.current.click();
                }
                return;
            }

            if (metaPressed && event.shiftKey && event.key === "Enter") {
                event.preventDefault();
                if (onPreview && !offline && !(data.saving.previewIng && !data.saving.autoSaving)) {
                    void onPreview();
                }
                return;
            }

            if (metaPressed && event.key === "Enter") {
                event.preventDefault();
                if (enterBtnRef.current && !getAiDrawerOpen()) {
                    enterBtnRef.current.click();
                }
                return;
            }

            if (event.altKey && event.shiftKey && key === "s") {
                event.preventDefault();
                onOpenSettings?.();
                return;
            }

            if (event.altKey && event.shiftKey && key === "v" && canOpenVersionHistory) {
                event.preventDefault();
                onOpenVersionHistory?.();
            }
        };

        // 绑定键盘事件
        window.addEventListener("keydown", handleKeyPress);

        // 在组件卸载时移除事件监听
        return () => {
            window.removeEventListener("keydown", handleKeyPress);
        };
    }, [
        canOpenVersionHistory,
        data.article,
        data.saving.autoSaving,
        data.saving.rubbishSaving,
        offline,
        onPreview,
        onOpenSettings,
        onOpenVersionHistory,
        previewUrl,
    ]);

    return (
        <StyledActionBar style={{ display: "flex", justifyContent: "end", gap: 8 }}>
            <AIButton
                aiProvider={data.aiProvider}
                apiUri={"/api/admin/article/ai"}
                input={""}
                aiMessages={data.aiMessages ? data.aiMessages : []}
                subject={data.article.title}
                sessionId={data.article.logId ? data.article.logId : 0}
                getContainer={getContainer}
                onAiMessagesChange={onAiMessagesChange}
                axiosInstance={axiosInstance}
                user={getEditorUser()}
                drawerWidth={aiDrawerWidth}
                dark={getAppState().dark}
                configUrl={getRealRouteUrl("/website/ai")}
                onSizeChange={onAiDrawerSizeChange}
            >
                <Button
                    className={"btn"}
                    type={"primary"}
                    icon={<AIIcon name={data.aiProvider} />}
                    style={{
                        background: `linear-gradient(135deg, #6253e1, ${getAppState().colorPrimary})`,
                        border: "none",
                    }}
                >
                    {screens.sm && <span>{getRes().websiteAi.label}</span>}
                </Button>
            </AIButton>

            <Button
                ref={saveDraftBtnRef}
                className={"btn"}
                type={fullScreen ? "default" : "dashed"}
                title={getShortcutTitle(getRes().articleEdit.actions.saveAsDraft, {
                    ctrlOrCmd: true,
                    key: "S",
                })}
                icon={<SaveOutlined hidden={data.saving.rubbishSaving} />}
                disabled={offline || (data.saving.rubbishSaving && !data.saving.autoSaving)}
                onClick={async () => await onSubmit(data.article, false, false, false)}
            >
                {screens.sm && (
                    <span>
                        {data.saving.rubbishSaving
                            ? getRes().articleEdit.saving
                            : getRes().articleEdit.actions.saveAsDraft}
                    </span>
                )}
            </Button>
            <Button
                className={"btn"}
                type="dashed"
                icon={<EyeOutlined />}
                title={getShortcutTitle(getRes().preview, {
                    ctrlOrCmd: true,
                    shift: true,
                    key: "Enter",
                })}
                disabled={offline || (data.saving.previewIng && !data.saving.autoSaving)}
                style={{ display: fullScreen ? "none" : "flex" }}
                onClick={() => void onPreview?.()}
            >
                {screens.sm && getRes().preview}
            </Button>
            <Button
                ref={enterBtnRef}
                type="primary"
                className={"btn"}
                title={getShortcutTitle(
                    data.article.privacy === true
                        ? getRes().articleEdit.actions.save
                        : getRes().articleEdit.actions.release,
                    {
                        ctrlOrCmd: true,
                        key: "Enter",
                    }
                )}
                disabled={offline}
                icon={data.saving.releaseSaving ? <></> : <SendOutlined />}
                loading={data.saving.releaseSaving}
                onClick={async () => {
                    await onSubmit(data.article, true, false, false);
                }}
            >
                {screens.sm && (
                    <span>
                        {data.article.privacy === true
                            ? getRes().articleEdit.actions.save
                            : getRes().articleEdit.actions.release}
                    </span>
                )}
            </Button>
        </StyledActionBar>
    );
};
export default ArticleEditActionBar;
