import { Avatar, Space, Typography } from "antd";
import { FunctionComponent, ReactNode, useId } from "react";
import AIIcon from "../../../../icons/AIProviderIcon";
import { useTheme } from "antd-style";
import { WritingSkill } from "../writing-assistant.types";
import { getWritingSkillLabel } from "./writing-skills";

type WritingSkillResultCardProps = {
    aiProvider: any;
    tool: WritingSkill;
    children: ReactNode;
};

const WritingSkillResultCard: FunctionComponent<WritingSkillResultCardProps> = ({ aiProvider, tool, children }) => {
    const theme = useTheme();
    const titleId = useId();
    return (
        <section aria-labelledby={titleId} style={{ minWidth: 0, overflowWrap: "anywhere" }}>
            <Space size={theme.marginXS} style={{ display: "flex", marginBottom: theme.marginSM }}>
                <Avatar icon={<AIIcon name={aiProvider} />} size={32} />
                <Typography.Title id={titleId} level={3} style={{ margin: 0, fontSize: theme.fontSizeHeading4 }}>
                    {getWritingSkillLabel(tool)}
                </Typography.Title>
            </Space>
            {children}
        </section>
    );
};

export default WritingSkillResultCard;
