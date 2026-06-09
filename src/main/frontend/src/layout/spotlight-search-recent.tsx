import {
    ApiOutlined,
    EditOutlined,
    FileImageOutlined,
    FolderOpenOutlined,
    SearchOutlined,
    SkinOutlined,
} from "@ant-design/icons";
import type { SpotlightItem, SpotlightRecentItem, SpotlightRenderImageIcon } from "./spotlight-search-types";

const RECENT_STORAGE_KEY = "zrlog_spotlight_recent";

export const spotlightItemStorageKey = (item: Pick<SpotlightItem, "id" | "type" | "sourceId">) =>
    `${item.sourceId || item.type}:${item.id}`;

const readRecentItems = (): SpotlightRecentItem[] => {
    try {
        const stored = JSON.parse(localStorage.getItem(RECENT_STORAGE_KEY) || "[]");
        return Array.isArray(stored) ? stored : [];
    } catch (e) {
        return [];
    }
};

export const saveSpotlightRecentItem = (item: SpotlightItem) => {
    if (item.persist === false || !item.path) {
        return;
    }
    try {
        const stored = readRecentItems();
        const recentItem: SpotlightRecentItem = {
            id: item.id,
            title: item.title,
            subTitle: item.subTitle,
            path: item.path,
            type: item.type,
            sourceId: item.sourceId,
            iconSrc: item.iconSrc,
            iconVariant: item.iconVariant,
        };
        const recentKey = spotlightItemStorageKey(recentItem);
        const newStored = [recentItem, ...stored.filter((row) => spotlightItemStorageKey(row) !== recentKey)].slice(
            0,
            5
        );
        localStorage.setItem(RECENT_STORAGE_KEY, JSON.stringify(newStored));
    } catch (e) {
        console.error(e);
    }
};

const recentIcon = (
    item: SpotlightRecentItem,
    currentItem: SpotlightItem | undefined,
    renderImageIcon: SpotlightRenderImageIcon
) => {
    if (currentItem?.icon) {
        return currentItem.icon;
    }
    switch (item.type) {
        case "article":
            return <EditOutlined />;
        case "file":
            return item.iconVariant === "directory" ? <FolderOpenOutlined /> : <FileImageOutlined />;
        case "template":
            return <SkinOutlined />;
        case "plugin":
            return renderImageIcon(item.iconSrc, item.title, <ApiOutlined />);
        default:
            return <SearchOutlined />;
    }
};

export const readSpotlightRecentItems = (
    emptyItems: SpotlightItem[],
    renderImageIcon: SpotlightRenderImageIcon
): SpotlightItem[] => {
    const currentItems = new Map<string, SpotlightItem>();
    emptyItems.forEach((item) => {
        currentItems.set(spotlightItemStorageKey(item), item);
        currentItems.set(`${item.type}:${item.id}`, item);
    });

    return readRecentItems()
        .filter((item) => item.id && item.title && item.path)
        .map((item) => {
            const currentItem =
                currentItems.get(spotlightItemStorageKey(item)) || currentItems.get(`${item.type}:${item.id}`);
            return {
                ...currentItem,
                ...item,
                sourceId: item.sourceId || currentItem?.sourceId || item.type,
                icon: recentIcon(item, currentItem, renderImageIcon),
                keywords: currentItem?.keywords || [],
                recent: true,
            };
        });
};
