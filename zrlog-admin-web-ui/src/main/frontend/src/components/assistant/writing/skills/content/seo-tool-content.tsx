import Tag from "@zrlog/ui/antd/Tag";
import { FunctionComponent } from "react";
import { WritingSkillPayload } from "../../writing-assistant.types";
import { getSeoStatusColor, getSeoStatusText } from "../writing-skill-status";
import WritingSkillRefineActions from "../writing-skill-refine-actions";
import WritingSkillResultCard from "../writing-skill-result-card";
import { ToolReportDetail, ToolReportItem, ToolReportList, ToolReportSummary } from "../writing-skill-report";
import { SpecificToolContentProps } from "../writing-skill-content.types";

type SeoToolPayload = Extract<WritingSkillPayload, { tool: "seo" }>;

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
        <WritingSkillResultCard aiProvider={aiProvider} tool={toolPayload.tool}>
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
            <WritingSkillRefineActions
                tool={toolPayload.tool}
                offline={offline}
                loadingKey={loadingKey}
                onRefine={onRefine}
            />
        </WritingSkillResultCard>
    );
};

export default SeoToolContent;
