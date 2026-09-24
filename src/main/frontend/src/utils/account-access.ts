import { getSsDate } from "../base/SsData";
export type AccountRole = "owner" | "admin" | "editor" | "author" | "contributor";
export const hasAction = (action: string) => getSsDate().user?.actions?.includes(action) === true;
export const actionForPath = (raw: string): string => {
    const path = raw.split("?")[0].replace(/\.html$/, "");
    if (path.startsWith("/oauth")) return "oauth.grant.manage";
    if (path.startsWith("/members")) return "member.manage";
    if (path.startsWith("/access")) return "permission.read";
    if (path.startsWith("/article-edit")) return "article.read";
    if (path.startsWith("/article-type") || path.startsWith("/tag")) return "taxonomy.manage";
    if (path.startsWith("/article")) return "article.read";
    if (path.startsWith("/comment")) return "comment.manage";
    if (path.startsWith("/user") || path.startsWith("/account-security")) return "account.self";
    if (path.startsWith("/file-manager")) return "file.manage";
    if (path.startsWith("/plugin")) return "plugin.manage";
    if (["/", "", "/index"].includes(path)) return "dashboard.read";
    return "site.configure";
};
