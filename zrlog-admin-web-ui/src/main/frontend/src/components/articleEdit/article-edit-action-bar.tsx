import SaveIcon from "@zrlog/ui/icons/save";
import SendIcon from "@zrlog/ui/icons/send";
import { isModuleEnabled } from "../../utils/module-capabilities";
import { hasAction } from "../../utils/account-access";
import { Button } from "antd";

import { getRes } from "../../utils/constants";
import { ArticleEditState, ArticleEntry } from "./index.types";
import { FunctionComponent, useEffect, useRef } from "react";
import { AIStateCache } from "@zrlog/editor/dist/ai/AIStateCache";
import { getAiDrawerOpen } from "@zrlog/editor/core/ai/AIDrawer";
import { getShortcutTitle, isMacLikeDevice, isTouchLikeDevice } from "./shortcut-utils";
import WritingAssistantButton, {
    getWritingAssistantDrawerOpen,
    WritingAssistantState,
} from "../assistant/writing/writing-assistant-button";
import useArticleEditorScreens from "./use-article-editor-screens";

type ArticleEditActionBarProps = {
    data: ArticleEditState;
    draftAiPending: boolean;
    writingAssistant?: WritingAssistantState;
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
    onAiDrawerSizeChange?: (newSize: number) => void;
    aiDrawerOpen?: boolean;
    onAiDrawerOpenChange?: (open: boolean) => void;
    aiDrawerWidth?: number | "default" | "large";
    aiStateCache?: AIStateCache;
};

const ArticleEditActionBar: FunctionComponent<ArticleEditActionBarProps> = ({
    data,
    draftAiPending,
    writingAssistant,
    offline,
    shortcutsDisabled = false,
    onSubmit,
    onRequestPublish,
    onPreview,
    onOpenSettings,
    onOpenVersionHistory,
    canOpenVersionHistory,
    getContainer,
    onAiDrawerSizeChange,
    aiDrawerOpen,
    onAiDrawerOpenChange,
    aiDrawerWidth,
    aiStateCache,
}) => {
    const enterBtnRef = useRef<HTMLAnchorElement | HTMLButtonElement>(null);
    const saveDraftBtnRef = useRef<HTMLButtonElement>(null);

    const screens = useArticleEditorScreens();

    useEffect(() => {
        const handleKeyPress = (event: KeyboardEvent) => {
            if (shortcutsDisabled) {
                return;
            }
            const isMac = isMacLikeDevice();
            const metaPressed = isMac ? event.metaKey : event.ctrlKey;
            const key = event.key.toLowerCase();

            if (getAiDrawerOpen() || getWritingAssistantDrawerOpen() || isTouchLikeDevice()) {
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
        <div
            style={{
                display: "flex",
                justifyContent: "end",
                gap: 8,
            }}
        >
            <Button
                ref={saveDraftBtnRef}
                type="default"
                aria-label={getRes().articleEdit.actions.saveAsDraft}
                title={getShortcutTitle(getRes().articleEdit.actions.saveAsDraft, {
                    ctrlOrCmd: true,
                    key: "S",
                })}
                icon={<SaveIcon />}
                loading={data.saving.rubbishSaving && !data.saving.autoSaving}
                disabled={
                    offline ||
                    (!hasAction("article.publish") && Boolean(data.article.logId) && !data.article.rubbish) ||
                    draftAiPending ||
                    (data.saving.rubbishSaving && !data.saving.autoSaving)
                }
                onClick={async () => await onSubmit(data.article, false, false, false)}
            />
            {isModuleEnabled("ai") && writingAssistant && (
                <WritingAssistantButton
                    {...writingAssistant}
                    getContainer={getContainer}
                    onAiDrawerSizeChange={onAiDrawerSizeChange}
                    open={aiDrawerOpen}
                    onOpenChange={onAiDrawerOpenChange}
                    aiDrawerWidth={aiDrawerWidth}
                    stateCache={aiStateCache}
                />
            )}
            {hasAction("article.publish") && (
                <Button
                    ref={enterBtnRef}
                    type="primary"
                    style={{ width: screens.sm ? 120 : undefined }}
                    title={getShortcutTitle(
                        data.article.privacy === true ? getRes().common.save : getRes().articleEdit.actions.release,
                        {
                            ctrlOrCmd: true,
                            key: "Enter",
                        }
                    )}
                    disabled={offline || draftAiPending || data.saving.releaseSaving}
                    icon={<SendIcon />}
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
                                ? getRes().common.save
                                : getRes().articleEdit.actions.release}
                        </span>
                    )}
                </Button>
            )}
        </div>
    );
};
export default ArticleEditActionBar;
