import { ChangedContent } from "./index.types";

type EditorContentChange = {
    value: string;
    previewContent: string;
};

export const getArticleEditorChange = (
    change: EditorContentChange,
    currentMarkdown: string | undefined,
    liveMarkdown: string | undefined
): ChangedContent | undefined => {
    // Rendering can finish after the editor has moved on to another document value.
    if (liveMarkdown !== undefined && change.value !== liveMarkdown) {
        return undefined;
    }
    if (change.value === (currentMarkdown ?? "")) {
        return undefined;
    }
    return { markdown: change.value, content: change.previewContent };
};
