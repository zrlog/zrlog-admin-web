import { Typography } from "antd";
import { FunctionComponent, ReactNode, useId } from "react";
import { useTheme } from "antd-style";
import { AssistantTool } from "../article-ai-assistant.types";
import { getAssistantToolLabel } from "./article-ai-assistant-tools";

type ArticleAiAssistantToolResultCardProps = {
    tool: AssistantTool;
    children: ReactNode;
};

const ArticleAiAssistantToolResultCard: FunctionComponent<ArticleAiAssistantToolResultCardProps> = ({
    tool,
    children,
}) => {
    const theme = useTheme();
    const titleId = useId();
    return (
        <section aria-labelledby={titleId} style={{ minWidth: 0, overflowWrap: "anywhere" }}>
            <Typography.Title
                id={titleId}
                level={3}
                style={{ margin: 0, marginBottom: theme.marginSM, fontSize: theme.fontSizeHeading4 }}
            >
                {getAssistantToolLabel(tool)}
            </Typography.Title>
            {children}
        </section>
    );
};

export default ArticleAiAssistantToolResultCard;
