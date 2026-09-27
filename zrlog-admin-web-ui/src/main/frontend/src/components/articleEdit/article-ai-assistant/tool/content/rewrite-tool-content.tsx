import { Button, Space, Typography } from "antd";
import { FunctionComponent } from "react";
import { getRes } from "../../../../../utils/constants";
import MarkdownDiffView from "../../../markdown-diff-view";
import { AssistantToolPayload } from "../../article-ai-assistant.types";
import ArticleAiAssistantToolRefineActions from "../article-ai-assistant-tool-refine-actions";
import ArticleAiAssistantToolResultCard from "../article-ai-assistant-tool-result-card";
import { SpecificToolContentProps } from "../article-ai-assistant-tool-content.types";

type RewriteToolPayload = Extract<AssistantToolPayload, { tool: "rewrite" }>;

const RewriteToolContent: FunctionComponent<SpecificToolContentProps<RewriteToolPayload>> = ({
    aiProvider,
    currentMarkdown,
    messageIndex,
    offline,
    loadingKey,
    toolPayload,
    onApplyValues,
    onRefine,
    onUpdateToolPayload,
}) => {
    const assistantRes = getRes().articleEdit.assistant;
    const markdown = toolPayload.payload.markdown || "";
    const summary = toolPayload.payload.summary || "";
    const unchanged = markdown === currentMarkdown;
    const discarded = toolPayload.payload.discarded === true;
    return (
        <ArticleAiAssistantToolResultCard aiProvider={aiProvider} tool={toolPayload.tool}>
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
                                disabled={!markdown || unchanged}
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
                <ArticleAiAssistantToolRefineActions
                    tool={toolPayload.tool}
                    offline={offline}
                    loadingKey={loadingKey}
                    onRefine={onRefine}
                />
            </Space>
        </ArticleAiAssistantToolResultCard>
    );
};

export default RewriteToolContent;
