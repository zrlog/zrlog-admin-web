import { List, Space, Typography } from "antd";
import { FunctionComponent } from "react";
import { AssistantToolPayload } from "../../article-ai-assistant.types";
import ArticleAiAssistantToolRefineActions from "../article-ai-assistant-tool-refine-actions";
import ArticleAiAssistantToolResultCard from "../article-ai-assistant-tool-result-card";
import { SpecificToolContentProps } from "../article-ai-assistant-tool-content.types";

type QuestionsToolPayload = Extract<AssistantToolPayload, { tool: "questions" }>;

const QuestionsToolContent: FunctionComponent<SpecificToolContentProps<QuestionsToolPayload>> = ({
    aiProvider,
    offline,
    loadingKey,
    toolPayload,
    onRefine,
}) => {
    const readerQuestionsResult = toolPayload.payload;
    const items = readerQuestionsResult.items || [];
    return (
        <ArticleAiAssistantToolResultCard aiProvider={aiProvider} tool={toolPayload.tool}>
            <Space direction="vertical" size={8} style={{ width: "100%" }}>
                {readerQuestionsResult.summary && (
                    <Typography.Paragraph style={{ marginBottom: 0 }}>
                        {readerQuestionsResult.summary}
                    </Typography.Paragraph>
                )}
                <List
                    size="small"
                    dataSource={items}
                    renderItem={(item) => (
                        <List.Item>
                            <Space direction="vertical" size={4} style={{ width: "100%" }}>
                                <Typography.Text>{item.question}</Typography.Text>
                                <Typography.Text type="secondary">{item.reason}</Typography.Text>
                                <Typography.Text type="secondary">{item.suggestion}</Typography.Text>
                            </Space>
                        </List.Item>
                    )}
                />
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

export default QuestionsToolContent;
