import { parse as parseYaml } from "yaml";
import { ArticleEntry } from "./index.types";

export const MARKDOWN_IMPORT_MAX_FILE_SIZE = 2 * 1024 * 1024;

const MAX_FRONT_MATTER_SIZE = 64 * 1024;
const MAX_FRONT_MATTER_DEPTH = 8;
const MAX_FRONT_MATTER_NODES = 200;
const MAX_FRONT_MATTER_FIELDS = 64;
const DANGEROUS_KEYS = new Set(["__proto__", "prototype", "constructor"]);
const IGNORED_METADATA_KEYS = new Set([
    "date",
    "updated",
    "draft",
    "published",
    "sticky",
    "weight",
    "cover",
    "thumbnail",
    "type",
    "layout",
    "kind",
    "permalink",
]);
const SUPPORTED_METADATA_KEYS = new Set([
    "title",
    "slug",
    "alias",
    "excerpt",
    "description",
    "summary",
    "tags",
    "keywords",
    "category",
    "categories",
]);

export type MarkdownImportErrorCode =
    | "invalid-extension"
    | "too-large"
    | "invalid-utf8"
    | "empty-file"
    | "binary-file"
    | "unclosed-front-matter"
    | "invalid-front-matter"
    | "front-matter-root"
    | "front-matter-too-complex";

export class MarkdownImportError extends Error {
    code: MarkdownImportErrorCode;

    constructor(code: MarkdownImportErrorCode, message?: string) {
        super(message || code);
        this.name = "MarkdownImportError";
        this.code = code;
    }
}

export type MarkdownImportMappedMetadata = {
    title: string;
    titleSource: "frontMatter" | "fileName";
    alias?: string;
    digest?: string;
    keywords?: string;
    categoryNames: string[];
};

export type MarkdownImportResourceSummary = {
    imageReferences: string[];
    remoteImages: string[];
    remoteHosts: string[];
    relativeImages: string[];
    siteAbsoluteImages: string[];
    embeddedImages: string[];
    unsupportedImages: string[];
};

export type MarkdownImportPreview = {
    fileName: string;
    fileSize: number;
    markdown: string;
    frontMatterPresent: boolean;
    metadata: MarkdownImportMappedMetadata;
    ignoredFields: string[];
    unknownFields: string[];
    invalidFields: string[];
    resources: MarkdownImportResourceSummary;
};

export type MarkdownImportField = "title" | "alias" | "digest" | "keywords" | "category";
export type MarkdownImportTarget = "current" | "newDraft";

export type MarkdownImportTargetPolicy = {
    initialTarget: MarkdownImportTarget;
    currentAllowed: boolean;
    newDraftAllowed: boolean;
    dangerousCurrentReplace: boolean;
    forcedNewDraftReason?: "published" | "private" | "contentConflict";
};

export type ArticleTypeOption = {
    value: number;
    label: string;
};

export type MarkdownImportCategoryResolution = {
    typeId?: number;
    matchedName?: string;
    additionalMatchedNames: string[];
    unmatchedNames: string[];
};

export type MarkdownImportedArticlePatch = {
    markdown: string;
    content: string;
    title?: string;
    alias?: string;
    digest?: string;
    keywords?: string;
    typeId?: number;
};

const normalizeKey = (key: string) => key.trim().toLowerCase();

const getFileTitle = (fileName: string) => {
    const title = fileName.replace(/\.(?:md|markdown)$/i, "").trim();
    return (title || fileName).slice(0, 100);
};

const validateFrontMatterComplexity = (value: unknown) => {
    let nodes = 0;
    const visited = new WeakSet<object>();

    const visit = (entry: unknown, depth: number) => {
        nodes += 1;
        if (depth > MAX_FRONT_MATTER_DEPTH || nodes > MAX_FRONT_MATTER_NODES) {
            throw new MarkdownImportError("front-matter-too-complex");
        }
        if (!entry || typeof entry !== "object") {
            return;
        }
        if (visited.has(entry)) {
            return;
        }
        visited.add(entry);
        if (Array.isArray(entry)) {
            entry.forEach((item) => visit(item, depth + 1));
            return;
        }
        Object.entries(entry).forEach(([key, child]) => {
            if (DANGEROUS_KEYS.has(normalizeKey(key))) {
                throw new MarkdownImportError("front-matter-too-complex");
            }
            visit(child, depth + 1);
        });
    };

    visit(value, 0);
};

