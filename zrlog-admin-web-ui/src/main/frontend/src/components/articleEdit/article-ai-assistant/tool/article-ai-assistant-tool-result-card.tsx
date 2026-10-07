import { Avatar, Space, Typography } from "antd";
import { FunctionComponent, ReactNode, useId } from "react";
import AIIcon from "../../../../icons/AIProviderIcon";
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
    const titleId = useId();
    return (
        <section aria-labelledby={titleId} style={{ minWidth: 0, overflowWrap: "anywhere" }}>
            <Space size={theme.marginXS} style={{ display: "flex", marginBottom: theme.marginSM }}>
                <Avatar icon={<AIIcon name={aiProvider} />} size={32} />
                <Typography.Title id={titleId} level={3} style={{ margin: 0, fontSize: theme.fontSizeHeading4 }}>
                    {getAssistantToolLabel(tool)}
                </Typography.Title>
            </Space>
            {children}
        </section>
    );
};

export default ArticleAiAssistantToolResultCard;
