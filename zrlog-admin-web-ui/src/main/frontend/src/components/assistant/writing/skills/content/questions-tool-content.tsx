import { FunctionComponent } from "react";
import { getRes } from "../../../../../utils/constants";
import { WritingSkillPayload } from "../../writing-assistant.types";
import WritingSkillRefineActions from "../writing-skill-refine-actions";
import WritingSkillResultCard from "../writing-skill-result-card";
import { ToolReportDetail, ToolReportItem, ToolReportList, ToolReportSummary } from "../writing-skill-report";
import { SpecificToolContentProps } from "../writing-skill-content.types";

type QuestionsToolPayload = Extract<WritingSkillPayload, { tool: "questions" }>;

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
        <WritingSkillResultCard aiProvider={aiProvider} tool={toolPayload.tool}>
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
            <WritingSkillRefineActions
                tool={toolPayload.tool}
                offline={offline}
                loadingKey={loadingKey}
                onRefine={onRefine}
            />
        </WritingSkillResultCard>
    );
};

export default QuestionsToolContent;
