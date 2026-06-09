import { Button, Space } from "antd";
import { FunctionComponent } from "react";
import { AssistantTool } from "../article-ai-assistant.types";
import { getToolRefineActions } from "./article-ai-assistant-tools";

type ArticleAiAssistantToolRefineActionsProps = {
    tool: AssistantTool;
    offline: boolean;
    loadingKey?: string;
    onRefine: (prompt: string, tool: AssistantTool) => void;
};

const ArticleAiAssistantToolRefineActions: FunctionComponent<ArticleAiAssistantToolRefineActionsProps> = ({
    tool,
    offline,
    loadingKey,
    onRefine,
}) => (
    <Space wrap>
        {getToolRefineActions(tool).map((action) => (
            <Button
                key={action.prompt}
                size="small"
                disabled={offline || Boolean(loadingKey)}
                onClick={() => onRefine(action.prompt, tool)}
            >
                {action.label}
            </Button>
        ))}
    </Space>
);

export default ArticleAiAssistantToolRefineActions;
