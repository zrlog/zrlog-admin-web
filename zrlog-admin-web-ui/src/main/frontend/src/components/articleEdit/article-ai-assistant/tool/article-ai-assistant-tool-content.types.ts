import {ArticleChangeableValue} from "../../index.types";
import {AssistantTool, AssistantToolPayload} from "../article-ai-assistant.types";

export type ArticleAiAssistantToolContentCommonProps = {
    aiProvider: any;
    messageIndex: number;
    messageId?: string;
    offline: boolean;
    loadingKey?: string;
    applyingCoverMessageId?: string;
    selectedTitle?: string;
    currentMarkdown: string;
    onApplyValues: (cv: ArticleChangeableValue) => void;
    onSelectTitle: (messageIndex: number, title: string) => void;
    onRefine: (prompt: string, tool: AssistantTool) => void;
    onUpdateToolPayload: (messageIndex: number, toolPayload: AssistantToolPayload, persist?: boolean) => void;
    onApplyGeneratedCover?: (cover: {
        dataUrl: string;
        extension?: string;
        messageId?: string;
    }) => Promise<string | undefined>;
    onCoverApplyingChange: (coverApplyKey?: string) => void;
    onCropCover: (url: string) => void;
};

export type SpecificToolContentProps<T extends AssistantToolPayload> = ArticleAiAssistantToolContentCommonProps & {
    toolPayload: T;
};
