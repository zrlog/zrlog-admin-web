import { Button, Space } from "antd";
import { FunctionComponent } from "react";
import { getRes } from "../../../../../utils/constants";
import Tags from "../../../../../common/Tags";
import { AssistantToolPayload } from "../../article-ai-assistant.types";
import ArticleAiAssistantToolRefineActions from "../article-ai-assistant-tool-refine-actions";
import ArticleAiAssistantToolResultCard from "../article-ai-assistant-tool-result-card";
import { SpecificToolContentProps } from "../article-ai-assistant-tool-content.types";

type TagsToolPayload = Extract<AssistantToolPayload, { tool: "tags" }>;

const TagsToolContent: FunctionComponent<SpecificToolContentProps<TagsToolPayload>> = ({
    aiProvider,
    messageIndex,
    offline,
    loadingKey,
    toolPayload,
    onApplyValues,
    onUpdateToolPayload,
    onRefine,
}) => {
    const tags = toolPayload.payload.tags || [];
    return (
        <ArticleAiAssistantToolResultCard aiProvider={aiProvider} tool={toolPayload.tool}>
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
                    disabled={tags.length === 0}
                    onClick={() => onApplyValues({ keywords: tags.join(",") })}
                >
                    {getRes().articleEdit.assistant.apply}
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

export default TagsToolContent;
