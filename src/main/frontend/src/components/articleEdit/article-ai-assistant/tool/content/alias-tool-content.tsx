import { Button, Space, Typography } from "antd";
import { FunctionComponent } from "react";
import { getRes } from "../../../../../utils/constants";
import { AssistantToolPayload } from "../../article-ai-assistant.types";
import ArticleAiAssistantToolRefineActions from "../article-ai-assistant-tool-refine-actions";
import ArticleAiAssistantToolResultCard from "../article-ai-assistant-tool-result-card";
import { SpecificToolContentProps } from "../article-ai-assistant-tool-content.types";

type AliasToolPayload = Extract<AssistantToolPayload, { tool: "alias" }>;

const AliasToolContent: FunctionComponent<SpecificToolContentProps<AliasToolPayload>> = ({
    aiProvider,
    offline,
    loadingKey,
    toolPayload,
    onApplyValues,
    onRefine,
}) => {
    const alias = toolPayload.payload.alias || "";
    return (
        <ArticleAiAssistantToolResultCard aiProvider={aiProvider} tool={toolPayload.tool}>
            <Space direction="vertical" style={{ width: "100%" }}>
                <Typography.Text copyable>{alias}</Typography.Text>
                <Button size="small" type="primary" disabled={!alias} onClick={() => onApplyValues({ alias })}>
                    {getRes().articleEdit.assistant.apply}
                </Button>
                <ArticleAiAssistantToolRefineActions
                    tool={toolPayload.tool}
                    offline={offline}
                    loadingKey={loadingKey}
                    onRefine={onRefine}
                />
            </Space>
        </ArticleAiAssistantToolResultCard>
    );
};

export default AliasToolContent;
