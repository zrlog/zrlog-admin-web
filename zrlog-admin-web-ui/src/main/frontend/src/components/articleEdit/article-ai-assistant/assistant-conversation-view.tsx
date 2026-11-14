import type { ReactNode, RefObject, UIEventHandler } from "react";
import { useTheme } from "antd-style";
import AIContentItem from "@zrlog/editor/core/ai/AIContentItem";
import { getAppState } from "../../../base/ConfigProviderApp";
import { getEditorUser } from "../../../utils/helpers";
import type { AIProviderType } from "../../../type";
import type { useArticleAiAssistantConfig } from "./article-ai-assistant-button";

/** The existing conversation content, shared by the assistant page and writing surfaces. */
export default function AssistantConversationView({
    config,
    aiProvider,
    leading,
    scrollRef,
    onScroll,
    selectedText,
}: {
    config: ReturnType<typeof useArticleAiAssistantConfig>;
    aiProvider: AIProviderType;
    leading?: ReactNode;
    scrollRef?: RefObject<HTMLDivElement>;
    onScroll?: UIEventHandler<HTMLDivElement>;
    selectedText?: string;
}) {
    const theme = useTheme();
    return (
        <>
            <div style={{ display: "flex", flexDirection: "column", height: "100%", minHeight: 0 }}>
                <div
                    ref={scrollRef}
                    onScroll={onScroll}
                    style={{ flex: 1, minHeight: 0, overflowY: "auto", padding: theme.paddingSM }}
                >
                    <div style={{ maxWidth: config.contentMaxWidth, margin: "0 auto", width: "100%" }}>
                        {leading}
                        {config.messages.map((content, index) => {
                            const node = config.renderMessage({
                                content,
                                index,
                                defaultNode: (
                                    <AIContentItem
                                        content={content}
                                        aiProvider={aiProvider}
                                        user={getEditorUser()}
                                        dark={getAppState().dark}
                                    />
                                ),
                            });
                            return node === null ? null : (
                                <div
                                    key={(content as { messageId?: string }).messageId || `message-${index}`}
                                    style={{ paddingBottom: theme.paddingSM }}
                                >
                                    {node}
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
                        {config.renderFooter(selectedText)}
                    </div>
                </div>
            </div>
            {config.overlays}
        </>
    );
}
