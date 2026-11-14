import { Button, Space } from "antd";
import { FunctionComponent } from "react";
import { getRes } from "../../../../../utils/constants";
import Tags from "../../../../../common/Tags";
import { WritingSkillPayload } from "../../writing-assistant.types";
import WritingSkillRefineActions from "../writing-skill-refine-actions";
import WritingSkillResultCard from "../writing-skill-result-card";
import { SpecificToolContentProps } from "../writing-skill-content.types";

type TagsToolPayload = Extract<WritingSkillPayload, { tool: "tags" }>;

const TagsToolContent: FunctionComponent<SpecificToolContentProps<TagsToolPayload>> = ({
    messageIndex,
    offline,
    loadingKey,
    toolPayload,
    onApplyValues,
    applyDisabled,
    onUpdateToolPayload,
    onRefine,
}) => {
    const tags = toolPayload.payload.tags || [];
    return (
        <WritingSkillResultCard tool={toolPayload.tool}>
            <Space direction="vertical" size={8} style={{ width: "100%" }}>
                <Tags
                    keywords={tags.join(",")}
                    closeable
                    onClose={(e, tag) => {
                        e.preventDefault();
                        onUpdateToolPayload(messageIndex, {
                            tool: "tags",
                            payload: {
                                tags: tags.filter((item) => item !== tag),
                            },
                        });
                    }}
                />
                <Button
                    size="small"
                    type="primary"
                    disabled={applyDisabled || tags.length === 0}
                    onClick={() => onApplyValues({ keywords: tags.join(",") })}
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

export default TagsToolContent;
