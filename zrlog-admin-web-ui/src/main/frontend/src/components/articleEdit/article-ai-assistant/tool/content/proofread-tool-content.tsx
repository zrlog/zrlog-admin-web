import { Typography } from "antd";
import { FunctionComponent } from "react";
import { getRes } from "../../../../../utils/constants";
import { AssistantToolPayload } from "../../article-ai-assistant.types";
import ArticleAiAssistantToolRefineActions from "../article-ai-assistant-tool-refine-actions";
import ArticleAiAssistantToolResultCard from "../article-ai-assistant-tool-result-card";
import {
    ToolReportDetail,
    ToolReportItem,
    ToolReportList,
    ToolReportSummary,
} from "../article-ai-assistant-tool-report";
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
            <ToolReportSummary summary={proofreadResult.summary} />
            {items.length === 0 ? (
                <Typography.Paragraph type="secondary">
                    {getRes().articleEdit.assistant.noProofreadIssues}
                </Typography.Paragraph>
            ) : (
                <ToolReportList>
                    {items.map((item, index) => (
                        <ToolReportItem key={index} title={item.issue}>
                            <ToolReportDetail label={getRes().articleEdit.assistant.reportOriginal} secondary>
                                {item.original}
                            </ToolReportDetail>
                            <ToolReportDetail label={getRes().articleEdit.assistant.reportSuggestion} copyable>
                                {item.suggestion}
                            </ToolReportDetail>
                        </ToolReportItem>
                    ))}
                </ToolReportList>
            )}
            <ArticleAiAssistantToolRefineActions
                tool={toolPayload.tool}
                offline={offline}
                loadingKey={loadingKey}
                onRefine={onRefine}
            />
        </ArticleAiAssistantToolResultCard>
    );
};

export default ProofreadToolContent;
