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
            <ToolReportSummary summary={readerQuestionsResult.summary} />
            <ToolReportList>
                {items.map((item, index) => (
                    <ToolReportItem key={index} title={item.question}>
                        <ToolReportDetail label={getRes().articleEdit.assistant.reportReason} secondary>
                            {item.reason}
                        </ToolReportDetail>
                        <ToolReportDetail label={getRes().articleEdit.assistant.reportSuggestion}>
                            {item.suggestion}
                        </ToolReportDetail>
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

export default QuestionsToolContent;
