import { Button, Grid } from "antd";
import { SaveOutlined, SendOutlined } from "@ant-design/icons";
import { getRes } from "../../utils/constants";
import { ArticleChangeableValue, ArticleEditState, ArticleEntry } from "./index.types";
import { FunctionComponent, useEffect, useRef } from "react";
import styled from "styled-components";
import { AIContent } from "@editor/dist/ai/AIContentItem";
import { AIStateCache } from "@editor/dist/ai/AIStateCache";
import { useAxiosBaseInstance } from "../../base/AppBase";
import { getAiDrawerOpen } from "@editor/dist/ai/AIDrawer";
import { getShortcutTitle, isMacLikeDevice, isTouchLikeDevice } from "./shortcut-utils";
import ArticleAiAssistantButton, {
    getArticleAiAssistantDrawerOpen,
} from "./article-ai-assistant/article-ai-assistant-button";
import { DraftAiSaveGate } from "./draft-ai-save-gate";

type ArticleEditActionBarProps = {
    data: ArticleEditState;
    draftAiPending: boolean;
    draftAiSaveGate: DraftAiSaveGate;
    fullScreen: boolean;
    offline: boolean;
    shortcutsDisabled?: boolean;
    onSubmit: (article: ArticleEntry, release: boolean, preview: boolean, autoSave: boolean) => Promise<boolean>;
    onRequestPublish: () => void;
    onPreview?: () => Promise<void>;
    onOpenSettings?: () => void;
    onOpenVersionHistory?: () => void;
    canOpenVersionHistory?: boolean;
    getContainer?: () => HTMLElement;
    onAiMessagesChange?: (messages: AIContent[], articleId?: number) => void;
    onAiDrawerSizeChange?: (newSize: number) => void;
    aiDrawerOpen?: boolean;
    onAiDrawerOpenChange?: (open: boolean) => void;
    aiDrawerWidth?: number | "default" | "large";
    aiStateCache?: AIStateCache;
    onApplyAiValues: (cv: ArticleChangeableValue) => void;
    onApplyGeneratedCover?: (cover: { dataUrl: string; extension?: string }) => Promise<string | undefined>;
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
    draftAiPending,
    draftAiSaveGate,
    offline,
    shortcutsDisabled = false,
    fullScreen,
    onSubmit,
    onRequestPublish,
    onPreview,
    onOpenSettings,
    onOpenVersionHistory,
    canOpenVersionHistory,
    getContainer,
    onAiMessagesChange,
    onAiDrawerSizeChange,
    aiDrawerOpen,
    onAiDrawerOpenChange,
    aiDrawerWidth,
    aiStateCache,
    onApplyAiValues,
    onApplyGeneratedCover,
}) => {
    const enterBtnRef = useRef<HTMLAnchorElement | HTMLButtonElement>(null);
    const saveDraftBtnRef = useRef<HTMLButtonElement>(null);

    const { useBreakpoint } = Grid;
    const screens = useBreakpoint();

    const axiosInstance = useAxiosBaseInstance(getContainer);

    useEffect(() => {
        const handleKeyPress = (event: KeyboardEvent) => {
            if (shortcutsDisabled) {
                return;
            }
            const isMac = isMacLikeDevice();
            const metaPressed = isMac ? event.metaKey : event.ctrlKey;
            const key = event.key.toLowerCase();

            if (getAiDrawerOpen() || getArticleAiAssistantDrawerOpen() || isTouchLikeDevice()) {
                return;
            }

            if (metaPressed && key === "s") {
                event.preventDefault();
                if (
                    saveDraftBtnRef.current &&
                    !offline &&
                    !draftAiPending &&
                    !(data.saving.rubbishSaving && !data.saving.autoSaving)
                ) {
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
                if (enterBtnRef.current && !draftAiPending && !getAiDrawerOpen()) {
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
        draftAiPending,
        offline,
        onPreview,
        onOpenSettings,
        onOpenVersionHistory,
        shortcutsDisabled,
    ]);

    return (
        <StyledActionBar
            style={{
                display: "flex",
                justifyContent: "end",
                gap: 8,
            }}
        >
            <ArticleAiAssistantButton
                data={data}
                draftAiSaveGate={draftAiSaveGate}
                offline={offline}
                axiosInstance={axiosInstance}
                getContainer={getContainer}
                onAiMessagesChange={onAiMessagesChange}
                onAiDrawerSizeChange={onAiDrawerSizeChange}
                open={aiDrawerOpen}
                onOpenChange={onAiDrawerOpenChange}
                aiDrawerWidth={aiDrawerWidth}
                stateCache={aiStateCache}
                onApplyValues={onApplyAiValues}
                onApplyGeneratedCover={onApplyGeneratedCover}
            />

            <Button
                ref={saveDraftBtnRef}
                className={"btn"}
                type={fullScreen ? "default" : "dashed"}
                title={getShortcutTitle(getRes().articleEdit.actions.saveAsDraft, {
                    ctrlOrCmd: true,
                    key: "S",
                })}
                icon={<SaveOutlined hidden={data.saving.rubbishSaving} />}
                disabled={offline || draftAiPending || (data.saving.rubbishSaving && !data.saving.autoSaving)}
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
                disabled={offline || draftAiPending || data.saving.releaseSaving}
                icon={<SendOutlined />}
                onClick={async () => {
                    if (data.article.privacy === true) {
                        await onSubmit(data.article, true, false, false);
                        return;
                    }
                    onRequestPublish();
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
