import { ApiOutlined, EditOutlined, FileImageOutlined, FolderOpenOutlined, SkinOutlined } from "@ant-design/icons";
import { getAdminDashboardRouteSearchItems } from "../components/admin-dashboard-routes";
import {
    cacheIgnoreReloadTime,
    formatLabelValue,
    getBackendServerUrl,
    getRes,
    tryAppendBackendServerUrl,
} from "../utils/constants";
import type {
    SpotlightCommand,
    SpotlightItem,
    SpotlightRenderImageIcon,
    SpotlightSearchContext,
    SpotlightSource,
} from "./spotlight-search-types";

const SOURCE_RESULT_LIMIT = 5;
const REFERENCE_CACHE_TTL = 30000;

type SpotlightReferenceCache<T> = {
    rows: T[];
    signature: string;
    requestedAt: number;
    inFlight?: Promise<void>;
};

const templateSearchCache: SpotlightReferenceCache<TemplateSearchEntry> = {
    rows: [],
    signature: "",
    requestedAt: 0,
};

const pluginSearchCache: SpotlightReferenceCache<PluginSearchEntry> = {
    rows: [],
    signature: "",
    requestedAt: 0,
};

type TemplateSearchEntry = {
    name?: string;
    shortTemplate?: string;
    digest?: string;
    author?: string;
    tags?: string[];
};

type PluginIconEntry =
    | string
    | {
          src?: string;
          url?: string;
          href?: string;
          base64?: string;
          data?: string;
      };

type PluginSearchEntry = {
    id?: string;
    name?: string;
    shortName?: string;
    description?: string;
    desc?: string;
    summary?: string;
    author?: string;
    indexPage?: string;
    icons?: PluginIconEntry[];
    previewImageBase64?: string;
    services?: string[];
    capabilities?: { key?: string; label?: string; description?: string }[];
};

type ArticleTypeSearchEntry = {
    id?: number | string;
    typeId?: number | string;
    typeName?: string;
    alias?: string;
    remark?: string;
};

function shouldRefreshReferenceCache<T>(cache: SpotlightReferenceCache<T>) {
    return !cache.inFlight && Date.now() - cache.requestedAt > REFERENCE_CACHE_TTL;
}

function updateReferenceCache<T>(
    cache: SpotlightReferenceCache<T>,
    rows: T[],
    onSourceRefresh: SpotlightSearchContext["onSourceRefresh"]
) {
    const signature = JSON.stringify(rows);
    if (signature === cache.signature) {
        return;
    }
    cache.rows = rows;
    cache.signature = signature;
    onSourceRefresh?.();
}

const articleCreateCommandKeywords = [
    "article",
    "create article",
    "new article",
    "write article",
    "新建文章",
    "创建文章",
    "写文章",
    "文章",
];

const includesKeyword = (value: unknown, keyword: string) => {
    if (value === undefined || value === null) {
        return false;
    }
    return String(value).toLowerCase().includes(keyword);
};

const arrayIncludesKeyword = (values: unknown, keyword: string) => {
    return Array.isArray(values) && values.some((value) => includesKeyword(value, keyword));
};

const articleCreateCommandMatches = (keyword: string) => {
    if (!keyword) {
        return false;
    }
    return articleCreateCommandKeywords.some((value) => includesKeyword(value, keyword));
};

const articleTypeMatches = (row: ArticleTypeSearchEntry, keyword: string) => {
    return (
        includesKeyword(row.typeName, keyword) ||
        includesKeyword(row.alias, keyword) ||
        includesKeyword(row.remark, keyword)
    );
};

const articleTypeId = (row: ArticleTypeSearchEntry) => {
    const rawId = row.id || row.typeId;
    const id = rawId ? Number(rawId) : undefined;
    return id && Number.isFinite(id) && id > 0 ? id : undefined;
};

const buildQueryPath = (path: string, params: Record<string, string>) => {
    return `${path}?${new URLSearchParams(params).toString()}`;
};

const buildArticleCreatePath = (typeId?: number) => {
    const params: Record<string, string> = {
        [cacheIgnoreReloadTime]: String(Date.now()),
    };
    if (typeId) {
        params.typeId = String(typeId);
    }
    return buildQueryPath("/article-edit", params);
};

