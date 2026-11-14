import Typography from "@zrlog/ui/antd/Typography";
import { FunctionComponent } from "react";
import { getRes } from "../../../../../utils/constants";
import { WritingSkillPayload } from "../../writing-assistant.types";
import WritingSkillRefineActions from "../writing-skill-refine-actions";
import WritingSkillResultCard from "../writing-skill-result-card";
import { ToolReportDetail, ToolReportItem, ToolReportList, ToolReportSummary } from "../writing-skill-report";
import { SpecificToolContentProps } from "../writing-skill-content.types";

type ProofreadToolPayload = Extract<WritingSkillPayload, { tool: "proofread" }>;

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
        <WritingSkillResultCard aiProvider={aiProvider} tool={toolPayload.tool}>
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
            <WritingSkillRefineActions
                tool={toolPayload.tool}
                offline={offline}
                loadingKey={loadingKey}
                onRefine={onRefine}
            />
        </WritingSkillResultCard>
    );
};

export default ProofreadToolContent;
