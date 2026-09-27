import { describe, expect, it } from "@jest/globals";
import { filterTemplates, getTemplateAuthorUrl, getTemplatePreviewUrl, TemplateEntry } from "./template-model";

const entry = (overrides: Partial<TemplateEntry>): TemplateEntry => ({
    template: "/include/templates/local",
    shortTemplate: "local",
    name: "Local",
    digest: "A theme",
    version: "1.0",
    author: "Writer",
    tags: [],
    adminPreviewImage: "",
    previewImage: "",
    builtIn: false,
    use: false,
    preview: false,
    deleteAble: true,
    ...overrides,
});

describe("theme source and filters", () => {
    const bundled = entry({ shortTemplate: "default", builtIn: true, deleteAble: false });
    const activeLocal = entry({ shortTemplate: "active-local", use: true, deleteAble: false });
    const removable = entry({ shortTemplate: "fluid", name: "Fluid", author: "Fluid Team", tags: ["polyglot"] });
    const themes = [bundled, removable, activeLocal];

    it("uses the SPI origin flag independently from active and deletion states", () => {
        expect(filterTemplates(themes, "builtin", "")).toEqual([bundled]);
        expect(filterTemplates(themes, "active", "")).toEqual([activeLocal]);
        expect(filterTemplates(themes, "removable", "")).toEqual([removable]);
        expect(filterTemplates(themes, "all", "")[0]).toBe(activeLocal);
        expect(themes[0]).toBe(bundled);
    });
    it("combines source filters with name, author and tag searches", () => {
        expect(filterTemplates(themes, "all", " FLUID TEAM ")).toEqual([removable]);
        expect(filterTemplates(themes, "all", "polyglot")).toEqual([removable]);
        expect(filterTemplates(themes, "builtin", "fluid")).toEqual([]);
    });
});

describe("theme author homepage", () => {
    it.each(["https://author.example/blog", "http://author.example/", "//author.example/"])(
        "preserves the external address %s",
        (href) => {
            expect(getTemplateAuthorUrl(` ${href} `)).toBe(href);
        }
    );
    it.each([
        undefined,
        "",
        " ",
        "/author",
        "../author",
        "author.example",
        "javascript:alert(1)",
        "data:text/html,test",
        "file:///tmp/home",
        "https://",
    ])("does not render a link for %s", (href) => {
        expect(getTemplateAuthorUrl(href)).toBeUndefined();
    });
});

describe("theme preview URLs", () => {
    it.each([
        ["/admin/template/preview-image?shortTemplate=default", "/", "/admin/template/preview-image?shortTemplate=default"],
        ["/admin/template/preview-image?t=1", "/blog/", "/blog/admin/template/preview-image?t=1"],
        ["admin/template/preview-image", "/blog", "/blog/admin/template/preview-image"],
        ["/blog/admin/template/preview-image", "/blog/", "/blog/admin/template/preview-image"],
        ["/admin/template/preview-image", "https://backend.example/blog/", "https://backend.example/blog/admin/template/preview-image"],
        ["/blog/admin/template/preview-image", "https://backend.example/blog/", "https://backend.example/blog/admin/template/preview-image"],
        ["https://theme.example/preview.png", "/blog/", "https://theme.example/preview.png"],
        ["//theme.example/preview.png", "/blog/", "//theme.example/preview.png"],
        ["javascript:alert(1)", "/blog/", undefined],
        ["", "/blog/", ""],
        [undefined, "/blog/", undefined],
    ])("resolves %s against %s without duplicating the context path", (src, base, expected) => {
        expect(getTemplatePreviewUrl(src, base!)).toBe(expected);
    });
});
