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
    offline,
    loadingKey,
    toolPayload,
    onApplyValues,
    onRefine,
}) => {
    const assistantRes = getRes().articleEdit.assistant;
    const markdown = toolPayload.payload.markdown || "";
    const summary = toolPayload.payload.summary || "";
    const unchanged = markdown === currentMarkdown;
    return (
        <ArticleAiAssistantToolResultCard aiProvider={aiProvider} tool={toolPayload.tool}>
            <Space direction="vertical" size={8} style={{ width: "100%" }}>
                {summary ? <Typography.Paragraph style={{ marginBottom: 0 }}>{summary}</Typography.Paragraph> : null}
                <MarkdownDiffView
                    beforeText={currentMarkdown}
                    afterText={markdown}
                    beforeLabel={assistantRes.rewriteCurrent}
                    afterLabel={assistantRes.rewriteCandidate}
                    maxHeight="36vh"
                />
                {unchanged ? <Typography.Text type="secondary">{assistantRes.rewriteUnchanged}</Typography.Text> : null}
                <Button
                    size="small"
                    type="primary"
                    disabled={!markdown || unchanged}
                    onClick={() => onApplyValues({ markdown })}
                >
                    {assistantRes.apply}
                </Button>
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
