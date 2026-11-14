import { Typography } from "antd";
import { FunctionComponent, ReactNode, useId } from "react";
import { useTheme } from "antd-style";
import { WritingSkill } from "../writing-assistant.types";
import { getWritingSkillLabel } from "./writing-skills";

type WritingSkillResultCardProps = {
    tool: WritingSkill;
    children: ReactNode;
};

const WritingSkillResultCard: FunctionComponent<WritingSkillResultCardProps> = ({ tool, children }) => {
    const theme = useTheme();
    const titleId = useId();
    return (
        <section aria-labelledby={titleId} style={{ minWidth: 0, overflowWrap: "anywhere" }}>
            <Typography.Title
                id={titleId}
                level={3}
                style={{ margin: 0, marginBottom: theme.marginSM, fontSize: theme.fontSizeHeading4 }}
            >
                {getWritingSkillLabel(tool)}
            </Typography.Title>
            {children}
        </section>
    );
};

export default WritingSkillResultCard;
