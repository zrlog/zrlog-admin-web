import { useRef, useState } from "react";
import { ArticleChangeableValue, ArticleEntry } from "./index.types";

import { canApplySkillValues, createSkillApplicationSession } from "./article-ai-assistant/article-ai-skill-contract";
import { ToolAwareAIContent } from "./article-ai-assistant/article-ai-assistant.types";

export type SkillContextRevision = (message: ToolAwareAIContent) => string;
export type ApplyAiValues = (cv: ArticleChangeableValue, source?: ToolAwareAIContent) => void;

type ArticleFieldAiOptions = {
    getCurrentArticle: () => ArticleEntry;
    onValuesChange: (cv: ArticleChangeableValue) => { article: ArticleEntry } | undefined;
    onApplied?: () => void;
};

const useArticleFieldAi = ({ getCurrentArticle, onValuesChange, onApplied }: ArticleFieldAiOptions) => {
    const application = useRef(createSkillApplicationSession());
    const getSkillContextRevision: SkillContextRevision = (source) =>
        application.current.getRevision(source, getCurrentArticle());
    const [titleInputRevision, setTitleInputRevision] = useState(0);
    const [aliasInputRevision, setAliasInputRevision] = useState(0);

    const applyGeneratedValues: ApplyAiValues = (cv, source) => {
        if (source && !canApplySkillValues(source, getSkillContextRevision(source), cv)) return;
        const values = "markdown" in cv && cv.markdown !== undefined ? { ...cv, content: "" } : cv;
        const change = onValuesChange(values);
        if (!change) return;
        application.current.record(source, change.article);
        if ("title" in cv && cv.title !== undefined) {
            setTitleInputRevision((revision) => revision + 1);
        }
        if ("alias" in cv && cv.alias !== undefined) {
            setAliasInputRevision((revision) => revision + 1);
        }
        onApplied?.();
    };

    return {
        titleInputRevision,
        aliasInputRevision,
        applyGeneratedValues,
        getSkillContextRevision,
    };
};

export default useArticleFieldAi;
