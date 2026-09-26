import { getBackendServerUrl } from "./constants";

const httpUrl = (value: string, base?: string): URL | undefined => {
    try {
        const url = new URL(value, base);
        if (!["http:", "https:"].includes(url.protocol) || url.username || url.password) return undefined;
        return url;
    } catch {
        return undefined;
    }
};

/** Keep canonical OAuth identifiers intact; resolve incomplete service URLs against the connected backend. */
export const resolveApplicationServerUrl = (
    serverUrl: string | undefined,
    fallbackPath: "" | "api/oauth" | "mcp",
    backendServerUrl = getBackendServerUrl(),
    origin = window.location.origin
): string => {
    const value = serverUrl?.trim() || "";
    if (value.includes("\\")) return "";
    if (/^[a-z][a-z\d+.-]*:/i.test(value)) {
        return /^https?:\/\//i.test(value) && httpUrl(value) ? value : "";
    }

    const base = httpUrl(backendServerUrl, origin);
    if (!base || base.search || base.hash) return "";
    if (value.startsWith("//")) return httpUrl(value, base.href)?.href || "";
    const basePath = base.pathname.replace(/\/+$/, "") + "/";
    base.pathname = basePath;
    const suppliedPath = value.replace(/^\/+/, "");
    const contextPath = basePath.replace(/^\/+/, "");
    const path =
        suppliedPath === contextPath.slice(0, -1)
            ? ""
            : contextPath && suppliedPath.startsWith(contextPath)
            ? suppliedPath.slice(contextPath.length)
            : suppliedPath;
    try {
        if (decodeURIComponent(path).split(/[\\/]/).includes("..")) return "";
    } catch {
        return "";
    }
    const resolved = httpUrl(path || fallbackPath, base.href);
    if (!resolved) return "";
    return !fallbackPath ? resolved.href.replace(/\/+$/, "") : resolved.href;
};
