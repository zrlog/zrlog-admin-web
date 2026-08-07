import { ArticleEntry } from "./index.types";

type MarkdownRenderer = (markdown: string, options?: { linkPreview?: boolean }) => Promise<string>;

export const renderMissingMarkdownContent = async (
    article: ArticleEntry,
    renderMarkdown: MarkdownRenderer
): Promise<ArticleEntry> => {
    if (article.editorType !== "markdown" || article.content || !article.markdown) {
        return article;
    }
    return {
        ...article,
        content: await renderMarkdown(article.markdown, { linkPreview: false }),
    };
};
