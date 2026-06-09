const MARKDOWN_IMAGE_LINK_PATTERN = /!\[[^\]]*]\(([^)\s]+)(?:\s+["'][^"']*["'])?\)/g;
const MARKDOWN_TEXT_LINK_PATTERN = /(^|[^!])\[[^\]]+]\(([^)\s]+)(?:\s+["'][^"']*["'])?\)/g;
const MARKDOWN_INLINE_LINK_PATTERN = /!?\[[^\]]*]\(([^)\s]+)(?:\s+["'][^"']*["'])?\)/g;
const BARE_URL_PATTERN = /\bhttps?:\/\/[^\s<>"')]+/gi;

export type MarkdownReferenceSummary = {
    imageReferenceCount: number;
    imageReferences: string[];
    linkReferenceCount: number;
    linkReferences: string[];
    externalLinkCount: number;
    externalLinks: string[];
};

const normalizeMarkdownReferenceTarget = (target: string) => target.trim().replace(/[.,;:]+$/, "");

const collectPatternMatches = (value: string, pattern: RegExp, groupIndex: number) => {
    const matches = new Set<string>();
    pattern.lastIndex = 0;
    let match = pattern.exec(value);
    while (match) {
        const target = normalizeMarkdownReferenceTarget(match[groupIndex] || "");
        if (target) {
            matches.add(target);
        }
        match = pattern.exec(value);
    }
    return matches;
};

export const collectMarkdownReferenceSummary = (markdown: string): MarkdownReferenceSummary => {
    const imageTargets = collectPatternMatches(markdown, MARKDOWN_IMAGE_LINK_PATTERN, 1);
    const linkTargets = collectPatternMatches(markdown, MARKDOWN_TEXT_LINK_PATTERN, 2);
    const markdownWithoutInlineLinks = markdown.replace(MARKDOWN_INLINE_LINK_PATTERN, "");
    const bareUrlTargets = collectPatternMatches(markdownWithoutInlineLinks, BARE_URL_PATTERN, 0);
    const allLinkTargets = new Set(Array.from(linkTargets).concat(Array.from(bareUrlTargets)));
    const externalTargets = new Set(Array.from(allLinkTargets).filter((target) => /^https?:\/\//i.test(target)));
    const imageReferences = Array.from(imageTargets);
    const linkReferences = Array.from(allLinkTargets);
    const externalLinks = Array.from(externalTargets);
    return {
        imageReferenceCount: imageReferences.length,
        imageReferences,
        linkReferenceCount: linkReferences.length,
        linkReferences,
        externalLinkCount: externalLinks.length,
        externalLinks,
    };
};
