import { Avatar, Space, Typography } from "antd";
import { FunctionComponent, ReactNode } from "react";
import AIIcon from "@zrlog/editor/dist/ai/AIIcon";
import { useTheme } from "antd-style";
import { AssistantTool } from "../article-ai-assistant.types";
import { getAssistantToolLabel } from "./article-ai-assistant-tools";

type ArticleAiAssistantToolResultCardProps = {
    aiProvider: any;
    tool: AssistantTool;
    children: ReactNode;
};

const ArticleAiAssistantToolResultCard: FunctionComponent<ArticleAiAssistantToolResultCardProps> = ({
    aiProvider,
    tool,
    children,
}) => {
    const theme = useTheme();
    return (
        <div style={{ minWidth: 0, overflowWrap: "anywhere" }}>
            <Space size={theme.marginXS} style={{ display: "flex", marginBottom: theme.marginSM }}>
                <Avatar icon={<AIIcon name={aiProvider} />} size={32} />
                <Typography.Text type="secondary">{getAssistantToolLabel(tool)}</Typography.Text>
            </Space>
            {children}
        </div>
    );
};

export default ArticleAiAssistantToolResultCard;
