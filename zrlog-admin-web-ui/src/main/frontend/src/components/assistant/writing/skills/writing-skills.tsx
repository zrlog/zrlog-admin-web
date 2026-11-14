import ApartmentIcon from "@zrlog/ui/icons/apartment";
import IdeaIcon from "@zrlog/ui/icons/idea";
import EditIcon from "@zrlog/ui/icons/edit";
import FileTextIcon from "@zrlog/ui/icons/file-text";
import LinkIcon from "@zrlog/ui/icons/link";
import MessageIcon from "@zrlog/ui/icons/message";
import ImageIcon from "@zrlog/ui/icons/image";
import SearchIcon from "@zrlog/ui/icons/search";
import StarIcon from "@zrlog/ui/icons/star";
import TagsIcon from "@zrlog/ui/icons/tags";
import { getRes } from "../../../../utils/constants";
import { WritingSkill, WritingSkillButton, WritingSkillGroup } from "../writing-assistant.types";

export const buildWritingSkillButtons = (): WritingSkillButton[] => [
    {
        key: "rewrite",
        command: "rewrite",
        icon: <EditIcon />,
        label: getRes().articleEdit.assistant.rewriteSuggestion,
        prompt: getRes().articleEdit.assistant.rewriteSuggestionPrompt,
        group: "editing",
    },
    {
        key: "structure",
        command: "structure",
        icon: <ApartmentIcon />,
        label: getRes().articleEdit.assistant.structure,
        prompt: getRes().articleEdit.assistant.structurePrompt,
        group: "ideation",
    },
    {
        key: "questions",
        command: "questions",
        icon: <MessageIcon />,
        label: getRes().articleEdit.assistant.readerQuestions,
        prompt: getRes().articleEdit.assistant.readerQuestionsPrompt,
        group: "ideation",
    },
    {
        key: "title",
        command: "title",
        icon: <IdeaIcon />,
        label: getRes().articleEdit.assistant.titleSuggestion,
        prompt: getRes().articleEdit.assistant.titleSuggestionPrompt,
        group: "ideation",
    },
    {
        key: "alias",
        command: "alias",
        icon: <LinkIcon />,
        label: getRes().articleEdit.assistant.aliasSuggestion,
        prompt: getRes().articleEdit.assistant.aliasSuggestionPrompt,
        group: "editing",
    },
    {
        key: "digest",
        command: "digest",
        icon: <FileTextIcon />,
        label: getRes().articleEdit.assistant.digestSuggestion,
        prompt: getRes().articleEdit.assistant.digestSuggestionPrompt,
        group: "editing",
    },
    {
        key: "tags",
        command: "tags",
        icon: <TagsIcon />,
        label: getRes().articleEdit.assistant.tagsSuggestion,
        prompt: getRes().articleEdit.assistant.tagsSuggestionPrompt,
        group: "editing",
    },
    {
        key: "cover",
        command: "cover",
        icon: <ImageIcon />,
        label: getRes().articleEdit.assistant.coverSuggestion,
        prompt: getRes().articleEdit.assistant.coverSuggestionPrompt,
        group: "editing",
    },
    {
        key: "score",
        command: "score",
        icon: <StarIcon />,
        label: getRes().articleEdit.assistant.score,
        prompt: getRes().articleEdit.assistant.scorePrompt,
        group: "publishCheck",
    },
    {
        key: "publishCheck",
        command: "publish-check",
        icon: <StarIcon />,
        label: getRes().articleEdit.assistant.publishCheck,
        prompt: getRes().articleEdit.assistant.publishCheckPrompt,
        group: "publishCheck",
    },
    {
        key: "seo",
        command: "seo",
        icon: <SearchIcon />,
        label: getRes().articleEdit.assistant.seo,
        prompt: getRes().articleEdit.assistant.seoPrompt,
        group: "publishCheck",
    },
    {
        key: "proofread",
        command: "proofread",
        icon: <EditIcon />,
        label: getRes().articleEdit.assistant.proofread,
        prompt: getRes().articleEdit.assistant.proofreadPrompt,
        group: "publishCheck",
    },
];

export const buildWritingSkillGroups = (): WritingSkillGroup[] => [
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

export const getWritingSkillLabel = (tool: WritingSkill) => {
    const matched = buildWritingSkillButtons().find((item) => item.key === tool);
    return matched?.label || getRes().articleEdit.assistant.structure;
};

export const getToolRefineActions = (tool: WritingSkill) => {
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
