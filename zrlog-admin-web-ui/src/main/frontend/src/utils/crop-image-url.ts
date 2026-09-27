import {getBackendServerUrl, tryAppendBackendServerUrl} from "./constants";

export const resolveBackendCropImageUrl = (url: string) => {
    if (!url || url.startsWith("data:") || url.startsWith("blob:")) {
        return url;
    }
    try {
        const backendUrl = new URL(getBackendServerUrl(), window.location.origin);
        const targetUrl = new URL(url, backendUrl);
        if (targetUrl.origin !== backendUrl.origin) {
            return url;
        }
        const backendPath = backendUrl.pathname.endsWith("/") ? backendUrl.pathname : `${backendUrl.pathname}/`;
        let resourcePath = targetUrl.pathname;
        if (backendPath !== "/" && resourcePath.startsWith(backendPath)) {
            resourcePath = `/${resourcePath.substring(backendPath.length)}`;
        }
        if (resourcePath.startsWith("/attached/") || resourcePath.startsWith("/admin/attached/tmp/")) {
            return tryAppendBackendServerUrl(
                `/api/admin/file-manager/download?path=${encodeURIComponent(resourcePath)}`
            );
        }
    } catch (e) {
        // Keep the original image URL for display if URL parsing fails.
    }
    return url;
};
