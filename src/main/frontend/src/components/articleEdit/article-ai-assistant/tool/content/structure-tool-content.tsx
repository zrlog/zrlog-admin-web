import { List, Space, Tag, Typography } from "antd";
import { FunctionComponent } from "react";
import { AssistantToolPayload } from "../../article-ai-assistant.types";
import { getSeoStatusColor, getSeoStatusText } from "../article-ai-assistant-tool-status";
import ArticleAiAssistantToolRefineActions from "../article-ai-assistant-tool-refine-actions";
import ArticleAiAssistantToolResultCard from "../article-ai-assistant-tool-result-card";
import { SpecificToolContentProps } from "../article-ai-assistant-tool-content.types";

type StructureToolPayload = Extract<AssistantToolPayload, { tool: "structure" }>;

const StructureToolContent: FunctionComponent<SpecificToolContentProps<StructureToolPayload>> = ({
    aiProvider,
    offline,
    loadingKey,
    toolPayload,
    onRefine,
}) => {
    const structureResult = toolPayload.payload;
    const items = structureResult.items || [];
    return (
        <ArticleAiAssistantToolResultCard aiProvider={aiProvider} tool={toolPayload.tool}>
            <Space direction="vertical" size={8} style={{ width: "100%" }}>
                {structureResult.summary && (
                    <Typography.Paragraph style={{ marginBottom: 0 }}>{structureResult.summary}</Typography.Paragraph>
                )}
                <List
                    size="small"
                    dataSource={items}
                    renderItem={(item) => (
                        <List.Item>
                            <Space direction="vertical" size={4} style={{ width: "100%" }}>
                                <Space wrap>
                                    <Typography.Text>{item.name}</Typography.Text>
                                    <Tag color={getSeoStatusColor(item.status)}>{getSeoStatusText(item.status)}</Tag>
                                </Space>
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

export default StructureToolContent;