const loadArticleCreateItems = async (
    { axiosInstance, normalizedKeyword }: SpotlightSearchContext,
    showDefault: boolean
): Promise<SpotlightItem[]> => {
    const commandMatches = articleCreateCommandMatches(normalizedKeyword);
    let rows: ArticleTypeSearchEntry[] = [];
    try {
        const typeRes = await axiosInstance.get("/api/admin/article", {
            params: { page: 1, size: 1 },
            showError: false,
        } as any);
        rows = typeRes?.data?.data?.types || [];
    } catch (error) {
        rows = [];
    }
    if (rows.length === 0) {
        if (!showDefault && !commandMatches) {
            return [];
        }
        return [
            {
                id: "article-create",
                title: getRes().articleEdit.new,
                subTitle: getRes().pleaseChoose + getRes().articleType.title,
                path: buildArticleCreatePath(),
                icon: <EditOutlined />,
                keywords: articleCreateCommandKeywords,
                type: "action" as const,
                persist: false,
            },
        ];
    }

    return rows
        .filter(
            (row) => articleTypeId(row) && (showDefault || commandMatches || articleTypeMatches(row, normalizedKeyword))
        )
        .sort(
            (a, b) =>
                Number(articleTypeMatches(b, normalizedKeyword)) - Number(articleTypeMatches(a, normalizedKeyword))
        )
        .slice(0, SOURCE_RESULT_LIMIT)
        .map((row) => {
            const typeId = articleTypeId(row);
            const typeName = row.typeName || getRes().articleType.title;
            return {
                id: `article-create-${typeId}`,
                title: `${getRes().articleEdit.new} - ${typeName}`,
                subTitle: formatLabelValue(getRes().type, typeName),
                path: buildArticleCreatePath(typeId),
                icon: <EditOutlined />,
                keywords: [...articleCreateCommandKeywords, typeName, row.alias || ""],
                type: "action" as const,
                persist: false,
            };
        });
};

