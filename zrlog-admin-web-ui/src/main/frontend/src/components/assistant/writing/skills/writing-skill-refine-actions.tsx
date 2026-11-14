import { Button, Space } from "antd";
import { FunctionComponent } from "react";
import { WritingSkill } from "../writing-assistant.types";
import { getToolRefineActions } from "./writing-skills";

type WritingSkillRefineActionsProps = {
    tool: WritingSkill;
    offline: boolean;
    loadingKey?: string;
    onRefine: (prompt: string, tool: WritingSkill) => void;
};

const WritingSkillRefineActions: FunctionComponent<WritingSkillRefineActionsProps> = ({
    tool,
    offline,
    loadingKey,
    onRefine,
}) => (
    <Space wrap>
        {getToolRefineActions(tool).map((action) => (
            <Button
                key={action.prompt}
                size="small"
                disabled={offline || Boolean(loadingKey)}
                onClick={() => onRefine(action.prompt, tool)}
            >
                {action.label}
            </Button>
        ))}
    </Space>
);

export default WritingSkillRefineActions;
