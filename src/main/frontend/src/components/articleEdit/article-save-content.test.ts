import { describe, expect, it, jest } from "@jest/globals";
import { ArticleEntry } from "./index.types";
import { renderMissingMarkdownContent } from "./article-save-content";

const article = (overrides: Partial<ArticleEntry> = {}): ArticleEntry => ({
    title: "Article",
    rubbish: true,
    version: 0,
    editorType: "markdown",
    markdown: "# Article",
    ...overrides,
});

describe("renderMissingMarkdownContent", () => {
    it("renders missing HTML with link previews disabled", async () => {
        const renderMarkdown = jest.fn(
            async (_markdown: string, _options?: { linkPreview?: boolean }) => "<h1>Article</h1>\n"
        );

        const result = await renderMissingMarkdownContent(article(), renderMarkdown);

        expect(result.content).toBe("<h1>Article</h1>\n");
        expect(renderMarkdown).toHaveBeenCalledWith("# Article", { linkPreview: false });
    });

    it("preserves content already produced by the editor", async () => {
        const renderMarkdown = jest.fn(async () => "");
        const original = article({ content: "<p>Existing</p>" });

        const result = await renderMissingMarkdownContent(original, renderMarkdown);

        expect(result).toBe(original);
        expect(renderMarkdown).not.toHaveBeenCalled();
    });

    it("does not render non-Markdown articles", async () => {
        const renderMarkdown = jest.fn(async () => "");
        const original = article({ editorType: "html", content: "" });

        const result = await renderMissingMarkdownContent(original, renderMarkdown);

        expect(result).toBe(original);
        expect(renderMarkdown).not.toHaveBeenCalled();
    });
});
