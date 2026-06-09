import { getRes } from "../utils/constants";

export type AdminNavigationGroupKey = "content" | "site" | "extension" | "settings";

type AdminNavigationRouteRule = {
    group: AdminNavigationGroupKey;
    paths: string[];
};

const navigationRouteRules: AdminNavigationRouteRule[] = [
    {
        group: "content",
        paths: ["/article-edit", "/article-type", "/article", "/tag", "/comment", "/file-manager"],
    },
    {
        group: "site",
        paths: ["/nav", "/link"],
    },
    {
        group: "extension",
        paths: ["/plugin", "/template-center", "/template", "/template-config"],
    },
    {
        group: "settings",
        paths: ["/website", "/system", "/upgrade", "/user", "/account-security", "/user-update-password"],
    },
];

export const getAdminNavigationGroup = (pathname: string): AdminNavigationGroupKey | undefined => {
    const normalizedPath = pathname.split(".")[0] || "/index";
    return navigationRouteRules.find((rule) => rule.paths.some((path) => normalizedPath.startsWith(path)))?.group;
};

export const getAdminNavigationGroupLabel = (group: AdminNavigationGroupKey) => {
    const res = getRes();
    switch (group) {
        case "content":
            return res.common.content;
        case "site":
            return res.common.site;
        case "extension":
            return res.common.extension;
        case "settings":
            return res.common.settings;
    }
};
