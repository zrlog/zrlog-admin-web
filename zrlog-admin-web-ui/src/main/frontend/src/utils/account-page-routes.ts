import type { AdminI18nResource } from "../i18n/admin";

export const USER_ROUTES = {
    profile: "/user",
    appearance: "/user/preferences/appearance",
    writing: "/user/preferences/writing",
    assistant: "/user/preferences/assistant",
    security: "/user/security",
    tokens: "/user/applications/tokens",
    grants: "/user/applications/grants",
    clients: "/user/applications/clients",
    authorize: "/user/applications/authorize",
} as const;

export type UserPreferencePage = "appearance" | "writing" | "assistant";
export type UserApplicationPage = "tokens" | "grants" | "clients";
export const USER_PREFERENCE_PAGES: UserPreferencePage[] = ["appearance", "writing", "assistant"];
export const USER_APPLICATION_PAGES: UserApplicationPage[] = ["tokens", "grants", "clients"];

export const WEBSITE_ROUTES = { members: "/website/members" } as const;

type AccountPage = {
    api: string;
    action: string;
    sensitive?: boolean;
    title: (res: AdminI18nResource) => string;
};

const pages: Record<string, AccountPage> = {
    [USER_ROUTES.profile]: { api: "/api/admin/user", action: "account.self", title: (res) => res.user.title },
    ...Object.fromEntries(
        USER_PREFERENCE_PAGES.map((page) => [
            USER_ROUTES[page],
            {
                api: "/api/admin/user/preferences",
                action: "account.self",
                title: (res: AdminI18nResource) =>
                    ({
                        appearance: res.user.preferences.appearanceTitle,
                        writing: res.user.preferences.writingTitle,
                        assistant: res.user.preferences.assistantTitle,
                    }[page]),
            },
        ])
    ),
    [USER_ROUTES.security]: {
        api: "/api/admin/account-security",
        action: "account.self",
        title: (res) => res.accountSecurity.title,
    },
    ...Object.fromEntries(
        USER_APPLICATION_PAGES.map((page) => [
            USER_ROUTES[page],
            {
                api: page === "clients" ? "/api/admin/oauth/clients" : "/api/admin/oauth",
                action: page === "clients" ? "oauth.client.manage" : "oauth.grant.manage",
                sensitive: true,
                title: (res: AdminI18nResource) =>
                    ({
                        tokens: res.oauth.personalTokens.title,
                        grants: res.oauth.grants,
                        clients: res.oauth.applications,
                    }[page]),
            },
        ])
    ),
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