const toStringValue = (
    value: unknown,
    field: string,
    invalidFields: Set<string>,
    maximumLength: number
): string | undefined => {
    if (value === undefined || value === null || value === "") {
        return undefined;
    }
    if (typeof value !== "string") {
        invalidFields.add(field);
        return undefined;
    }
    const normalized = value.trim();
    if (!normalized) {
        return undefined;
    }
    if (normalized.length > maximumLength) {
        invalidFields.add(field);
        return undefined;
    }
    return normalized;
};

const toStringList = (
    value: unknown,
    field: string,
    invalidFields: Set<string>,
    maximumItems: number
): string[] => {
    if (value === undefined || value === null || value === "") {
        return [];
    }
    const entries =
        typeof value === "string"
            ? value.split(",")
            : Array.isArray(value) && value.every((entry) => typeof entry === "string")
            ? value
            : undefined;
    if (!entries) {
        invalidFields.add(field);
        return [];
    }
    const normalized = Array.from(new Set(entries.map((entry) => entry.trim()).filter(Boolean)));
    if (normalized.length > maximumItems || normalized.some((entry) => entry.length > 100)) {
        invalidFields.add(field);
        return [];
    }
    return normalized;
};

const collectHtmlImageReferences = (markdown: string) => {
    if (typeof DOMParser === "undefined") {
        return [];
    }
    const document = new DOMParser().parseFromString(markdown, "text/html");
    return Array.from(document.querySelectorAll("img[src]"))
        .map((image) => image.getAttribute("src")?.trim() || "")
        .filter(Boolean);
};

