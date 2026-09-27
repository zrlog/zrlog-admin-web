import { List, Progress, Space, Tag, Typography } from "antd";
import { FunctionComponent } from "react";
import { useTheme } from "antd-style";
import { AssistantToolPayload } from "../../article-ai-assistant.types";
import { getScoreStrokeColor } from "../article-ai-assistant-score-color";
import { getSeoStatusColor, getSeoStatusText } from "../article-ai-assistant-tool-status";
import ArticleAiAssistantToolRefineActions from "../article-ai-assistant-tool-refine-actions";
import ArticleAiAssistantToolResultCard from "../article-ai-assistant-tool-result-card";
import { SpecificToolContentProps } from "../article-ai-assistant-tool-content.types";

type SeoToolPayload = Extract<AssistantToolPayload, { tool: "seo" }>;

const SeoToolContent: FunctionComponent<SpecificToolContentProps<SeoToolPayload>> = ({
    aiProvider,
    offline,
    loadingKey,
    toolPayload,
    onRefine,
}) => {
    const theme = useTheme();
    const seoResult = toolPayload.payload;
    const score = seoResult.score || 0;
    const items = seoResult.items || [];
    return (
        <ArticleAiAssistantToolResultCard aiProvider={aiProvider} tool={toolPayload.tool}>
            <Space direction="vertical" size={8} style={{ width: "100%" }}>
                <Progress percent={score} strokeColor={getScoreStrokeColor(score, theme)} />
                {seoResult.summary && (
                    <Typography.Paragraph style={{ marginBottom: 0 }}>{seoResult.summary}</Typography.Paragraph>
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

export default SeoToolContent;
