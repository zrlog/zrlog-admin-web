import { Avatar, Space, Typography } from "antd";
import { FunctionComponent, ReactNode } from "react";
import AIIcon from "@editor/dist/src/ai/AIIcon";
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
        <div>
            <Space size={8} style={{ paddingBottom: 8 }}>
                <Avatar icon={<AIIcon name={aiProvider} />} size={32} />
                <Typography.Text type="secondary">{getAssistantToolLabel(tool)}</Typography.Text>
            </Space>
            <div
                style={{
                    background: theme.colorFillQuaternary,
                    borderRadius: theme.borderRadius,
                    padding: 12,
                }}
            >
                {children}
            </div>
        </div>
    );
};

export default ArticleAiAssistantToolResultCard;
