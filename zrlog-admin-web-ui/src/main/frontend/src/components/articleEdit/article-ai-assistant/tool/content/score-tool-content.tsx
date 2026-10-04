import { FunctionComponent } from "react";
import { AssistantToolPayload } from "../../article-ai-assistant.types";
import ArticleAiAssistantToolRefineActions from "../article-ai-assistant-tool-refine-actions";
import ArticleAiAssistantToolResultCard from "../article-ai-assistant-tool-result-card";
import { SpecificToolContentProps } from "../article-ai-assistant-tool-content.types";
import PublishCheckResult from "./publish-check-result";

type ScoreToolPayload = Extract<AssistantToolPayload, { tool: "score" | "publishCheck" }>;

const ScoreToolContent: FunctionComponent<SpecificToolContentProps<ScoreToolPayload>> = ({
    aiProvider,
    offline,
    loadingKey,
    toolPayload,
    onRefine,
}) => {
    return (
        <ArticleAiAssistantToolResultCard aiProvider={aiProvider} tool={toolPayload.tool}>
            <PublishCheckResult toolPayload={toolPayload} />
            <ArticleAiAssistantToolRefineActions
                tool={toolPayload.tool}
                offline={offline}
                loadingKey={loadingKey}
                onRefine={onRefine}
            />
        </ArticleAiAssistantToolResultCard>
    );
};

export default ScoreToolContent;
