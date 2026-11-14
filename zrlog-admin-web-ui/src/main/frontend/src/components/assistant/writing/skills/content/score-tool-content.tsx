import { FunctionComponent } from "react";
import { WritingSkillPayload } from "../../writing-assistant.types";
import WritingSkillRefineActions from "../writing-skill-refine-actions";
import WritingSkillResultCard from "../writing-skill-result-card";
import { SpecificToolContentProps } from "../writing-skill-content.types";
import PublishCheckResult from "./publish-check-result";

type ScoreToolPayload = Extract<WritingSkillPayload, { tool: "score" | "publishCheck" }>;

const ScoreToolContent: FunctionComponent<SpecificToolContentProps<ScoreToolPayload>> = ({
    offline,
    loadingKey,
    toolPayload,
    onRefine,
}) => {
    return (
        <WritingSkillResultCard tool={toolPayload.tool}>
            <PublishCheckResult toolPayload={toolPayload} />
            <WritingSkillRefineActions
                tool={toolPayload.tool}
                offline={offline}
                loadingKey={loadingKey}
                onRefine={onRefine}
            />
        </WritingSkillResultCard>
    );
};

export default ScoreToolContent;