const normalizePluginImageSrc = (src?: string) => {
    if (!src) {
        return undefined;
    }
    if (src.startsWith("data:") || src.startsWith("http://") || src.startsWith("https://")) {
        return src;
    }
    if (src.startsWith("/")) {
        return tryAppendBackendServerUrl(src);
    }
    return getBackendServerUrl() + "admin/plugins/" + src.replace(/^\.?\//, "");
};

const pluginIconSrc = (plugin: PluginSearchEntry) => {
    const icon = plugin.icons?.find(Boolean);
    const iconSrc =
        typeof icon === "string" ? icon : icon?.src || icon?.url || icon?.href || icon?.base64 || icon?.data;
    return normalizePluginImageSrc(iconSrc || plugin.previewImageBase64);
};

const pluginDescription = (plugin: PluginSearchEntry) => {
    return plugin.description || plugin.desc || plugin.summary;
};

const templateMatches = (row: TemplateSearchEntry, keyword: string) => {
    return (
        includesKeyword(row.name, keyword) ||
        includesKeyword(row.shortTemplate, keyword) ||
        includesKeyword(row.digest, keyword) ||
        includesKeyword(row.author, keyword) ||
        arrayIncludesKeyword(row.tags, keyword)
    );
};

const templateToSpotlightItem = (row: TemplateSearchEntry): SpotlightItem => ({
    id: `template-${row.shortTemplate || row.name}`,
    title: row.name || row.shortTemplate || getRes().websiteTemplate.title,
    subTitle: row.shortTemplate || row.digest,
    path: buildQueryPath("/template", { shortTemplate: row.shortTemplate || "" }),
    icon: <SkinOutlined />,
    keywords: [],
    type: "template",
});

const pluginToSpotlightItem = (row: PluginSearchEntry, renderImageIcon: SpotlightRenderImageIcon): SpotlightItem => {
    const iconSrc = pluginIconSrc(row);
    const title = row.name || row.shortName || getRes().plugin.title;
    const page = `${row.shortName || ""}/${row.indexPage || "index"}`;
    return {
        id: `plugin-${row.shortName || row.id}`,
        title,
        subTitle: pluginDescription(row),
        path: buildQueryPath("/plugin", { page }),
        icon: renderImageIcon(iconSrc, title, <ApiOutlined />),
        iconSrc,
        keywords: [],
        type: "plugin",
    };
};

const refreshTemplateRows = ({ axiosInstance, onSourceRefresh }: SpotlightSearchContext) => {
    if (!shouldRefreshReferenceCache(templateSearchCache)) {
        return;
    }
    templateSearchCache.requestedAt = Date.now();
    templateSearchCache.inFlight = axiosInstance
        .get("/api/admin/template", { showError: false } as any)
        .then((templateRes) => {
            const rows: TemplateSearchEntry[] = templateRes?.data?.data || [];
            updateReferenceCache(templateSearchCache, rows, onSourceRefresh);
        })
        .catch(() => undefined)
        .finally(() => {
            templateSearchCache.inFlight = undefined;
        });
};

const refreshPluginRows = ({ axiosInstance, onSourceRefresh }: SpotlightSearchContext) => {
    if (!shouldRefreshReferenceCache(pluginSearchCache)) {
        return;
    }
    pluginSearchCache.requestedAt = Date.now();
    pluginSearchCache.inFlight = axiosInstance
        .get("/admin/plugins/api/plugins", { showError: false } as any)
        .then((pluginRes) => {
            const rows: PluginSearchEntry[] = pluginRes?.data?.plugins || [];
            updateReferenceCache(pluginSearchCache, rows, onSourceRefresh);
        })
        .catch(() => undefined)
        .finally(() => {
            pluginSearchCache.inFlight = undefined;
        });
};

const getTemplateRows = (context: SpotlightSearchContext) => {
    refreshTemplateRows(context);
    return templateSearchCache.rows;
};

const getPluginRows = (context: SpotlightSearchContext) => {
    refreshPluginRows(context);
    return pluginSearchCache.rows;
};

const withSource = (sourceId: string, items: SpotlightItem[]) =>
    items.map((item) => ({
        ...item,
        sourceId: item.sourceId || sourceId,
    }));

const createRouteSource = (): SpotlightSource => {
    const items = withSource(
        "route",
        getAdminDashboardRouteSearchItems().map((item) => ({
            ...item,
            type: "route" as const,
        }))
    );
    return {
        id: "route",
        empty: () => items.slice(0, SOURCE_RESULT_LIMIT),
        search: ({ normalizedKeyword }) =>
            items.filter(
                (item) =>
                    includesKeyword(item.title, normalizedKeyword) ||
                    item.keywords.some((keyword) => includesKeyword(keyword, normalizedKeyword))
            ),
    };
};

const articleCreateCommandSource: SpotlightSource = {
    id: "article-create",
    empty: (context) => loadArticleCreateItems(context, true),
    search: (context) => loadArticleCreateItems(context, false),
};

const articleSource: SpotlightSource = {
    id: "article",
    search: async ({ axiosInstance, keyword }) => {
        const articleRes = await axiosInstance.get("/api/admin/article", {
            params: { key: keyword, page: 1, size: 5 },
        });
        const rows = articleRes?.data?.data?.rows || [];
        return rows.map((row: any) => ({
            id: `article-${row.id}`,
            title: row.title,
            subTitle: row.typeName,
            path: `/article-edit?id=${row.id}`,
            icon: <EditOutlined />,
            keywords: [],
            type: "article",
        }));
    },
};

const fileSource: SpotlightSource = {
    id: "file",
    search: async ({ axiosInstance, keyword }) => {
        const fileRes = await axiosInstance.get("/api/admin/file-manager/search", { params: { key: keyword } });
        const rows = fileRes?.data?.data || [];
        return rows.map((row: any) => ({
            id: `file-${row.path}`,
            title: row.name,
            subTitle: row.path,
            path: `/file-manager?path=${row.path}`,
            icon: row.type === "directory" ? <FolderOpenOutlined /> : <FileImageOutlined />,
            iconVariant: row.type === "directory" ? "directory" : "file",
            keywords: [],
            type: "file",
        }));
    },
};

const templateSource: SpotlightSource = {
    id: "template",
    empty: (context) => {
        const rows = getTemplateRows(context);
        return rows.slice(0, SOURCE_RESULT_LIMIT).map(templateToSpotlightItem);
    },
    search: (context) => {
        const rows = getTemplateRows(context);
        return rows
            .filter((row) => templateMatches(row, context.normalizedKeyword))
            .slice(0, SOURCE_RESULT_LIMIT)
            .map(templateToSpotlightItem);
    },
};

const pluginSource: SpotlightSource = {
    id: "plugin",
    empty: (context) => {
        const rows = getPluginRows(context);
        return rows.slice(0, SOURCE_RESULT_LIMIT).map((row) => pluginToSpotlightItem(row, context.renderImageIcon));
    },
    search: (context) => {
        const rows = getPluginRows(context);
        return rows
            .filter((row) => {
                const description = pluginDescription(row);
                return (
                    includesKeyword(row.name, context.normalizedKeyword) ||
                    includesKeyword(row.shortName, context.normalizedKeyword) ||
                    includesKeyword(description, context.normalizedKeyword) ||
                    includesKeyword(row.author, context.normalizedKeyword) ||
                    arrayIncludesKeyword(row.services, context.normalizedKeyword) ||
                    (Array.isArray(row.capabilities) &&
                        row.capabilities.some(
                            (capability) =>
                                includesKeyword(capability.key, context.normalizedKeyword) ||
                                includesKeyword(capability.label, context.normalizedKeyword) ||
                                includesKeyword(capability.description, context.normalizedKeyword)
                        ))
                );
            })
            .slice(0, SOURCE_RESULT_LIMIT)
            .map((row) => pluginToSpotlightItem(row, context.renderImageIcon));
    },
};

export const createSpotlightCommandSource = (commands: SpotlightCommand[], sourceId = "command"): SpotlightSource => ({
    id: sourceId,
    search: ({ normalizedKeyword }) =>
        commands
            .filter(
                (command) =>
                    includesKeyword(command.title, normalizedKeyword) ||
                    includesKeyword(command.subTitle, normalizedKeyword) ||
                    (command.keywords || []).some((keyword) => includesKeyword(keyword, normalizedKeyword))
            )
            .map((command) => ({
                id: command.id,
                title: command.title,
                subTitle: command.subTitle,
                icon: command.icon,
                keywords: command.keywords || [],
                type: command.type || "action",
                persist: command.persist ?? false,
                onSelect: command.execute,
            })),
});

export const createSpotlightSearchSources = (): SpotlightSource[] => [
    articleCreateCommandSource,
    createRouteSource(),
    templateSource,
    pluginSource,
    articleSource,
    fileSource,
];

export const applySpotlightSourceId = withSource;
