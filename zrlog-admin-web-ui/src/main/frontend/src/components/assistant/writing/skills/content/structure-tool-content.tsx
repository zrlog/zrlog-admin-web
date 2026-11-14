import Tag from "@zrlog/ui/antd/Tag";
import { FunctionComponent } from "react";
import { WritingSkillPayload } from "../../writing-assistant.types";
import { getSeoStatusColor, getSeoStatusText } from "../writing-skill-status";
import WritingSkillRefineActions from "../writing-skill-refine-actions";
import WritingSkillResultCard from "../writing-skill-result-card";
import { ToolReportDetail, ToolReportItem, ToolReportList, ToolReportSummary } from "../writing-skill-report";
import { SpecificToolContentProps } from "../writing-skill-content.types";

type StructureToolPayload = Extract<WritingSkillPayload, { tool: "structure" }>;

const StructureToolContent: FunctionComponent<SpecificToolContentProps<StructureToolPayload>> = ({
    offline,
    loadingKey,
    toolPayload,
    onRefine,
}) => {
    const structureResult = toolPayload.payload;
    const items = structureResult.items || [];
    return (
        <WritingSkillResultCard tool={toolPayload.tool}>
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
            <WritingSkillRefineActions
                tool={toolPayload.tool}
                offline={offline}
                loadingKey={loadingKey}
                onRefine={onRefine}
            />
        </WritingSkillResultCard>
    );
};

export default StructureToolContent;
