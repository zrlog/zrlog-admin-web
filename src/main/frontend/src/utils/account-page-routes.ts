import type { AdminI18nResource } from "../i18n/admin";

export const USER_ROUTES = {
    profile: "/user",
    preferences: "/user/preferences",
    security: "/user/security",
    applications: "/user/applications",
    authorize: "/user/applications/authorize",
} as const;

export const WEBSITE_ROUTES = { members: "/website/members" } as const;

type AccountPage = {
    api: string;
    action: string;
    sensitive?: boolean;
    acceptsCachedData?: (data: unknown) => boolean;
    title: (res: AdminI18nResource) => string;
};

const pages: Record<string, AccountPage> = {
    [USER_ROUTES.profile]: { api: "/api/admin/user", action: "account.self", title: (res) => res.user.title },
    [USER_ROUTES.preferences]: {
        api: "/api/admin/user/preferences",
        action: "account.self",
        // Older versions cached the profile response for this route.
        acceptsCachedData: (data) =>
            !!data &&
            typeof data === "object" &&
            ["overrides", "defaults", "effective"].every((key) => {
                const value = (data as Record<string, unknown>)[key];
                return !!value && typeof value === "object" && !Array.isArray(value);
            }),
        title: (res) => res.user.preferences.title,
    },
    [USER_ROUTES.security]: {
        api: "/api/admin/account-security",
        action: "account.self",
        title: (res) => res.accountSecurity.title,
    },
    [USER_ROUTES.applications]: {
        api: "/api/admin/oauth",
        action: "oauth.grant.manage",
        sensitive: true,
        title: (res) => res.oauth.title,
    },
    [USER_ROUTES.authorize]: {
        api: "/api/admin/oauth/authorize",
        action: "oauth.grant.manage",
        sensitive: true,
        title: (res) => res.oauth.authorizeTitle,
    },
    [WEBSITE_ROUTES.members]: {
        api: "/api/admin/members",
        action: "member.manage",
        sensitive: true,
        title: (res) => res.members.title,
    },
};

export const getAccountPage = (uri: string): AccountPage | undefined => pages[uri.split("?")[0].replace(/\.html$/, "")];

export const getPageApiUri = (uri: string): string => {
    const queryIndex = uri.indexOf("?");
    const path = (queryIndex < 0 ? uri : uri.slice(0, queryIndex)).replace(/\.html$/, "");
    const search = queryIndex < 0 ? "" : uri.slice(queryIndex);
    return (getAccountPage(path)?.api || "/api/admin" + path) + search;
};
