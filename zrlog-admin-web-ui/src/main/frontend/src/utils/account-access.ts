import { getSsDate } from "../base/SsData";
import { getAccountPage } from "./account-page-routes";
export type AccountRole = "owner" | "admin" | "editor" | "author" | "contributor";
export const hasAction = (action: string) => getSsDate().user?.actions?.includes(action) === true;
export const actionForPath = (raw: string): string => {
    const path = raw.split("?")[0].replace(/\.html$/, "");
    const accountPage = getAccountPage(path);
    if (accountPage) return accountPage.action;
    if (path.startsWith("/article-edit")) return "article.read";
    if (path.startsWith("/article-type") || path.startsWith("/tag")) return "taxonomy.manage";
    if (path.startsWith("/article")) return "article.read";
    if (path.startsWith("/comment")) return "comment.manage";
    if (path.startsWith("/file-manager")) return "file.manage";
    if (path.startsWith("/plugin")) return "plugin.manage";
    if (["/", "", "/index"].includes(path)) return "dashboard.read";
    return "site.configure";
};
