import { Button, Space } from "antd";
import { FunctionComponent } from "react";
import { ScissorOutlined } from "@ant-design/icons";
import { useTheme } from "antd-style";
import { getRes, tryAppendBackendServerUrl } from "../../../../../utils/constants";
import { AssistantToolPayload } from "../../article-ai-assistant.types";
import ArticleAiAssistantToolRefineActions from "../article-ai-assistant-tool-refine-actions";
import ArticleAiAssistantToolResultCard from "../article-ai-assistant-tool-result-card";
import { SpecificToolContentProps } from "../article-ai-assistant-tool-content.types";
import BackendImage from "../../../../../common/BackendImage";

type CoverToolPayload = Extract<AssistantToolPayload, { tool: "cover" }>;

const CoverToolContent: FunctionComponent<SpecificToolContentProps<CoverToolPayload>> = ({
    aiProvider,
    messageIndex,
    messageId,
    offline,
    loadingKey,
    applyingCoverMessageId,
    toolPayload,
    onApplyValues,
    onUpdateToolPayload,
    onApplyGeneratedCover,
    onCoverApplyingChange,
    onCropCover,
    onRefine,
}) => {
    const theme = useTheme();
    const url = toolPayload.payload.url || "";
    const coverApplyKey = messageId || `${messageIndex}`;
    return (
        <ArticleAiAssistantToolResultCard aiProvider={aiProvider} tool={toolPayload.tool}>
            {url && (
                <BackendImage
                    alt={getRes().articleEdit.cover}
                    src={url}
                    preview={false}
                    style={{
                        width: "100%",
                        display: "block",
                        borderRadius: theme.borderRadius,
                        marginBottom: 12,
                    }}
                />
            )}
            <Space wrap>
                {url && (
                    <Button
                        size="small"
                        type="primary"
                        loading={applyingCoverMessageId === coverApplyKey}
                        onClick={async () => {
                            try {
                                onCoverApplyingChange(coverApplyKey);
                                const finalUrl = await onApplyGeneratedCover?.({
                                    dataUrl: url,
                                    messageId,
                                });
                                if (finalUrl) {
                                    onUpdateToolPayload(
                                        messageIndex,
                                        {
                                            tool: "cover",
                                            payload: {
                                                url: finalUrl,
                                            },
                                        },
                                        false
                                    );
                                    onApplyValues({ thumbnail: finalUrl });
                                }
                            } finally {
                                onCoverApplyingChange(undefined);
                            }
                        }}
                    >
                        {getRes().articleEdit.assistant.apply}
                    </Button>
                )}
                {url && (
                    <Button
                        size="small"
                        icon={<ScissorOutlined />}
                        onClick={() => onCropCover(tryAppendBackendServerUrl(url))}
                    >
                        {getRes().articleEdit.assistant.crop}
                    </Button>
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

export default CoverToolContent;
