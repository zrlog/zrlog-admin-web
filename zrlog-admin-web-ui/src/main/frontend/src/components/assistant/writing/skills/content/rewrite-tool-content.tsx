import { Button, Space, Typography } from "antd";
import { FunctionComponent } from "react";
import { getRes } from "../../../../../utils/constants";
import MarkdownDiffView from "../../../../articleEdit/markdown-diff-view";
import { WritingSkillPayload } from "../../writing-assistant.types";
import WritingSkillRefineActions from "../writing-skill-refine-actions";
import WritingSkillResultCard from "../writing-skill-result-card";
import { SpecificToolContentProps } from "../writing-skill-content.types";

type RewriteToolPayload = Extract<WritingSkillPayload, { tool: "rewrite" }>;

const RewriteToolContent: FunctionComponent<SpecificToolContentProps<RewriteToolPayload>> = ({
    currentMarkdown,
    messageIndex,
    offline,
    loadingKey,
    toolPayload,
    onApplyValues,
    applyDisabled,
    onRefine,
    onUpdateToolPayload,
}) => {
    const assistantRes = getRes().articleEdit.assistant;
    const markdown = toolPayload.payload.markdown || "";
    const summary = toolPayload.payload.summary || "";
    const unchanged = markdown === currentMarkdown;
    const discarded = toolPayload.payload.discarded === true;
    return (
        <WritingSkillResultCard tool={toolPayload.tool}>
            <Space direction="vertical" size={8} style={{ width: "100%" }}>
                {discarded ? (
                    <Typography.Text type="secondary">{assistantRes.rewriteRejected}</Typography.Text>
                ) : (
                    <>
                        {summary ? (
                            <Typography.Paragraph style={{ marginBottom: 0 }}>{summary}</Typography.Paragraph>
                        ) : null}
                        <MarkdownDiffView
                            beforeText={currentMarkdown}
                            afterText={markdown}
                            beforeLabel={assistantRes.rewriteCurrent}
                            afterLabel={assistantRes.rewriteCandidate}
                            maxHeight="36vh"
                        />
                        {unchanged ? (
                            <Typography.Text type="secondary">{assistantRes.rewriteUnchanged}</Typography.Text>
                        ) : null}
                        <Space wrap>
                            <Button
                                size="small"
                                type="primary"
                                disabled={applyDisabled || !markdown || unchanged}
                                onClick={() => onApplyValues({ markdown })}
                            >
                                {assistantRes.apply}
                            </Button>
                            <Button
                                size="small"
                                onClick={() =>
                                    onUpdateToolPayload(
                                        messageIndex,
                                        {
                                            tool: "rewrite",
                                            payload: {
                                                ...toolPayload.payload,
                                                discarded: true,
                                            },
                                        },
                                        false
                                    )
                                }
                            >
                                {assistantRes.rejectCandidate}
                            </Button>
                        </Space>
                    </>
                )}
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

export default RewriteToolContent;
