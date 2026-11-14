import Typography from "@zrlog/ui/antd/Typography";
import { Button, Space } from "antd";
import { FunctionComponent } from "react";
import { getRes } from "../../../../../utils/constants";
import { WritingSkillPayload } from "../../writing-assistant.types";
import WritingSkillRefineActions from "../writing-skill-refine-actions";
import WritingSkillResultCard from "../writing-skill-result-card";
import { SpecificToolContentProps } from "../writing-skill-content.types";

type AliasToolPayload = Extract<WritingSkillPayload, { tool: "alias" }>;

const AliasToolContent: FunctionComponent<SpecificToolContentProps<AliasToolPayload>> = ({
    offline,
    loadingKey,
    toolPayload,
    onApplyValues,
    applyDisabled,
    onRefine,
}) => {
    const alias = toolPayload.payload.alias || "";
    return (
        <WritingSkillResultCard tool={toolPayload.tool}>
            <Space direction="vertical" style={{ width: "100%" }}>
                <Typography.Text copyable>{alias}</Typography.Text>
                <Button
                    size="small"
                    type="primary"
                    disabled={applyDisabled || !alias}
                    onClick={() => onApplyValues({ alias })}
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

export default AliasToolContent;
