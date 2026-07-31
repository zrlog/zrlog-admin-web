import { afterAll, beforeAll, describe, expect, it } from "@jest/globals";
import { TextDecoder as NodeTextDecoder } from "util";
import {
    buildMarkdownImportedArticle,
    buildMarkdownImportedPatch,
    collectMarkdownImportResources,
    getDefaultMarkdownImportFields,
    getMarkdownImportApplyFields,
    getMarkdownImportTargetPolicy,
    MARKDOWN_IMPORT_MAX_FILE_SIZE,
    MarkdownImportField,
    parseMarkdownImportText,
    readMarkdownImportFile,
    resolveMarkdownImportCategory,
} from "./markdown-import";

const browserTextDecoder = globalThis.TextDecoder;

beforeAll(() => {
    Object.defineProperty(globalThis, "TextDecoder", {
        configurable: true,
        value: NodeTextDecoder,
    });
});

afterAll(() => {
    Object.defineProperty(globalThis, "TextDecoder", {
        configurable: true,
        value: browserTextDecoder,
    });
});

describe("Markdown import parsing", () => {
    it("parses YAML front matter and keeps the Markdown body unchanged", () => {
        const preview = parseMarkdownImportText(
            [
                "---",
                "title: Imported title",
                "slug: imported-title",
                "description: Imported summary",
                "tags:",
                "  - zrlog",
                "  - markdown",
                "categories: Docs",
                "date: 2026-07-31",
                "custom: ignored",
                "---",
                "# Heading",
                "",
                "Body",
            ].join("\r\n"),
            "post.markdown"
        );

        expect(preview.markdown).toBe("# Heading\r\n\r\nBody");
        expect(preview.metadata).toEqual({
            title: "Imported title",
            titleSource: "frontMatter",
            alias: "imported-title",
            digest: "Imported summary",
            keywords: "zrlog,markdown",
            categoryNames: ["Docs"],
        });
        expect(preview.ignoredFields).toEqual(["date"]);
        expect(preview.unknownFields).toEqual(["custom"]);
        expect(preview.invalidFields).toEqual([]);
    });

    it("recognizes front matter after a UTF-8 BOM", () => {
        const preview = parseMarkdownImportText("\uFEFF---\ntitle: BOM title\n---\nBody", "bom.md");

        expect(preview.metadata.title).toBe("BOM title");
        expect(preview.markdown).toBe("Body");
    });

    it("reads a real UTF-8 BOM and CRLF file", async () => {
        const preview = await readMarkdownImportFile(
            new File(["\uFEFF---\r\ntitle: File title\r\n---\r\nBody"], "post.md", {
                type: "text/markdown",
            })
        );

        expect(preview.metadata.title).toBe("File title");
        expect(preview.markdown).toBe("Body");
    });

    it("accepts exactly 2 MiB and rejects larger files or invalid UTF-8", async () => {
        const maximumFile = new File(["x".repeat(MARKDOWN_IMPORT_MAX_FILE_SIZE)], "maximum.md");
        const oversizedFile = new File(["x".repeat(MARKDOWN_IMPORT_MAX_FILE_SIZE + 1)], "oversized.md");
        const invalidUtf8File = new File([new Uint8Array([0xc3, 0x28])], "invalid.md");

        await expect(readMarkdownImportFile(maximumFile)).resolves.toMatchObject({
            fileSize: MARKDOWN_IMPORT_MAX_FILE_SIZE,
        });
        await expect(readMarkdownImportFile(oversizedFile)).rejects.toMatchObject({
            code: "too-large",
        });
        await expect(readMarkdownImportFile(invalidUtf8File)).rejects.toMatchObject({
            code: "invalid-utf8",
        });
    });

    it("falls back to the file name and reports invalid metadata without applying it", () => {
        const preview = parseMarkdownImportText(
            ["---", "title:", "tags:", "  nested: invalid", "alias: [invalid]", "---", "Body"].join("\n"),
            "fallback-name.md"
        );

        expect(preview.metadata.title).toBe("fallback-name");
        expect(preview.metadata.titleSource).toBe("fileName");
        expect(preview.metadata.alias).toBeUndefined();
        expect(preview.metadata.keywords).toBe("");
        expect(preview.invalidFields).toEqual(["alias", "tags"]);
    });

    it("rejects alias and joined tags that exceed the database columns", () => {
        const preview = parseMarkdownImportText(
            [
                "---",
                `alias: ${"a".repeat(65)}`,
                `tags: [${Array.from({ length: 3 }, (_, index) => `${index}-${"x".repeat(90)}`).join(", ")}]`,
                "---",
                "Body",
            ].join("\n"),
            "limits.md"
        );

        expect(preview.metadata.alias).toBeUndefined();
        expect(preview.metadata.keywords).toBe("");
        expect(preview.invalidFields).toEqual(["alias", "tags"]);
    });

    it("limits a file-name fallback title to the editor field length", () => {
        const preview = parseMarkdownImportText("Body", `${"a".repeat(120)}.md`);

        expect(preview.metadata.title).toHaveLength(100);
    });

    it("rejects invalid files and unsafe or malformed front matter", () => {
        expect(() => parseMarkdownImportText("Body", "post.txt")).toThrow(
            expect.objectContaining({ code: "invalid-extension" })
        );
        expect(() => parseMarkdownImportText(" \n", "post.md")).toThrow(
            expect.objectContaining({ code: "empty-file" })
        );
        expect(() => parseMarkdownImportText("Body\0", "post.md")).toThrow(
            expect.objectContaining({ code: "binary-file" })
        );
        expect(() => parseMarkdownImportText("---\ntitle: Missing close\nBody", "post.md")).toThrow(
            expect.objectContaining({ code: "unclosed-front-matter" })
        );
        expect(() => parseMarkdownImportText("---\n- invalid root\n---\nBody", "post.md")).toThrow(
            expect.objectContaining({ code: "front-matter-root" })
        );
    });

    it("rejects dangerous keys, excessive depth, and excessive aliases", () => {
        const dangerous = "---\n__proto__:\n  polluted: true\n---\nBody";
        const deep = `---\n${Array.from({ length: 10 }, (_, index) => `${"  ".repeat(index)}level${index}:`).join(
            "\n"
        )}\n${"  ".repeat(10)}value\n---\nBody`;
        const aliases = `---\nbase: &base value\nitems: [${Array.from({ length: 21 }, () => "*base").join(
            ", "
        )}]\n---\nBody`;

        expect(() => parseMarkdownImportText(dangerous, "dangerous.md")).toThrow(
            expect.objectContaining({ code: "front-matter-too-complex" })
        );
        expect(({} as Record<string, unknown>).polluted).toBeUndefined();
        expect(() => parseMarkdownImportText(deep, "deep.md")).toThrow(
            expect.objectContaining({ code: "front-matter-too-complex" })
        );
        expect(() => parseMarkdownImportText(aliases, "aliases.md")).toThrow(
            expect.objectContaining({ code: "invalid-front-matter" })
        );
    });

    it("classifies image references without requesting external resources", () => {
        expect(
            collectMarkdownImportResources(
                [
                    "![remote](https://cdn.example.com/a.png)",
                    "![protocol](//images.example.com/b.webp)",
                    "![relative](./images/c.jpg)",
                    "![site](/attached/d.png)",
                    "![embedded](data:image/png;base64,AAAA)",
                    '<img src="https://cdn.example.com/e.png" />',
                    "![reference][remote-ref]",
                    "![relative-ref][]",
                    "![shortcut-ref]",
                    "[remote-ref]: https://cdn.example.com/reference.png",
                    "[relative-ref]: ../images/reference.jpg",
                    "[shortcut-ref]: /attached/shortcut.png",
                ].join("\n")
            )
        ).toEqual({
            imageReferences: [
                "https://cdn.example.com/a.png",
                "//images.example.com/b.webp",
                "./images/c.jpg",
                "/attached/d.png",
                "data:image/png;base64,AAAA",
                "https://cdn.example.com/reference.png",
                "../images/reference.jpg",
                "/attached/shortcut.png",
                "https://cdn.example.com/e.png",
            ],
            remoteImages: [
                "https://cdn.example.com/a.png",
                "//images.example.com/b.webp",
                "https://cdn.example.com/reference.png",
                "https://cdn.example.com/e.png",
            ],
            remoteHosts: ["cdn.example.com", "images.example.com"],
            relativeImages: ["./images/c.jpg", "../images/reference.jpg"],
            siteAbsoluteImages: ["/attached/d.png", "/attached/shortcut.png"],
            embeddedImages: ["data:image/png;base64,AAAA"],
            unsupportedImages: [],
        });
    });
});

