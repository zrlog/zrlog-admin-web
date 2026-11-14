import { Button, Space, Typography } from "antd";
import { FunctionComponent } from "react";
import { getRes } from "../../../../../utils/constants";
import { WritingSkillPayload } from "../../writing-assistant.types";
import WritingSkillRefineActions from "../writing-skill-refine-actions";
import WritingSkillResultCard from "../writing-skill-result-card";
import { SpecificToolContentProps } from "../writing-skill-content.types";

type DigestToolPayload = Extract<WritingSkillPayload, { tool: "digest" }>;

const DigestToolContent: FunctionComponent<SpecificToolContentProps<DigestToolPayload>> = ({
    offline,
    loadingKey,
    toolPayload,
    onApplyValues,
    applyDisabled,
    onRefine,
}) => {
    const digest = toolPayload.payload.digest || "";
    return (
        <WritingSkillResultCard tool={toolPayload.tool}>
            <Space direction="vertical" style={{ width: "100%" }}>
                <Typography.Paragraph style={{ marginBottom: 0 }}>{digest}</Typography.Paragraph>
                <Button
                    size="small"
                    type="primary"
                    disabled={applyDisabled || !digest}
                    onClick={() => onApplyValues({ digest })}
                >
                    {getRes().articleEdit.assistant.apply}
                </Button>
                <WritingSkillRefineActions
                    tool={toolPayload.tool}
                    offline={offline}
                    loadingKey={loadingKey}
                    onRefine={onRefine}
                />
            </Space>
        </WritingSkillResultCard>
    );
};

export default DigestToolContent;
