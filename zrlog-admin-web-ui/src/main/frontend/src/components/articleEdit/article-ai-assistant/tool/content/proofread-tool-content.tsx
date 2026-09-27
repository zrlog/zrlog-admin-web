import { List, Space, Typography } from "antd";
import { FunctionComponent } from "react";
import { getRes } from "../../../../../utils/constants";
import { AssistantToolPayload } from "../../article-ai-assistant.types";
import ArticleAiAssistantToolRefineActions from "../article-ai-assistant-tool-refine-actions";
import ArticleAiAssistantToolResultCard from "../article-ai-assistant-tool-result-card";
import { SpecificToolContentProps } from "../article-ai-assistant-tool-content.types";

type ProofreadToolPayload = Extract<AssistantToolPayload, { tool: "proofread" }>;

const ProofreadToolContent: FunctionComponent<SpecificToolContentProps<ProofreadToolPayload>> = ({
    aiProvider,
    offline,
    loadingKey,
    toolPayload,
    onRefine,
}) => {
    const proofreadResult = toolPayload.payload;
    const items = proofreadResult.items || [];
    return (
        <ArticleAiAssistantToolResultCard aiProvider={aiProvider} tool={toolPayload.tool}>
            <Space direction="vertical" size={8} style={{ width: "100%" }}>
                {proofreadResult.summary && (
                    <Typography.Paragraph style={{ marginBottom: 0 }}>{proofreadResult.summary}</Typography.Paragraph>
                )}
                {items.length === 0 ? (
                    <Typography.Text type="secondary">
                        {getRes().articleEdit.assistant.noProofreadIssues}
                    </Typography.Text>
                ) : (
                    <List
                        size="small"
                        dataSource={items}
                        renderItem={(item) => (
                            <List.Item>
                                <Space direction="vertical" size={4} style={{ width: "100%" }}>
                                    <Typography.Text>{item.issue}</Typography.Text>
                                    <Typography.Text type="secondary">{item.original}</Typography.Text>
                                    <Typography.Text copyable>{item.suggestion}</Typography.Text>
                                </Space>
                            </List.Item>
                        )}
                    />
                )}
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

export default ProofreadToolContent;
