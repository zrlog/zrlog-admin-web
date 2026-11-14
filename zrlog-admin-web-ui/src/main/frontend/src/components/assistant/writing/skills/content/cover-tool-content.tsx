import CutIcon from "@zrlog/ui/icons/cut";
import { Button, Space } from "antd";
import { FunctionComponent } from "react";

import { useTheme } from "antd-style";
import { getRes, tryAppendBackendServerUrl } from "../../../../../utils/constants";
import { WritingSkillPayload } from "../../writing-assistant.types";
import WritingSkillRefineActions from "../writing-skill-refine-actions";
import WritingSkillResultCard from "../writing-skill-result-card";
import { SpecificToolContentProps } from "../writing-skill-content.types";
import BackendImage from "../../../../../common/BackendImage";

type CoverToolPayload = Extract<WritingSkillPayload, { tool: "cover" }>;

const CoverToolContent: FunctionComponent<SpecificToolContentProps<CoverToolPayload>> = ({
    aiProvider,
    messageIndex,
    messageId,
    offline,
    loadingKey,
    applyingCoverMessageId,
    toolPayload,
    onApplyValues,
    applyDisabled,
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
        <WritingSkillResultCard aiProvider={aiProvider} tool={toolPayload.tool}>
            {url && (
                <BackendImage
                    alt={getRes().articleEdit.cover}
                    src={url}
                    width="100%"
                    preview={{
                        getContainer: () => (document.fullscreenElement as HTMLElement) || document.body,
                    }}
                    styles={{
                        root: { display: "block", marginBottom: theme.marginSM },
                        cover: { borderRadius: theme.borderRadius },
                    }}
                    style={{
                        display: "block",
                        borderRadius: theme.borderRadius,
                    }}
                />
            )}
            <Space wrap>
                {url && (
                    <Button
                        size="small"
                        type="primary"
                        disabled={applyDisabled}
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
                        icon={<CutIcon />}
                        disabled={applyDisabled}
                        onClick={() => onCropCover(tryAppendBackendServerUrl(url))}
                    >
                        {getRes().articleEdit.assistant.crop}
                    </Button>
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

export default CoverToolContent;
