import { Avatar, Space, Typography } from "antd";
import { useTheme } from "antd-style";
import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import RobotIcon from "@zrlog/ui/icons/robot";
import HtmlPreviewPanel from "@zrlog/editor/core/editor/html-preview-panel";
import { markdownToHtmlSyncWithCallback } from "@zrlog/editor/dist/editor/utils/marked-utils";
import { getAppState } from "../../../base/ConfigProviderApp";
import { getRealRouteUrl, getRes } from "../../../utils/constants";

/** A standard session message from the assistant, without claiming a model generated it. */
export default function TaskResultMessage({
    content,
    taskId,
    runId,
}: {
    content: string;
    taskId: string;
    runId: string;
}) {
    const theme = useTheme();
    const [html, setHtml] = useState("");
    useEffect(() => {
        let active = true;
        const initial = markdownToHtmlSyncWithCallback(
            content,
            (value) => {
                if (active) setHtml(value);
            },
            { linkPreview: false }
        );
        setHtml(initial);
        return () => {
            active = false;
        };
    }, [content]);
    return (
        <div>
            <Space style={{ marginBottom: theme.marginSM }}>
                <Avatar icon={<RobotIcon />} />
                <Typography.Text>{getRes().assistant.title}</Typography.Text>
            </Space>
            <HtmlPreviewPanel htmlContent={html} dark={getAppState().dark} />
            <Link
                to={getRealRouteUrl(
                    `/assistant?taskId=${encodeURIComponent(taskId)}&runId=${encodeURIComponent(runId)}`
                )}
            >
                {getRes().assistant.executionDetails}
            </Link>
        </div>
    );
}
