export type TemplateEntry = {
    template: string;
    deleteAble: boolean;
    builtIn: boolean;
    use: boolean;
    name: string;
    shortTemplate: string;
    previewImage: string;
    adminPreviewImage: string;
    preview: boolean;
    digest: string;
    version: string;
    author?: string;
    url?: string;
    tags: string[];
};

export type TemplateFilter = "all" | "active" | "preview" | "builtin" | "removable";

export const getTemplatePreviewUrl = (src: string | undefined, backendUrl: string): string | undefined => {
    if (!src || /^https?:\/\//i.test(src) || src.startsWith("//")) return src;
    if (/^[a-z][a-z\d+.-]*:/i.test(src)) return undefined;
    const base = backendUrl.endsWith("/") ? backendUrl : `${backendUrl}/`;
    const contextPath = new URL(base, "https://zrlog.invalid").pathname;
    const relativePath = src.startsWith(contextPath) ? src.slice(contextPath.length) : src.replace(/^\/+/, "");
    return base + relativePath;
};

export const filterTemplates = (templates: TemplateEntry[], filter: TemplateFilter, search: string) => {
    const keyword = search.trim().toLocaleLowerCase();
    return templates
        .filter((template) => {
            if (filter === "active" && !template.use) return false;
            if (filter === "preview" && !template.preview) return false;
            if (filter === "builtin" && !template.builtIn) return false;
            if (filter === "removable" && (!template.deleteAble || template.builtIn || template.use)) return false;
            return [template.name, template.shortTemplate, template.author, template.digest, ...(template.tags || [])]
                .join(" ")
                .toLocaleLowerCase()
                .includes(keyword);
        })
        .sort((a, b) => Number(b.use) - Number(a.use));
};

export const getTemplateAuthorUrl = (value?: string): string | undefined => {
    const href = value?.trim();
    if (!href) return undefined;
    try {
        const url = new URL(href.startsWith("//") ? `https:${href}` : href);
        return url.protocol === "http:" || url.protocol === "https:" ? href : undefined;
    } catch {
        return undefined;
    }
};
