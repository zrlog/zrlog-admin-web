import { Button, Space, Typography } from "antd";
import { FunctionComponent } from "react";
import { getRes } from "../../../../../utils/constants";
import { AssistantToolPayload } from "../../article-ai-assistant.types";
import ArticleAiAssistantToolRefineActions from "../article-ai-assistant-tool-refine-actions";
import ArticleAiAssistantToolResultCard from "../article-ai-assistant-tool-result-card";
import { SpecificToolContentProps } from "../article-ai-assistant-tool-content.types";

type DigestToolPayload = Extract<AssistantToolPayload, { tool: "digest" }>;

const DigestToolContent: FunctionComponent<SpecificToolContentProps<DigestToolPayload>> = ({
    aiProvider,
    offline,
    loadingKey,
    toolPayload,
    onApplyValues,
    onRefine,
}) => {
    const digest = toolPayload.payload.digest || "";
    return (
        <ArticleAiAssistantToolResultCard aiProvider={aiProvider} tool={toolPayload.tool}>
            <Space direction="vertical" style={{ width: "100%" }}>
                <Typography.Paragraph style={{ marginBottom: 0 }}>{digest}</Typography.Paragraph>
                <Button size="small" type="primary" disabled={!digest} onClick={() => onApplyValues({ digest })}>
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

export default DigestToolContent;
