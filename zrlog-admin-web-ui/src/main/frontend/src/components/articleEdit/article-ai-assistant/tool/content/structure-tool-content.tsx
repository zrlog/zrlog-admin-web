import Tag from "@zrlog/ui/antd/Tag";
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
            <ToolReportSummary summary={structureResult.summary} />
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

export default StructureToolContent;
