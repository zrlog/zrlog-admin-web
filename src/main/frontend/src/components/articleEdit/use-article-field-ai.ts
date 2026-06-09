import {useState} from "react";
import {ArticleChangeableValue} from "./index.types";

type ArticleFieldAiOptions = {
    onValuesChange: (cv: ArticleChangeableValue) => void;
    onApplied?: () => void;
};

const useArticleFieldAi = ({ onValuesChange, onApplied }: ArticleFieldAiOptions) => {
    const [titleInputRevision, setTitleInputRevision] = useState(0);
    const [aliasInputRevision, setAliasInputRevision] = useState(0);

    const applyGeneratedValues = (cv: ArticleChangeableValue) => {
        onValuesChange(cv);
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
    };
};

export default useArticleFieldAi;