describe("Markdown import target and metadata policy", () => {
    const article = {
        title: "Current",
        markdown: "Current body",
        typeId: 1,
        rubbish: true,
        version: 2,
    };
    const preview = parseMarkdownImportText(
        ["---", "title: Imported", "tags: one, two", "category: Docs", "---", "Imported body"].join("\n"),
        "post.md"
    );

    it("imports a blank new article in place and defaults a non-empty draft to a new draft", () => {
        expect(
            getMarkdownImportTargetPolicy({
                title: "",
                markdown: "",
                rubbish: true,
                version: -1,
            })
        ).toEqual({
            initialTarget: "current",
            currentAllowed: true,
            newDraftAllowed: false,
            dangerousCurrentReplace: false,
        });
        expect(getMarkdownImportTargetPolicy(article)).toEqual({
            initialTarget: "newDraft",
            currentAllowed: true,
            newDraftAllowed: true,
            dangerousCurrentReplace: true,
        });
    });

    it("never replaces published or private articles", () => {
        expect(getMarkdownImportTargetPolicy({ ...article, logId: 42, rubbish: false }).currentAllowed).toBe(false);
        expect(getMarkdownImportTargetPolicy({ ...article, privacy: true }).forcedNewDraftReason).toBe("private");
        expect(getMarkdownImportTargetPolicy(article, article.markdown, true)).toEqual({
            initialTarget: "newDraft",
            currentAllowed: false,
            newDraftAllowed: true,
            dangerousCurrentReplace: false,
            forcedNewDraftReason: "contentConflict",
        });
    });

    it("matches only existing categories and builds a clean server draft", () => {
        const category = resolveMarkdownImportCategory(preview.metadata.categoryNames, [
            { value: 2, label: "Docs" },
            { value: 3, label: "Notes" },
        ]);
        expect(category).toEqual({
            typeId: 2,
            matchedName: "Docs",
            additionalMatchedNames: [],
            unmatchedNames: [],
        });
        const selectedFields = getDefaultMarkdownImportFields(preview, article, "newDraft");
        const imported = buildMarkdownImportedArticle({
            article: {
                ...article,
                logId: 42,
                privacy: true,
                recommended: true,
            },
            preview,
            selectedFields,
            selectedTypeId: category.typeId,
            target: "newDraft",
            html: "<p>Imported body</p>",
        });

        expect(imported).toEqual({
            title: "Imported",
            markdown: "Imported body",
            content: "<p>Imported body</p>",
            typeId: 2,
            keywords: "one,two",
            canComment: true,
            recommended: false,
            privacy: false,
            rubbish: true,
            version: -1,
            editorType: "markdown",
        });
    });

    it("separates additional existing categories from categories that do not exist", () => {
        expect(
            resolveMarkdownImportCategory(["Docs", "Notes", "Missing"], [
                { value: 2, label: "Docs" },
                { value: 3, label: "Notes" },
            ])
        ).toEqual({
            typeId: 2,
            matchedName: "Docs",
            additionalMatchedNames: ["Notes"],
            unmatchedNames: ["Missing"],
        });
    });

    it("defaults an imported category when the current category is missing or invalid", () => {
        [undefined, null, 0, -1].forEach((typeId) => {
            const articleWithInvalidTypeId = {
                ...article,
                typeId,
            } as unknown as Parameters<typeof getDefaultMarkdownImportFields>[1];

            expect(getDefaultMarkdownImportFields(preview, articleWithInvalidTypeId, "current").has("category")).toBe(
                true
            );
        });
    });

    it("applies a required user-selected category even when front matter has no category", () => {
        const previewWithoutCategory = parseMarkdownImportText("Imported body", "post.md");
        const defaultFields = getDefaultMarkdownImportFields(
            previewWithoutCategory,
            { ...article, typeId: undefined },
            "current"
        );

        expect(defaultFields.has("category")).toBe(false);
        expect(getMarkdownImportApplyFields(defaultFields, "current", true, 2)).toEqual(
            new Set<MarkdownImportField>(["category"])
        );
    });

    it("replaces an allowed draft atomically while retaining article identity and status", () => {
        const imported = buildMarkdownImportedArticle({
            article: { ...article, logId: 42, alias: "current-alias" },
            preview,
            selectedFields: new Set<MarkdownImportField>(["title", "keywords"]),
            selectedTypeId: 2,
            target: "current",
            html: "<p>Imported body</p>",
        });

        expect(imported).toEqual({
            ...article,
            logId: 42,
            alias: "current-alias",
            title: "Imported",
            keywords: "one,two",
            markdown: "Imported body",
            content: "<p>Imported body</p>",
        });
    });

    it("builds a current-article patch without captured identity or synchronization metadata", () => {
        expect(
            buildMarkdownImportedPatch({
                preview,
                selectedFields: new Set<MarkdownImportField>(["title", "keywords"]),
                selectedTypeId: 2,
                html: "<p>Imported body</p>",
            })
        ).toEqual({
            title: "Imported",
            keywords: "one,two",
            markdown: "Imported body",
            content: "<p>Imported body</p>",
        });
    });

    it("treats editor snapshots and user-entered metadata as current content but ignores a preselected category", () => {
        const blankArticle = {
            title: "",
            markdown: "stale body",
            rubbish: true,
            version: -1,
        };

        expect(getMarkdownImportTargetPolicy(blankArticle, "").initialTarget).toBe("current");
        expect(getMarkdownImportTargetPolicy(blankArticle, "live editor body").initialTarget).toBe("newDraft");
        expect(getMarkdownImportTargetPolicy({ ...blankArticle, markdown: "", title: "Metadata only" }).initialTarget).toBe(
            "newDraft"
        );
        expect(getMarkdownImportTargetPolicy({ ...blankArticle, markdown: "", typeId: 2 })).toEqual({
            initialTarget: "current",
            currentAllowed: true,
            newDraftAllowed: false,
            dangerousCurrentReplace: false,
        });
    });
});
