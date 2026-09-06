import {
    ApartmentOutlined,
    BulbOutlined,
    EditOutlined,
    FileTextOutlined,
    LinkOutlined,
    MessageOutlined,
    PictureOutlined,
    SearchOutlined,
    StarOutlined,
    TagsOutlined,
} from "@ant-design/icons";
import { getRes } from "../../../../utils/constants";
import {
    AssistantTool,
    AssistantToolButton,
    AssistantToolContextPolicy,
    AssistantToolGroup,
} from "../article-ai-assistant.types";

// Keep this aligned with AIChatService#getToolContextPolicy.
const CHAT_ONLY_TOOL_CONTEXT_TOOLS: AssistantTool[] = [
    "publishCheck",
    "score",
    "seo",
    "proofread",
    "structure",
    "questions",
    "tags",
    "cover",
];

export const buildAssistantToolButtons = (): AssistantToolButton[] => [
    {
        key: "rewrite",
        command: "rewrite",
        icon: <EditOutlined />,
        label: getRes().articleEdit.assistant.rewriteSuggestion,
        prompt: getRes().articleEdit.assistant.rewriteSuggestionPrompt,
        group: "editing",
    },
    {
        key: "structure",
        command: "structure",
        icon: <ApartmentOutlined />,
        label: getRes().articleEdit.assistant.structure,
        prompt: getRes().articleEdit.assistant.structurePrompt,
        group: "ideation",
    },
    {
        key: "questions",
        command: "questions",
        icon: <MessageOutlined />,
        label: getRes().articleEdit.assistant.readerQuestions,
        prompt: getRes().articleEdit.assistant.readerQuestionsPrompt,
        group: "ideation",
    },
    {
        key: "title",
        command: "title",
        icon: <BulbOutlined />,
        label: getRes().articleEdit.assistant.titleSuggestion,
        prompt: getRes().articleEdit.assistant.titleSuggestionPrompt,
        group: "ideation",
    },
    {
        key: "alias",
        command: "alias",
        icon: <LinkOutlined />,
        label: getRes().articleEdit.assistant.aliasSuggestion,
        prompt: getRes().articleEdit.assistant.aliasSuggestionPrompt,
        group: "editing",
    },
    {
        key: "digest",
        command: "digest",
        icon: <FileTextOutlined />,
        label: getRes().articleEdit.assistant.digestSuggestion,
        prompt: getRes().articleEdit.assistant.digestSuggestionPrompt,
        group: "editing",
    },
    {
        key: "tags",
        command: "tags",
        icon: <TagsOutlined />,
        label: getRes().articleEdit.assistant.tagsSuggestion,
        prompt: getRes().articleEdit.assistant.tagsSuggestionPrompt,
        group: "editing",
    },
    {
        key: "cover",
        command: "cover",
        icon: <PictureOutlined />,
        label: getRes().articleEdit.assistant.coverSuggestion,
        prompt: getRes().articleEdit.assistant.coverSuggestionPrompt,
        group: "editing",
    },
    {
        key: "score",
        command: "score",
        icon: <StarOutlined />,
        label: getRes().articleEdit.assistant.score,
        prompt: getRes().articleEdit.assistant.scorePrompt,
        group: "publishCheck",
    },
    {
        key: "publishCheck",
        command: "publish-check",
        icon: <StarOutlined />,
        label: getRes().articleEdit.assistant.publishCheck,
        prompt: getRes().articleEdit.assistant.publishCheckPrompt,
        group: "publishCheck",
    },
    {
        key: "seo",
        command: "seo",
        icon: <SearchOutlined />,
        label: getRes().articleEdit.assistant.seo,
        prompt: getRes().articleEdit.assistant.seoPrompt,
        group: "publishCheck",
    },
    {
        key: "proofread",
        command: "proofread",
        icon: <EditOutlined />,
        label: getRes().articleEdit.assistant.proofread,
        prompt: getRes().articleEdit.assistant.proofreadPrompt,
        group: "publishCheck",
    },
];

export const buildAssistantToolGroups = (): AssistantToolGroup[] => [
    {
        key: "ideation",
        label: getRes().articleEdit.assistant.groupIdeation,
    },
    {
        key: "editing",
        label: getRes().articleEdit.assistant.groupEditing,
    },
    {
        key: "publishCheck",
        label: getRes().articleEdit.assistant.groupPublishCheck,
    },
];

export const getAssistantToolLabel = (tool: AssistantTool) => {
    const matched = buildAssistantToolButtons().find((item) => item.key === tool);
    return matched?.label || getRes().articleEdit.assistant.structure;
};

export const getAssistantToolContextPolicy = (tool?: AssistantTool): AssistantToolContextPolicy => {
    if (!tool) {
        return "fullConversation";
    }
    if (CHAT_ONLY_TOOL_CONTEXT_TOOLS.includes(tool)) {
        return "chatOnly";
    }
    return "fullConversation";
};

export const getAssistantToolOutcomeDescription = (tool: AssistantTool) => {
    const assistantRes = getRes().articleEdit.assistant;
    switch (tool) {
        case "rewrite":
            return assistantRes.toolOutcomeRewrite;
        case "title":
            return assistantRes.toolOutcomeTitle;
        case "alias":
            return assistantRes.toolOutcomeAlias;
        case "digest":
            return assistantRes.toolOutcomeDigest;
        case "tags":
            return assistantRes.toolOutcomeTags;
        case "cover":
            return assistantRes.toolOutcomeCover;
        case "score":
        case "publishCheck":
            return assistantRes.toolOutcomeScore;
        case "seo":
            return assistantRes.toolOutcomeSeo;
        case "proofread":
            return assistantRes.toolOutcomeProofread;
        case "structure":
            return assistantRes.toolOutcomeStructure;
        case "questions":
            return assistantRes.toolOutcomeQuestions;
        default:
            return assistantRes.toolOutcomeReportOnly;
    }
};

export const getToolRefineActions = (tool: AssistantTool) => {
    if (tool === "title") {
        return [
            {
                label: getRes().articleEdit.assistant.refineAgain,
                prompt: getRes().articleEdit.assistant.titleRefineAgainPrompt,
            },
            {
                label: getRes().articleEdit.assistant.titleRefineShorter,
                prompt: getRes().articleEdit.assistant.titleRefineShorterPrompt,
            },
            {
                label: getRes().articleEdit.assistant.titleRefineTechnical,
                prompt: getRes().articleEdit.assistant.titleRefineTechnicalPrompt,
            },
        ];
    }
    return [
        {
            label: getRes().articleEdit.assistant.refineAgain,
            prompt: getRes().articleEdit.assistant.refineAgainPrompt,
        },
    ];
};
