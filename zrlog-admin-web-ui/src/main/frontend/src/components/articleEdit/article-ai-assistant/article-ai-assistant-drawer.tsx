import { App, Button, Drawer, Dropdown, Typography } from "antd";
import { DeleteOutlined, DownloadOutlined, DownOutlined } from "@ant-design/icons";
import { useEffect, useRef, useState } from "react";
import { useTheme } from "antd-style";
import AIContentItem from "@zrlog/editor/dist/ai/AIContentItem";
import AIIcon from "@zrlog/editor/dist/ai/AIIcon";
import { AIStateCache, getAIStateCacheKey } from "@zrlog/editor/dist/ai/AIStateCache";
import { getAppState } from "../../../base/ConfigProviderApp";
import { getRes } from "../../../utils/constants";
import { getEditorUser } from "../../../utils/helpers";
import { ArticleEditState } from "../index.types";
import useArticleEditorScreens from "../use-article-editor-screens";
import type { useArticleAiAssistantConfig } from "./article-ai-assistant-button";

type Props = {
    data: ArticleEditState;
    config: ReturnType<typeof useArticleAiAssistantConfig>;
    open: boolean;
    onClose: () => void;
    getContainer?: () => HTMLElement;
    width?: number | "default" | "large";
    onSizeChange?: (width: number) => void;
    stateCache: AIStateCache;
};

export default function ArticleAiAssistantDrawer({
    data,
    config,
    open,
    onClose,
    getContainer,
    width,
    onSizeChange,
    stateCache,
}: Props) {
    const { modal } = App.useApp();
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
                                    icon: <DownloadOutlined />,
                                    label: res.exportAiMessages,
                                    disabled: actions.disabled || actions.exporting || actions.clearing,
                                    onClick: () => void actions.onExport(),
                                },
                                {
                                    key: "clear",
                                    icon: <DeleteOutlined />,
                                    label: res.clearAiMessages,
                                    danger: true,
                                    disabled: actions.disabled || actions.exporting || actions.clearing,
                                    onClick: () =>
                                        modal.confirm({
                                            title: res.clearAiMessagesConfirmTitle,
                                            content: res.clearAiMessagesConfirmDescription,
                                            okText: getRes().confirm,
                                            cancelText: getRes().cancel,
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
                            aria-label={getRes().websiteAi.label}
                            title={res.conversationActions}
                            loading={actions.exporting || actions.clearing}
                            style={{ flexShrink: 0, paddingInline: theme.paddingXS }}
                        >
                            {getRes().websiteAi.label}
                            <DownOutlined style={{ fontSize: theme.fontSizeSM }} />
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
            <div style={{ display: "flex", flexDirection: "column", height: "100%" }}>
                <div
                    ref={scrollRef}
                    onScroll={saveScroll}
                    style={{ flex: 1, minHeight: 0, overflowY: "auto", padding: theme.paddingSM }}
                >
                    <div style={{ maxWidth: config.contentMaxWidth, margin: "0 auto", width: "100%" }}>
                        {config.messages.map((content, index) => {
                            const message = config.renderMessage({
                                content,
                                index,
                                defaultNode: (
                                    <AIContentItem
                                        content={content}
                                        aiProvider={data.aiProvider}
                                        user={getEditorUser()}
                                        dark={getAppState().dark}
                                    />
                                ),
                            });
                            return message === null ? null : (
                                <div key={index} style={{ paddingBottom: theme.paddingSM }}>
                                    {message}
                                </div>
                            );
                        })}
                    </div>
                </div>
                <div
                    style={{
                        borderTop: `${theme.lineWidth}px ${theme.lineType} ${theme.colorBorderSecondary}`,
                        padding: theme.paddingSM,
                    }}
                >
                    <div style={{ maxWidth: config.contentMaxWidth, margin: "0 auto", width: "100%" }}>
                        {config.renderFooter()}
                    </div>
                </div>
            </div>
            {config.overlays}
        </Drawer>
    );
}
