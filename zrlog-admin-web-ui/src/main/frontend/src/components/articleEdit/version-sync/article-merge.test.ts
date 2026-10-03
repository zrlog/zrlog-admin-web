import { describe, expect, it } from "@jest/globals";
import { ArticleEntry } from "../index.types";
import { buildArticleMerge, resolveArticleMerge } from "./article-merge";

const base: ArticleEntry = {
    logId: 7,
    version: 3,
    title: "Title",
    markdown: "First\nMiddle\nLast\n",
    content: "old rendered HTML",
    typeId: 1,
    rubbish: true,
};
const merge = (local: Partial<ArticleEntry>, server: Partial<ArticleEntry>) =>
    buildArticleMerge(base, { ...base, ...local }, { ...base, version: 4, ...server });

describe("three-way article merge", () => {
    it("combines independent fields without restoring stale values", () => {
        const plan = merge({ title: "My title" }, { digest: "Remote summary", thumbnail: "remote.png" });
        expect(plan.conflicts).toEqual([]);
        expect(resolveArticleMerge(plan, {})).toMatchObject({
            title: "My title",
            digest: "Remote summary",
            thumbnail: "remote.png",
            version: 4,
            logId: 7,
        });
    });
    it("merges edits in separate paragraphs and rebuilds HTML from the merged markdown", () => {
        const plan = merge({ markdown: "My first\nMiddle\nLast\n" }, { markdown: "First\nMiddle\nRemote last\n" });
        expect(plan.conflicts).toEqual([]);
        const article = resolveArticleMerge(plan, {})!;
        expect(article.markdown).toBe("My first\nMiddle\nRemote last\n");
        expect(article.content).toBeUndefined();
        expect(article.editorType).toBe("markdown");
    });
    it("requires an explicit decision for overlapping changes and preserves unrelated remote changes", () => {
        const plan = merge(
            { markdown: "Mine\nMiddle\nLast\n" },
            { markdown: "Theirs\nMiddle\nRemote last\n", digest: "Remote summary" }
        );
        expect(plan.conflicts).toHaveLength(1);
        expect(plan.conflicts[0]).toMatchObject({ base: "First\n", local: "Mine\n", server: "Theirs\n" });
        expect(resolveArticleMerge(plan, {})).toBeUndefined();
        expect(resolveArticleMerge(plan, { [plan.conflicts[0].id]: "Combined\n" })).toMatchObject({
            markdown: "Combined\nMiddle\nRemote last\n",
            digest: "Remote summary",
            version: 4,
        });
    });
    it("accepts identical concurrent edits without duplicating text", () => {
        const plan = merge({ markdown: "Same\nMiddle\nLast\n" }, { markdown: "Same\nMiddle\nLast\n" });
        expect(plan.conflicts).toEqual([]);
        expect(resolveArticleMerge(plan, {})?.markdown).toBe("Same\nMiddle\nLast\n");
    });
    it("merges a deletion with a separate edit", () => {
        const plan = merge({ markdown: "Middle\nLast\n" }, { markdown: "First\nMiddle\nNew last\n" });
        expect(plan.conflicts).toEqual([]);
        expect(resolveArticleMerge(plan, {})?.markdown).toBe("Middle\nNew last\n");
    });
    it("requires a choice for delete-versus-edit and supports an explicit empty result", () => {
        const plan = merge({ markdown: "Middle\nLast\n" }, { markdown: "Edited first\nMiddle\nLast\n" });
        expect(plan.conflicts).toHaveLength(1);
        expect(resolveArticleMerge(plan, { [plan.conflicts[0].id]: "" })?.markdown).toBe("Middle\nLast\n");
    });
    it("keeps concurrent insertions at the same point as a conflict", () => {
        const plan = merge({ markdown: "My intro\n" + base.markdown }, { markdown: "Their intro\n" + base.markdown });
        expect(plan.conflicts).toHaveLength(1);
        expect(resolveArticleMerge(plan, { [plan.conflicts[0].id]: "My intro\nTheir intro\n" })?.markdown).toBe(
            "My intro\nTheir intro\n" + base.markdown
        );
    });
    it("preserves CRLF and a missing final newline", () => {
        const original = { ...base, markdown: "one\r\ntwo\r\nthree" };
        const plan = buildArticleMerge(
            original,
            { ...original, markdown: "ONE\r\ntwo\r\nthree" },
            { ...original, version: 4, markdown: "one\r\ntwo\r\nTHREE" }
        );
        expect(resolveArticleMerge(plan, {})?.markdown).toBe("ONE\r\ntwo\r\nTHREE");
    });
    it("does not guess which differing value is correct when the base is unavailable", () => {
        const plan = buildArticleMerge(undefined, { ...base, title: "Mine" }, { ...base, title: "Theirs", version: 4 });
        expect(plan.baseAvailable).toBe(false);
        expect(plan.conflicts).toEqual([
            { id: "title", field: "title", base: undefined, local: "Mine", server: "Theirs" },
        ]);
        expect(resolveArticleMerge(plan, {})).toBeUndefined();
    });
    it("preserves remote visibility and category changes when local edits only affect the body", () => {
        const plan = merge({ markdown: "New body" }, { privacy: true, typeId: 9, rubbish: false });
        expect(resolveArticleMerge(plan, {})).toMatchObject({
            markdown: "New body",
            privacy: true,
            typeId: 9,
            rubbish: false,
        });
    });
    it("requires a choice when both clients change a category differently", () => {
        const plan = merge({ typeId: 2 }, { typeId: 3 });
        expect(resolveArticleMerge(plan, {})).toBeUndefined();
        expect(resolveArticleMerge(plan, { typeId: 2 })?.typeId).toBe(2);
    });
    it("bounds work for large completely different articles and retains both candidates", () => {
        const original = { ...base, markdown: Array.from({ length: 1500 }, (_, i) => `base ${i}\n`).join("") };
        const local = { ...original, markdown: original.markdown.replace(/base/g, "local") };
        const server = { ...original, version: 4, markdown: original.markdown.replace(/base/g, "server") };
        const plan = buildArticleMerge(original, local, server);
        expect(plan.conflicts).toHaveLength(1);
        expect(plan.conflicts[0]).toMatchObject({ local: local.markdown, server: server.markdown });
    });
});
