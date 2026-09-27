import type { AxiosInstance } from "axios";
import type { ReactNode } from "react";

export type SpotlightItemType = "route" | "article" | "action" | "file" | "template" | "plugin";

export type SpotlightIconVariant = "directory" | "file";

export type SpotlightSelectContext = {
    keyword: string;
    close: () => void;
    navigate: (path: string) => void;
};

export type SpotlightItem = {
    id: string;
    title: string;
    subTitle?: string;
    path?: string;
    icon: ReactNode;
    iconSrc?: string;
    iconVariant?: SpotlightIconVariant;
    keywords: string[];
    type: SpotlightItemType;
    sourceId?: string;
    recent?: boolean;
    persist?: boolean;
    onSelect?: (context: SpotlightSelectContext) => void | Promise<void>;
};

export type SpotlightRecentItem = Pick<
    SpotlightItem,
    "id" | "title" | "subTitle" | "path" | "type" | "sourceId" | "iconSrc" | "iconVariant"
>;

export type SpotlightRenderImageIcon = (src: string | undefined, title: string, fallback: ReactNode) => ReactNode;

export type SpotlightSearchContext = {
    keyword: string;
    normalizedKeyword: string;
    axiosInstance: AxiosInstance;
    renderImageIcon: SpotlightRenderImageIcon;
    onSourceRefresh?: () => void;
};

export type SpotlightSource = {
    id: string;
    empty?: (context: SpotlightSearchContext) => SpotlightItem[] | Promise<SpotlightItem[]>;
    search: (context: SpotlightSearchContext) => SpotlightItem[] | Promise<SpotlightItem[]>;
};

export type SpotlightCommand = {
    id: string;
    title: string;
    subTitle?: string;
    icon: ReactNode;
    keywords?: string[];
    type?: SpotlightItemType;
    persist?: boolean;
    execute: (context: SpotlightSelectContext) => void | Promise<void>;
};
