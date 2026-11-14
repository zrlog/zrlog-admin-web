import type { AIContent } from "@zrlog/editor/core/ai/AIContentItem";
import HtmlPreviewPanel from "@zrlog/editor/core/editor/html-preview-panel";
import { markdownToHtmlSyncWithCallback } from "@zrlog/editor/dist/editor/utils/marked-utils";
import Typography from "@zrlog/ui/antd/Typography";
import LoadingIcon from "@zrlog/ui/icons/loading";
import { useTheme } from "antd-style";
import { useEffect, useState } from "react";
import { getAppState } from "../../../base/ConfigProviderApp";
import { getRes } from "../../../utils/constants";
import { getEditorUser } from "../../../utils/helpers";

export default function AssistantMessageContent({ content }: { content: AIContent }) {
    const theme = useTheme();
    const [html, setHtml] = useState("");
    useEffect(() => {
        if (content.role === "user" || !content.content) {
            setHtml("");
            return;
        }
        let active = true;
        const initialHtml = markdownToHtmlSyncWithCallback(content.content, (value) => {
            if (active) setHtml(value);
        });
        setHtml(initialHtml);
        return () => {
            active = false;
        };
    }, [content.role, content.content]);

    if (content.role === "user") {
        return (
            <div
                role="group"
                aria-label={getEditorUser().nickname}
                data-message-role="user"
                style={{ display: "flex", justifyContent: "flex-end", minWidth: 0 }}
            >
                <div
                    style={{
                        maxWidth: "90%",
                        boxSizing: "border-box",
                        padding: `${theme.paddingXS}px ${theme.paddingSM}px`,
                        borderRadius: theme.borderRadiusLG,
                        background: theme.colorFillSecondary,
                        whiteSpace: "pre-wrap",
                        overflowWrap: "anywhere",
                    }}
                >
                    {content.content}
                </div>
            </div>
        );
    }

    return (
        <div role="group" aria-label={getRes().assistant.title} data-message-role="assistant" style={{ minWidth: 0 }}>
            {content.content ? (
                <>
                    <HtmlPreviewPanel htmlContent={html} dark={getAppState().dark} />
                    <Typography.Paragraph
                        copyable={{ text: content.content }}
                        style={{ paddingTop: theme.paddingXS, marginBottom: 0 }}
                    />
                </>
            ) : (
                <Typography.Text type="secondary">{getRes().articleEdit.knowledge.waitingForResponse}</Typography.Text>
            )}
            {content.thinking && <LoadingIcon />}
        </div>
    );
}
