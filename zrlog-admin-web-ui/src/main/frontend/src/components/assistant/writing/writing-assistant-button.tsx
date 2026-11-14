import useWritingConversation from "./use-writing-conversation";
import WritingAssistantDrawer from "./writing-assistant-drawer";
import { useAssistantConversation, AssistantConversationProps } from "../use-assistant-conversation";
import { getAppState } from "../../../base/ConfigProviderApp";
import { addToCache, getCacheByKey } from "../../../utils/cache";
import { Button } from "antd";
import RobotIcon from "@zrlog/ui/icons/robot";
import { FunctionComponent, useEffect, useMemo, useState } from "react";
import AIButton, { AIStateCache, getAIButtonDrawerOpen } from "@zrlog/editor/core/ai/AIButton";
import AIIcon from "../../../icons/AIProviderIcon";
import useArticleEditorScreens from "../../articleEdit/use-article-editor-screens";
import { getRealRouteUrl, getRes } from "../../../utils/constants";
import { useTheme } from "antd-style";
import { getShortcutTitle, isTouchLikeDevice } from "../../articleEdit/shortcut-utils";

type WritingAssistantButtonProps = AssistantConversationProps & {
    getContainer?: () => HTMLElement;
    aiDrawerWidth?: number | "default" | "large";
    stateCache?: AIStateCache;
    open?: boolean;
    onOpenChange?: (open: boolean) => void;
    onAiDrawerSizeChange?: (newSize: number) => void;
};

const AI_ASSISTANT_SHORTCUT = {
    alt: true,
    shift: true,
    key: "A",
};

const AI_ASSISTANT_STATE_CACHE_KEY_PREFIX = "ai/chat/state";
let writingAssistantDrawerOpen = false;
export const getWritingAssistantDrawerOpen = () => writingAssistantDrawerOpen || getAIButtonDrawerOpen();

const WritingAssistantButton: FunctionComponent<WritingAssistantButtonProps> = ({
    data,
    draftAiSaveGate,
    offline,
    axiosInstance,
    onAiMessagesChange,
    onArticleUpdated,
    onApplyValues,
    getSkillContextRevision,
    onApplyGeneratedCover,
    getContainer,
    aiDrawerWidth,
    stateCache,
    open,
    onOpenChange,
    onAiDrawerSizeChange,
}) => {
    const [innerOpen, setInnerOpen] = useState(() =>
        new URLSearchParams(window.location.search).has("assistantSession")
    );
    const screens = useArticleEditorScreens();
    const theme = useTheme();
    const mergedOpen = open ?? innerOpen;
    const updateOpen = (nextOpen: boolean) => {
        setInnerOpen(nextOpen);
        onOpenChange?.(nextOpen);
    };
    const sessions = useWritingConversation(
        axiosInstance,
        data.article.logId || 0,
        mergedOpen && !offline,
        data.aiMessages
    );
    const assistantData = { ...data, aiMessages: sessions.messages };
    const assistantConfig = useAssistantConversation({
        context: { articleId: data.article.logId || 0, sessionId: sessions.sessionId },
        data: assistantData,
        draftAiSaveGate,
        offline: offline || sessions.loading || Boolean(sessions.error),
        axiosInstance,
        onAiMessagesChange: (messages, articleId) => {
            sessions.change(messages);
            if (!sessions.sessionId) onAiMessagesChange?.(messages, articleId);
        },
        onArticleUpdated,
        onApplyValues,
        getSkillContextRevision,
        onApplyGeneratedCover,
    });
    const aiStateCacheKey = `${AI_ASSISTANT_STATE_CACHE_KEY_PREFIX}/${
        data.article.logId ? data.article.logId : "draft"
    }/${sessions.sessionId || "default"}`;
    const aiStateCache = useMemo<AIStateCache>(
        () => ({
            key: aiStateCacheKey,
            read: getCacheByKey,
            write: addToCache,
        }),
        [aiStateCacheKey]
    );
    const aiConfigured = data.aiConfigured === true;

    useEffect(() => {
        writingAssistantDrawerOpen = mergedOpen;
        return () => {
            writingAssistantDrawerOpen = false;
        };
    }, [mergedOpen]);

    useEffect(() => {
        const handleKeyPress = (event: KeyboardEvent) => {
            if (!aiConfigured || mergedOpen || isTouchLikeDevice()) {
                return;
            }
            if (event.altKey && event.shiftKey && event.key.toLowerCase() === "a") {
                event.preventDefault();
                updateOpen(true);
            }
        };

        window.addEventListener("keydown", handleKeyPress);
        return () => {
            window.removeEventListener("keydown", handleKeyPress);
        };
    }, [aiConfigured, mergedOpen, onOpenChange]);

    const trigger = (
        <Button
            type="primary"
            className="btn"
            style={{
                width: screens.sm ? 120 : undefined,
                background: `linear-gradient(135deg, ${theme.colorInfo}, ${theme.colorPrimary})`,
                border: "none",
            }}
            onClick={aiConfigured ? () => updateOpen(true) : undefined}
            icon={aiConfigured ? <AIIcon name={data.aiProvider} /> : <RobotIcon />}
            title={getShortcutTitle(getRes().assistant.writingAssistant, AI_ASSISTANT_SHORTCUT)}
            aria-label={getRes().assistant.writingAssistant}
        >
            {screens.sm && <span>{getRes().assistant.writingAssistant}</span>}
        </Button>
    );

    if (!aiConfigured) {
        return (
            <AIButton dark={getAppState().dark} configUrl={getRealRouteUrl("/website/ai")}>
                {trigger}
            </AIButton>
        );
    }
    return (
        <>
            {trigger}
            <WritingAssistantDrawer
                data={assistantData}
                sessions={sessions}
                config={assistantConfig}
                open={mergedOpen}
                onClose={() => updateOpen(false)}
                getContainer={getContainer}
                width={aiDrawerWidth}
                onSizeChange={onAiDrawerSizeChange}
                stateCache={stateCache ?? aiStateCache}
            />
        </>
    );
};

export default WritingAssistantButton;
