import { Button, Radio, Space } from "antd";
import { FunctionComponent } from "react";
import { useTheme } from "antd-style";
import { getRes } from "../../../../../utils/constants";
import { AssistantToolPayload } from "../../article-ai-assistant.types";
import ArticleAiAssistantToolRefineActions from "../article-ai-assistant-tool-refine-actions";
import ArticleAiAssistantToolResultCard from "../article-ai-assistant-tool-result-card";
import { SpecificToolContentProps } from "../article-ai-assistant-tool-content.types";

type TitleToolPayload = Extract<AssistantToolPayload, { tool: "title" }>;

const TitleToolContent: FunctionComponent<SpecificToolContentProps<TitleToolPayload>> = ({
    aiProvider,
    messageIndex,
    offline,
    loadingKey,
    selectedTitle,
    toolPayload,
    onApplyValues,
    applyDisabled,
    onSelectTitle,
    onRefine,
}) => {
    const theme = useTheme();
    const titles = toolPayload.payload.titles || [];
    const currentSelectedTitle = selectedTitle || toolPayload.payload.selectedTitle || titles[0] || "";
    if (titles.length === 0) {
        return null;
    }
    return (
        <ArticleAiAssistantToolResultCard aiProvider={aiProvider} tool={toolPayload.tool}>
            <Space direction="vertical" style={{ width: "100%" }}>
                <Radio.Group
                    aria-label={getRes().articleEdit.assistant.titleSuggestion}
                    disabled={applyDisabled}
                    value={currentSelectedTitle}
                    onChange={(e) => onSelectTitle(messageIndex, e.target.value)}
                    style={{ width: "100%" }}
                >
                    <Space direction="vertical" size={theme.marginXXS} style={{ width: "100%" }}>
                        {titles.map((title) => (
                            <Radio
                                key={title}
                                value={title}
                                style={{
                                    width: "100%",
                                    marginInlineEnd: 0,
                                    padding: theme.paddingXS,
                                    borderRadius: theme.borderRadiusSM,
                                    background:
                                        title === currentSelectedTitle ? theme.colorFillSecondary : "transparent",
                                    overflowWrap: "anywhere",
                                }}
                            >
                                {title}
                            </Radio>
                        ))}
                    </Space>
                </Radio.Group>
                <Button
                    size="small"
                    type="primary"
                    disabled={applyDisabled || !currentSelectedTitle}
                    onClick={() => onApplyValues({ title: currentSelectedTitle })}
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

export default TitleToolContent;
