import { Tag } from "antd";
import { FunctionComponent } from "react";
import { AssistantToolPayload } from "../../article-ai-assistant.types";
import { getSeoStatusColor, getSeoStatusText } from "../article-ai-assistant-tool-status";
import ArticleAiAssistantToolRefineActions from "../article-ai-assistant-tool-refine-actions";
import ArticleAiAssistantToolResultCard from "../article-ai-assistant-tool-result-card";
import {
    ToolReportDetail,
    ToolReportItem,
    ToolReportList,
    ToolReportSummary,
} from "../article-ai-assistant-tool-report";
import { SpecificToolContentProps } from "../article-ai-assistant-tool-content.types";

type SeoToolPayload = Extract<AssistantToolPayload, { tool: "seo" }>;

const SeoToolContent: FunctionComponent<SpecificToolContentProps<SeoToolPayload>> = ({
    aiProvider,
    offline,
    loadingKey,
    toolPayload,
    onRefine,
}) => {
    const seoResult = toolPayload.payload;
    const score = seoResult.score || 0;
    const items = seoResult.items || [];
    return (
        <ArticleAiAssistantToolResultCard aiProvider={aiProvider} tool={toolPayload.tool}>
            <ToolReportSummary score={score} summary={seoResult.summary} />
            <ToolReportList>
                {items.map((item, index) => (
                    <ToolReportItem
                        key={index}
                        title={item.name}
                        extra={
                            <Tag color={getSeoStatusColor(item.status)} style={{ marginInlineEnd: 0 }}>
                                {getSeoStatusText(item.status)}
                            </Tag>
                        }
                    >
                        <ToolReportDetail>{item.suggestion}</ToolReportDetail>
                    </ToolReportItem>
                ))}
            </ToolReportList>
            <ArticleAiAssistantToolRefineActions
                tool={toolPayload.tool}
                offline={offline}
                loadingKey={loadingKey}
                onRefine={onRefine}
            />
        </ArticleAiAssistantToolResultCard>
    );
};

export default SeoToolContent;
