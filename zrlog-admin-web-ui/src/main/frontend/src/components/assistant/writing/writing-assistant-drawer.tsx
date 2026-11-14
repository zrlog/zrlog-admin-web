import type useWritingConversation from "./use-writing-conversation";
import AssistantConversationView from "../chat/assistant-conversation-view";
import { useUiApp } from "@zrlog/ui/feedback";
import DeleteIcon from "@zrlog/ui/icons/delete";
import DownloadIcon from "@zrlog/ui/icons/download";
import ChevronDownIcon from "@zrlog/ui/icons/chevron-down";
import { Alert, Button, Drawer, Dropdown, Typography } from "antd";

import { useEffect, useRef, useState } from "react";
import { useTheme } from "antd-style";
import AIIcon from "../../../icons/AIProviderIcon";
import { AIStateCache, getAIStateCacheKey } from "@zrlog/editor/dist/ai/AIStateCache";
import { getRes } from "../../../utils/constants";
import useArticleEditorScreens from "../../articleEdit/use-article-editor-screens";
import type { useAssistantConversation, AssistantConversationData } from "../use-assistant-conversation";

type Props = {
    data: AssistantConversationData;
    config: ReturnType<typeof useAssistantConversation>;
    sessions: ReturnType<typeof useWritingConversation>;
    open: boolean;
    onClose: () => void;
    getContainer?: () => HTMLElement;
    width?: number | "default" | "large";
    onSizeChange?: (width: number) => void;
    stateCache: AIStateCache;
};

export default function WritingAssistantDrawer({
    data,
    config,
    sessions,
    open,
    onClose,
    getContainer,
    width,
    onSizeChange,
    stateCache,
}: Props) {
    const { modal } = useUiApp();
    const theme = useTheme();
    const screens = useArticleEditorScreens();
    const scrollRef = useRef<HTMLDivElement>(null);
    const followMessages = useRef(true);
    const [ready, setReady] = useState(false);
    const [drawerWidth, setDrawerWidth] = useState<number>(736);
    const widthKey = getAIStateCacheKey(stateCache, "drawerWidth");
    const scrollKey = getAIStateCacheKey(stateCache, "scrollTop");
    const cacheRef = useRef(stateCache);
    cacheRef.current = stateCache;
    const actions = config.conversationActions;
    const res = getRes().articleEdit.assistant;

    useEffect(() => {
        const cached = cacheRef.current.read(widthKey) ?? width;
        setDrawerWidth(typeof cached === "number" ? cached : cached === "default" ? 378 : 736);
    }, [widthKey, width]);

    useEffect(() => {
        if (!open || !ready || !scrollRef.current) return;
        const scroll = scrollRef.current;
        const cached = cacheRef.current.read(scrollKey);
        scroll.scrollTop = typeof cached === "number" ? cached : scroll.scrollHeight;
        followMessages.current = scroll.scrollHeight - scroll.scrollTop - scroll.clientHeight < 48;
    }, [open, ready, scrollKey]);

    useEffect(() => {
        if (open && ready && followMessages.current && scrollRef.current) {
            scrollRef.current.scrollTop = scrollRef.current.scrollHeight;
        }
    }, [open, ready, config.messages]);

    const saveScroll = () => {
        const scroll = scrollRef.current;
        if (!scroll) return;
        followMessages.current = scroll.scrollHeight - scroll.scrollTop - scroll.clientHeight < 48;
        stateCache.write(scrollKey, scroll.scrollTop);
    };

    return (
        <Drawer
            title={
                <div style={{ display: "flex", alignItems: "center", gap: theme.marginXS, minWidth: 0 }}>
                    <Dropdown
                        trigger={["click"]}
                        placement="bottomLeft"
                        // Escape the editor card's clipping while staying in the fullscreen top layer.
                        getPopupContainer={(trigger) =>
                            (trigger.ownerDocument.fullscreenElement as HTMLElement) || trigger.ownerDocument.body
                        }
                        menu={{
                            items: [
                                {
                                    key: "export",
                                    icon: <DownloadIcon />,
                                    label: res.exportAiMessages,
                                    disabled: actions.disabled || actions.exporting || actions.clearing,
                                    onClick: () => void actions.onExport(),
                                },
                                {
                                    key: "clear",
                                    icon: <DeleteIcon />,
                                    label: res.clearAiMessages,
                                    danger: true,
                                    disabled: actions.disabled || actions.exporting || actions.clearing,
                                    onClick: () =>
                                        modal.confirm({
                                            title: res.clearAiMessagesConfirmTitle,
                                            content: res.clearAiMessagesConfirmDescription,
                                            okText: res.clearAiMessages,
                                            cancelText: getRes().common.cancel,
                                            okButtonProps: { danger: true },
                                            getContainer,
                                            onOk: actions.onClear,
                                        }),
                                },
                            ],
                        }}
                    >
                        <Button
                            type="text"
                            icon={<AIIcon name={data.aiProvider} />}
                            aria-label={getRes().assistant.writingAssistant}
                            title={res.conversationActions}
                            loading={actions.exporting || actions.clearing}
                            style={{ flexShrink: 0, paddingInline: theme.paddingXS }}
                        >
                            {getRes().assistant.writingAssistant}
                            <ChevronDownIcon style={{ fontSize: theme.fontSizeSM }} />
                        </Button>
                    </Dropdown>
                    {data.article.title && (
                        <Typography.Text
                            type="secondary"
                            ellipsis
                            title={data.article.title}
                            style={{
                                flex: "1 1 0",
                                minWidth: 0,
                                overflow: "hidden",
                                whiteSpace: "nowrap",
                                textOverflow: "ellipsis",
                            }}
                        >
                            {data.article.title}
                        </Typography.Text>
                    )}
                </div>
            }
            open={open}
            closable={{ placement: "end" }}
            onClose={() => {
                saveScroll();
                onClose();
            }}
            afterOpenChange={setReady}
            getContainer={getContainer}
            size={screens.sm ? drawerWidth : "100%"}
            resizable={
                screens.sm
                    ? {
                          onResize: (nextWidth) => {
                              setDrawerWidth(nextWidth);
                              stateCache.write(widthKey, nextWidth);
                              onSizeChange?.(nextWidth);
                          },
                      }
                    : false
            }
            styles={{
                header: { padding: theme.paddingSM },
                title: { minWidth: 0 },
                body: { padding: 0, overflow: "hidden" },
            }}
        >
            <div style={{ display: "flex", flexDirection: "column", minHeight: 0, height: "100%" }}>
                {sessions.error && (
                    <Alert
                        type="error"
                        title={sessions.error}
                        action={<Button onClick={sessions.retry}>{getRes().common.retry}</Button>}
                    />
                )}
                <div style={{ flex: 1, minHeight: 0 }}>
                    <AssistantConversationView
                        config={config}
                        aiProvider={data.aiProvider}
                        scrollRef={scrollRef}
                        onScroll={saveScroll}
                    />
                </div>
            </div>
        </Drawer>
    );
}
