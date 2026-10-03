import { useEffect, useState } from "react";
import { Collapse, Space, Typography, theme } from "antd";
import { LoadingOutlined } from "@ant-design/icons";
import HtmlPreviewPanel from "@zrlog/editor/dist/editor/html-preview-panel";
import { markdownToHtmlSyncWithCallback } from "@zrlog/editor/dist/editor/utils/marked-utils";
import { getAppState } from "../../../base/ConfigProviderApp";
import { getRes } from "../../../utils/constants";

const ArticleAiReasoning = ({
    content,
    thinking = false,
    status,
}: {
    content?: string;
    thinking?: boolean;
    status?: string;
}) => {
    const { token } = theme.useToken();
    const [html, setHtml] = useState("");

    useEffect(() => {
        if (!content) {
            setHtml("");
            return;
        }
        let active = true;
        const initialHtml = markdownToHtmlSyncWithCallback(
            content,
            (nextHtml) => {
                if (active) setHtml(nextHtml);
            },
            { linkPreview: false }
        );
        setHtml(initialHtml);
        return () => {
            active = false;
        };
    }, [content]);

    if (!content && !status) return null;
    const progress = status ? (
        <Space role="status">
            <LoadingOutlined />
            <Typography.Text type="secondary">{status}</Typography.Text>
        </Space>
    ) : null;
    if (!content) return <div style={{ marginBottom: token.marginXS }}>{progress}</div>;
    return (
        <div style={{ marginBottom: token.marginXS }}>
            <Collapse
                size="small"
                ghost
                defaultActiveKey={thinking || status ? ["reasoning"] : []}
                items={[
                    {
                        key: "reasoning",
                        label: (
                            <Space>
                                {getRes().articleEdit.assistant.reasoningProcess}
                                {progress}
                            </Space>
                        ),
                        children: (
                            <HtmlPreviewPanel
                                htmlContent={html}
                                dark={getAppState().dark}
                                style={{ color: token.colorTextSecondary }}
                            />
                        ),
                    },
                ]}
            />
        </div>
    );
};

export default ArticleAiReasoning;
