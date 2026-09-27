import { act, useState } from "react";
import { createRoot, Root } from "react-dom/client";
import { afterEach, beforeEach, describe, expect, it, jest } from "@jest/globals";
import { ArticleChangeableValue, ArticleEntry } from "./index.types";
import { renderMissingMarkdownContent } from "./article-save-content";
import useArticleFieldAi from "./use-article-field-ai";
import { getArticleEditorChange } from "./article-editor-change";

const reactActEnvironment = globalThis as typeof globalThis & {
    IS_REACT_ACT_ENVIRONMENT?: boolean;
};

const initialArticle: ArticleEntry = {
    title: "Original title",
    digest: "Original summary",
    typeId: 1,
    rubbish: true,
    version: 3,
    editorType: "markdown",
    markdown: "Original body",
    content: "<p>Original body</p>",
};

describe("AI field application and saved article content", () => {
    let container: HTMLDivElement;
    let root: Root;
    let article: ArticleEntry;
    let fieldAi: ReturnType<typeof useArticleFieldAi>;
    let onEditorChange: (change: { value: string; previewContent: string }, liveMarkdown: string) => void;
    const onApplied = jest.fn();

    const Harness = () => {
        const [value, setValue] = useState(initialArticle);
        article = value;
        fieldAi = useArticleFieldAi({
            onValuesChange: (patch) => setValue((current) => ({ ...current, ...patch })),
            onApplied,
        });
        onEditorChange = (change, liveMarkdown) => {
            const patch = getArticleEditorChange(change, value.markdown, liveMarkdown);
            if (patch) {
                setValue((current) => ({ ...current, ...patch }));
            }
        };
        return null;
    };

    beforeEach(() => {
        reactActEnvironment.IS_REACT_ACT_ENVIRONMENT = true;
        onApplied.mockClear();
        container = document.createElement("div");
        document.body.appendChild(container);
        root = createRoot(container);
        act(() => root.render(<Harness />));
    });

    afterEach(() => {
        act(() => root.unmount());
        container.remove();
        reactActEnvironment.IS_REACT_ACT_ENVIRONMENT = false;
    });

    it("renders the accepted AI body instead of sending the article's old HTML", async () => {
        act(() => fieldAi.applyGeneratedValues({ markdown: "Accepted AI body" }));
        expect(article.markdown).toBe("Accepted AI body");
        expect(article.content).toBe("");

        const renderMarkdown = jest.fn(async () => "<p>Accepted AI body</p>");
        const payload = await renderMissingMarkdownContent(article, renderMarkdown);
        expect(renderMarkdown).toHaveBeenCalledWith("Accepted AI body", { linkPreview: false });
        expect(payload.markdown).toBe("Accepted AI body");
        expect(payload.content).toBe("<p>Accepted AI body</p>");
        expect(onApplied).toHaveBeenCalledTimes(1);
        expect(initialArticle.content).toBe("<p>Original body</p>");
    });

    it("clears both representations when an applied body is empty", async () => {
        act(() => fieldAi.applyGeneratedValues({ markdown: "" }));
        const renderMarkdown = jest.fn(async () => "<p>Unexpected old body</p>");
        const payload = await renderMissingMarkdownContent(article, renderMarkdown);

        expect(payload.markdown).toBe("");
        expect(payload.content).toBe("");
        expect(renderMarkdown).not.toHaveBeenCalled();
    });

    it.each(["Accepted AI body", ""])("does not restore old HTML from an unchanged editor callback: %j", (markdown) => {
        act(() => fieldAi.applyGeneratedValues({ markdown }));
        act(() => onEditorChange({ value: markdown, previewContent: initialArticle.content as string }, markdown));

        expect(article.markdown).toBe(markdown);
        expect(article.content).toBe("");
    });

    it("ignores an earlier asynchronous editor render after applying AI and still accepts later typing", async () => {
        const earlierEditorCallback = onEditorChange;
        let finishEarlierRender!: (html: string) => void;
        const earlierRender = new Promise<string>((resolve) => {
            finishEarlierRender = resolve;
        });
        const earlierChange = earlierRender.then((html) =>
            earlierEditorCallback({ value: "Earlier typing", previewContent: html }, "Accepted AI body")
        );
        act(() => fieldAi.applyGeneratedValues({ markdown: "Accepted AI body" }));
        await act(async () => {
            finishEarlierRender("<p>Earlier typing</p>");
            await earlierChange;
        });
        expect(article.markdown).toBe("Accepted AI body");
        expect(article.content).toBe("");

        act(() =>
            onEditorChange({ value: "Edited AI body", previewContent: "<p>Edited AI body</p>" }, "Edited AI body")
        );
        const renderMarkdown = jest.fn(async () => "<p>Unexpected replacement</p>");
        const payload = await renderMissingMarkdownContent(article, renderMarkdown);
        expect(payload.markdown).toBe("Edited AI body");
        expect(payload.content).toBe("<p>Edited AI body</p>");
        expect(renderMarkdown).not.toHaveBeenCalled();
    });

    it.each([
        { title: "AI title" },
        { digest: "AI summary" },
        { alias: "ai-title" },
        { keywords: "ai,writing" },
    ] as ArticleChangeableValue[])("preserves body HTML when applying only metadata: %j", async (patch) => {
        act(() => fieldAi.applyGeneratedValues(patch));
        const renderMarkdown = jest.fn(async () => "<p>Unexpected replacement</p>");
        const payload = await renderMissingMarkdownContent(article, renderMarkdown);

        expect(payload).toMatchObject(patch);
        expect(payload.markdown).toBe(initialArticle.markdown);
        expect(payload.content).toBe(initialArticle.content);
        expect(renderMarkdown).not.toHaveBeenCalled();
    });
});