const collectMarkdownImageReferences = (markdown: string) => {
    const references: string[] = [];
    const inlineImagePattern = /!\[[^\]]*]\(\s*(?:<([^>]+)>|([^\s)]+))(?:\s+["'][^"']*["'])?\s*\)/g;
    let match = inlineImagePattern.exec(markdown);
    while (match) {
        const reference = (match[1] || match[2] || "").trim();
        if (reference) {
            references.push(reference);
        }
        match = inlineImagePattern.exec(markdown);
    }

    const normalizeReferenceLabel = (label: string) => label.trim().replace(/\s+/g, " ").toLowerCase();
    const definitions = new Map<string, string>();
    const definitionPattern =
        /^[ \t]{0,3}\[([^\]\r\n]+)]\s*:\s*(?:<([^>\r\n]+)>|(\S+))(?:[ \t]+(?:"[^"\r\n]*"|'[^'\r\n]*'|\([^)\r\n]*\)))?[ \t]*$/gm;
    let definitionMatch = definitionPattern.exec(markdown);
    while (definitionMatch) {
        definitions.set(
            normalizeReferenceLabel(definitionMatch[1]),
            (definitionMatch[2] || definitionMatch[3] || "").trim()
        );
        definitionMatch = definitionPattern.exec(markdown);
    }

    const referenceImagePattern = /!\[([^\]\r\n]*)]\[([^\]\r\n]*)]/g;
    let referenceMatch = referenceImagePattern.exec(markdown);
    while (referenceMatch) {
        const label = normalizeReferenceLabel(referenceMatch[2] || referenceMatch[1]);
        const reference = definitions.get(label);
        if (reference) {
            references.push(reference);
        }
        referenceMatch = referenceImagePattern.exec(markdown);
    }

    const shortcutImagePattern = /!\[([^\]\r\n]+)](?![[(])/g;
    let shortcutMatch = shortcutImagePattern.exec(markdown);
    while (shortcutMatch) {
        const reference = definitions.get(normalizeReferenceLabel(shortcutMatch[1]));
        if (reference) {
            references.push(reference);
        }
        shortcutMatch = shortcutImagePattern.exec(markdown);
    }
    return references.concat(collectHtmlImageReferences(markdown));
};

export const collectMarkdownImportResources = (markdown: string): MarkdownImportResourceSummary => {
    const imageReferences = Array.from(new Set(collectMarkdownImageReferences(markdown)));
    const remoteImages: string[] = [];
    const relativeImages: string[] = [];
    const siteAbsoluteImages: string[] = [];
    const embeddedImages: string[] = [];
    const unsupportedImages: string[] = [];

    imageReferences.forEach((reference) => {
        if (/^https?:\/\//i.test(reference) || reference.startsWith("//")) {
            remoteImages.push(reference);
        } else if (/^data:/i.test(reference)) {
            embeddedImages.push(reference);
        } else if (reference.startsWith("/")) {
            siteAbsoluteImages.push(reference);
        } else if (/^[a-z][a-z0-9+.-]*:/i.test(reference)) {
            unsupportedImages.push(reference);
        } else {
            relativeImages.push(reference);
        }
    });

    const remoteHosts = Array.from(
        new Set(
            remoteImages
                .map((reference) => {
                    try {
                        return new URL(reference.startsWith("//") ? `https:${reference}` : reference).host;
                    } catch (_error) {
                        return "";
                    }
                })
                .filter(Boolean)
        )
    );

    return {
        imageReferences,
        remoteImages,
        remoteHosts,
        relativeImages,
        siteAbsoluteImages,
        embeddedImages,
        unsupportedImages,
    };
};

const parseFrontMatter = (source: string) => {
    const opener = /^---[ \t]*(?:\r?\n|$)/.exec(source);
    if (!opener) {
        return {
            body: source,
            fields: new Map<string, { originalKey: string; value: unknown }>(),
            present: false,
        };
    }
    const closingPattern = /^---[ \t]*(?:\r?\n|$)/gm;
    closingPattern.lastIndex = opener[0].length;
    const closing = closingPattern.exec(source);
    if (!closing) {
        throw new MarkdownImportError("unclosed-front-matter");
    }
    const yamlSource = source.slice(opener[0].length, closing.index);
    if (yamlSource.length > MAX_FRONT_MATTER_SIZE) {
        throw new MarkdownImportError("front-matter-too-complex");
    }
    let parsed: unknown;
    try {
        parsed = parseYaml(yamlSource, {
            schema: "core",
            customTags: [],
            merge: false,
            maxAliasCount: 20,
        });
    } catch (error) {
        throw new MarkdownImportError(
            "invalid-front-matter",
            error instanceof Error ? error.message : "invalid-front-matter"
        );
    }
    if (parsed === null || parsed === undefined) {
        parsed = {};
    }
    if (Array.isArray(parsed) || typeof parsed !== "object") {
        throw new MarkdownImportError("front-matter-root");
    }
    validateFrontMatterComplexity(parsed);
    const entries = Object.entries(parsed as Record<string, unknown>);
    if (entries.length > MAX_FRONT_MATTER_FIELDS) {
        throw new MarkdownImportError("front-matter-too-complex");
    }
    const fields = new Map<string, { originalKey: string; value: unknown }>();
    entries.forEach(([originalKey, value]) => {
        const key = normalizeKey(originalKey);
        if (DANGEROUS_KEYS.has(key)) {
            throw new MarkdownImportError("front-matter-too-complex");
        }
        fields.set(key, { originalKey, value });
    });
    return {
        body: source.slice(closing.index + closing[0].length),
        fields,
        present: true,
    };
};

const getFirstField = (
    fields: Map<string, { originalKey: string; value: unknown }>,
    keys: string[]
): { key: string; value: unknown } | undefined => {
    for (const key of keys) {
        const field = fields.get(key);
        if (field) {
            return { key: field.originalKey, value: field.value };
        }
    }
    return undefined;
};

export const parseMarkdownImportText = (
    source: string,
    fileName: string,
    fileSize: number = new Blob([source]).size
): MarkdownImportPreview => {
    if (!/\.(?:md|markdown)$/i.test(fileName)) {
        throw new MarkdownImportError("invalid-extension");
    }
    if (fileSize > MARKDOWN_IMPORT_MAX_FILE_SIZE) {
        throw new MarkdownImportError("too-large");
    }
    const normalizedSource = source.charCodeAt(0) === 0xfeff ? source.slice(1) : source;
    if (normalizedSource.includes("\0")) {
        throw new MarkdownImportError("binary-file");
    }
    if (!normalizedSource.trim()) {
        throw new MarkdownImportError("empty-file");
    }

    const frontMatter = parseFrontMatter(normalizedSource);
    const invalidFields = new Set<string>();
    const titleField = getFirstField(frontMatter.fields, ["title"]);
    const aliasField = getFirstField(frontMatter.fields, ["slug", "alias"]);
    const digestField = getFirstField(frontMatter.fields, ["excerpt", "description", "summary"]);
    const keywordsField = getFirstField(frontMatter.fields, ["tags", "keywords"]);
    const categoriesField = getFirstField(frontMatter.fields, ["category", "categories"]);
    const frontMatterTitle = titleField
        ? toStringValue(titleField.value, titleField.key, invalidFields, 100)
        : undefined;
    const alias = aliasField ? toStringValue(aliasField.value, aliasField.key, invalidFields, 64) : undefined;
    const digest = digestField
        ? toStringValue(digestField.value, digestField.key, invalidFields, 10000)
        : undefined;
    const keywordList = keywordsField ? toStringList(keywordsField.value, keywordsField.key, invalidFields, 50) : [];
    let keywords = keywordsField ? keywordList.join(",") : undefined;
    if (keywordsField && keywords && keywords.length > 255) {
        invalidFields.add(keywordsField.key);
        keywords = "";
    }
    const categoryNames = categoriesField
        ? toStringList(categoriesField.value, categoriesField.key, invalidFields, 20)
        : [];
    const ignoredFields: string[] = [];
    const unknownFields: string[] = [];

    frontMatter.fields.forEach((field, key) => {
        if (IGNORED_METADATA_KEYS.has(key)) {
            ignoredFields.push(field.originalKey);
        } else if (!SUPPORTED_METADATA_KEYS.has(key)) {
            unknownFields.push(field.originalKey);
        }
    });

    return {
        fileName,
        fileSize,
        markdown: frontMatter.body,
        frontMatterPresent: frontMatter.present,
        metadata: {
            title: frontMatterTitle || getFileTitle(fileName),
            titleSource: frontMatterTitle ? "frontMatter" : "fileName",
            alias,
            digest,
            keywords,
            categoryNames,
        },
        ignoredFields,
        unknownFields,
        invalidFields: Array.from(invalidFields),
        resources: collectMarkdownImportResources(frontMatter.body),
    };
};

export const readMarkdownImportFile = async (file: File): Promise<MarkdownImportPreview> => {
    if (!/\.(?:md|markdown)$/i.test(file.name)) {
        throw new MarkdownImportError("invalid-extension");
    }
    if (file.size > MARKDOWN_IMPORT_MAX_FILE_SIZE) {
        throw new MarkdownImportError("too-large");
    }
    const buffer =
        typeof file.arrayBuffer === "function"
            ? await file.arrayBuffer()
            : await new Promise<ArrayBuffer>((resolve, reject) => {
                  const reader = new FileReader();
                  reader.onload = () => resolve(reader.result as ArrayBuffer);
                  reader.onerror = () => reject(reader.error);
                  reader.readAsArrayBuffer(file);
              });
    let source: string;
    try {
        source = new TextDecoder("utf-8", { fatal: true }).decode(new Uint8Array(buffer));
    } catch (_error) {
        throw new MarkdownImportError("invalid-utf8");
    }
    return parseMarkdownImportText(source, file.name, file.size);
};

const hasArticleBody = (article: ArticleEntry, currentMarkdown?: string) => {
    const markdown = currentMarkdown === undefined ? article.markdown : currentMarkdown;
    return Boolean(markdown?.trim()) || Boolean(markdown === undefined && article.content?.trim());
};

const hasArticleMetadata = (article: ArticleEntry) =>
    Boolean(
        article.title?.trim() ||
            article.alias?.trim() ||
            article.digest?.trim() ||
            article.keywords?.trim() ||
            article.thumbnail?.trim() ||
            article.recommended === true ||
            article.canComment === false
    );

export const getMarkdownImportTargetPolicy = (
    article: ArticleEntry,
    currentMarkdown?: string,
    contentConflict = false
): MarkdownImportTargetPolicy => {
    const isNew = !article.logId || article.logId <= 0;
    if (contentConflict) {
        return {
            initialTarget: "newDraft",
            currentAllowed: false,
            newDraftAllowed: true,
            dangerousCurrentReplace: false,
            forcedNewDraftReason: "contentConflict",
        };
    }
    if (article.privacy === true) {
        return {
            initialTarget: "newDraft",
            currentAllowed: false,
            newDraftAllowed: true,
            dangerousCurrentReplace: false,
            forcedNewDraftReason: "private",
        };
    }
    if (!isNew && article.rubbish !== true) {
        return {
            initialTarget: "newDraft",
            currentAllowed: false,
            newDraftAllowed: true,
            dangerousCurrentReplace: false,
            forcedNewDraftReason: "published",
        };
    }
    const currentHasContent = hasArticleBody(article, currentMarkdown) || hasArticleMetadata(article);
    if (isNew && !currentHasContent) {
        return {
            initialTarget: "current",
            currentAllowed: true,
            newDraftAllowed: false,
            dangerousCurrentReplace: false,
        };
    }
    return {
        initialTarget: currentHasContent ? "newDraft" : "current",
        currentAllowed: true,
        newDraftAllowed: true,
        dangerousCurrentReplace: currentHasContent,
    };
};

export const resolveMarkdownImportCategory = (
    categoryNames: string[],
    typeOptions: ArticleTypeOption[]
): MarkdownImportCategoryResolution => {
    const matchedCategories = categoryNames
        .map((name) => ({
            name,
            option: typeOptions.find((option) => option.label === name),
        }))
        .filter((entry) => entry.option);
    const matched = matchedCategories[0];
    return {
        typeId: matched?.option?.value,
        matchedName: matched?.name,
        additionalMatchedNames: matchedCategories.slice(1).map((entry) => entry.name),
        unmatchedNames: categoryNames.filter(
            (name) => !typeOptions.some((option) => option.label === name)
        ),
    };
};

export const getDefaultMarkdownImportFields = (
    preview: MarkdownImportPreview,
    article: ArticleEntry,
    target: MarkdownImportTarget
): Set<MarkdownImportField> => {
    const fields = new Set<MarkdownImportField>();
    const include = (field: MarkdownImportField, importedValue: string | undefined, currentValue: string | undefined) => {
        if (importedValue && (target === "newDraft" || !currentValue?.trim())) {
            fields.add(field);
        }
    };
    include("title", preview.metadata.title, article.title);
    include("alias", preview.metadata.alias, article.alias);
    include("digest", preview.metadata.digest, article.digest);
    include("keywords", preview.metadata.keywords, article.keywords);
    if (
        preview.metadata.categoryNames.length > 0 &&
        (target === "newDraft" || article.typeId === undefined || article.typeId === null || article.typeId <= 0)
    ) {
        fields.add("category");
    }
    if (target === "newDraft") {
        fields.add("title");
    }
    return fields;
};

export const getMarkdownImportApplyFields = (
    selectedFields: ReadonlySet<MarkdownImportField>,
    target: MarkdownImportTarget,
    categoryRequired: boolean,
    selectedTypeId?: number
): Set<MarkdownImportField> => {
    const fields = new Set(selectedFields);
    if (target === "newDraft") {
        fields.add("title");
    }
    if (categoryRequired && selectedTypeId !== undefined && selectedTypeId > 0) {
        fields.add("category");
    }
    return fields;
};

export const buildMarkdownImportedPatch = ({
    preview,
    selectedFields,
    selectedTypeId,
    html,
}: {
    preview: MarkdownImportPreview;
    selectedFields: Set<MarkdownImportField>;
    selectedTypeId?: number;
    html: string;
}): MarkdownImportedArticlePatch => {
    const patch: MarkdownImportedArticlePatch = {
        markdown: preview.markdown,
        content: html,
    };
    if (selectedFields.has("title")) {
        patch.title = preview.metadata.title;
    }
    if (selectedFields.has("alias") && preview.metadata.alias !== undefined) {
        patch.alias = preview.metadata.alias;
    }
    if (selectedFields.has("digest") && preview.metadata.digest !== undefined) {
        patch.digest = preview.metadata.digest;
    }
    if (selectedFields.has("keywords") && preview.metadata.keywords !== undefined) {
        patch.keywords = preview.metadata.keywords;
    }
    if (selectedFields.has("category") && selectedTypeId !== undefined) {
        patch.typeId = selectedTypeId;
    }
    return patch;
};

export const buildMarkdownImportedArticle = ({
    article,
    preview,
    selectedFields,
    selectedTypeId,
    target,
    html,
}: {
    article: ArticleEntry;
    preview: MarkdownImportPreview;
    selectedFields: Set<MarkdownImportField>;
    selectedTypeId?: number;
    target: MarkdownImportTarget;
    html: string;
}): ArticleEntry => {
    const importedPatch = buildMarkdownImportedPatch({
        preview,
        selectedFields,
        selectedTypeId,
        html,
    });
    const nextArticle: ArticleEntry =
        target === "current"
            ? {
                  ...article,
                  ...importedPatch,
              }
            : {
                  title: preview.metadata.title,
                  markdown: preview.markdown,
                  content: html,
                  typeId: selectedTypeId,
                  keywords: "",
                  canComment: article.canComment !== false,
                  recommended: false,
                  privacy: false,
                  rubbish: true,
                  version: -1,
                  editorType: "markdown",
              };

    if (target === "newDraft") {
        Object.assign(nextArticle, importedPatch);
        if (selectedTypeId !== undefined) {
            nextArticle.typeId = selectedTypeId;
        }
    }
    return nextArticle;
};
