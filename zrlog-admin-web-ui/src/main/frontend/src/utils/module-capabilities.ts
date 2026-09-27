import { getSsDate } from "../base/SsData";
import type { AdminCapabilities } from "./constants";

// Older servers omit capabilities; keep their existing UI behavior.
export const isModuleEnabled = (module: keyof AdminCapabilities): boolean =>
    getSsDate().resourceInfo?.capabilities?.[module] !== false;

export const isFeaturePathEnabled = (raw: string): boolean => {
    const path = "/" + raw.split("?")[0].replace(/\.html$/, "").replace(/^\//, "");
    if (path === "/website/ai" || path === "/user/preferences/assistant") return isModuleEnabled("ai");
    if (path.startsWith("/user/applications/") || path === "/website/webhook") return isModuleEnabled("access");
    if (path.startsWith("/user") || path === "/website/members") return isModuleEnabled("account");
    if (path.startsWith("/file-manager")) return isModuleEnabled("assets");
    if (/^\/(article|article-edit|article-type|tag|comment|link|nav)(\/|$)/.test(path)
        || path === "/website/privacy") return isModuleEnabled("content");
    return true;
};
