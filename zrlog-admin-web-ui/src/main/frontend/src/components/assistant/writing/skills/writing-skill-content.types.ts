import { ArticleChangeableValue } from "../../../articleEdit/index.types";
import { WritingSkill, WritingSkillPayload } from "../writing-assistant.types";

export type WritingSkillContentCommonProps = {
    messageIndex: number;
    messageId?: string;
    offline: boolean;
    applyDisabled?: boolean;
    loadingKey?: string;
    applyingCoverMessageId?: string;
    selectedTitle?: string;
    currentMarkdown: string;
    onApplyValues: (cv: ArticleChangeableValue) => void;
    onSelectTitle: (messageIndex: number, title: string) => void;
    onRefine: (prompt: string, tool: WritingSkill) => void;
    onUpdateToolPayload: (messageIndex: number, toolPayload: WritingSkillPayload, persist?: boolean) => void;
    onApplyGeneratedCover?: (cover: {
        dataUrl: string;
        extension?: string;
        messageId?: string;
    }) => Promise<string | undefined>;
    onCoverApplyingChange: (coverApplyKey?: string) => void;
    onCropCover: (url: string) => void;
};

export type SpecificToolContentProps<T extends WritingSkillPayload> = WritingSkillContentCommonProps & {
    toolPayload: T;
};
