import { getContextPath } from "./helpers";
import { getSsDate } from "../base/SsData";
import type { AdminI18nResource, AdminLang } from "../i18n/admin";
import { getAdminI18n } from "../i18n/admin";

export type AdminTheme = "geek" | "antd" | "shadcn" | "default" | "cartoon" | "illustration" | "bootstrap" | "desk";

class Constants {
    static getFillBackImg() {
        return "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAMIAAADDCAYAAADQvc6UAAABRWlDQ1BJQ0MgUHJvZmlsZQAAKJFjYGASSSwoyGFhYGDIzSspCnJ3UoiIjFJgf8LAwSDCIMogwMCcmFxc4BgQ4ANUwgCjUcG3awyMIPqyLsis7PPOq3QdDFcvjV3jOD1boQVTPQrgSkktTgbSf4A4LbmgqISBgTEFyFYuLykAsTuAbJEioKOA7DkgdjqEvQHEToKwj4DVhAQ5A9k3gGyB5IxEoBmML4BsnSQk8XQkNtReEOBxcfXxUQg1Mjc0dyHgXNJBSWpFCYh2zi+oLMpMzyhRcASGUqqCZ16yno6CkYGRAQMDKMwhqj/fAIcloxgHQqxAjIHBEugw5sUIsSQpBobtQPdLciLEVJYzMPBHMDBsayhILEqEO4DxG0txmrERhM29nYGBddr//5/DGRjYNRkY/l7////39v///y4Dmn+LgeHANwDrkl1AuO+pmgAAADhlWElmTU0AKgAAAAgAAYdpAAQAAAABAAAAGgAAAAAAAqACAAQAAAABAAAAwqADAAQAAAABAAAAwwAAAAD9b/HnAAAHlklEQVR4Ae3dP3PTWBSGcbGzM6GCKqlIBRV0dHRJFarQ0eUT8LH4BnRU0NHR0UEFVdIlFRV7TzRksomPY8uykTk/zewQfKw/9znv4yvJynLv4uLiV2dBoDiBf4qP3/ARuCRABEFAoBEgghggQAQZQKAnYEaQBAQaASKIAQJEkAEEegJmBElAoBEgghggQAQZQKAnYEaQBAQaASKIAQJEkAEEegJmBElAoBEgghggQAQZQKAnYEaQBAQaASKIAQJEkAEEegJmBElAoBEgghggQAQZQKAnYEaQBAQaASKIAQJEkAEEegJmBElAoBEgghggQAQZQKAnYEaQBAQaASKIAQJEkAEEegJmBElAoBEgghggQAQZQKAnYEaQBAQaASKIAQJEkAEEegJmBElAoBEgghggQAQZQKAnYEaQBAQaASKIAQJEkAEEegJmBElAoBEgghggQAQZQKAnYEaQBAQaASKIAQJEkAEEegJmBElAoBEgghggQAQZQKAnYEaQBAQaASKIAQJEkAEEegJmBElAoBEgghggQAQZQKAnYEaQBAQaASKIAQJEkAEEegJmBElAoBEgghggQAQZQKAnYEaQBAQaASKIAQJEkAEEegJmBElAoBEgghggQAQZQKAnYEaQBAQaASKIAQJEkAEEegJmBElAoBEgghgg0Aj8i0JO4OzsrPv69Wv+hi2qPHr0qNvf39+iI97soRIh4f3z58/u7du3SXX7Xt7Z2enevHmzfQe+oSN2apSAPj09TSrb+XKI/f379+08+A0cNRE2ANkupk+ACNPvkSPcAAEibACyXUyfABGm3yNHuAECRNgAZLuYPgEirKlHu7u7XdyytGwHAd8jjNyng4OD7vnz51dbPT8/7z58+NB9+/bt6jU/TI+AGWHEnrx48eJ/EsSmHzx40L18+fLyzxF3ZVMjEyDCiEDjMYZZS5wiPXnyZFbJaxMhQIQRGzHvWR7XCyOCXsOmiDAi1HmPMMQjDpbpEiDCiL358eNHurW/5SnWdIBbXiDCiA38/Pnzrce2YyZ4//59F3ePLNMl4PbpiL2J0L979+7yDtHDhw8vtzzvdGnEXdvUigSIsCLAWavHp/+qM0BcXMd/q25n1vF57TYBp0a3mUzilePj4+7k5KSLb6gt6ydAhPUzXnoPR0dHl79WGTNCfBnn1uvSCJdegQhLI1vvCk+fPu2ePXt2tZOYEV6/fn31dz+shwAR1sP1cqvLntbEN9MxA9xcYjsxS1jWR4AIa2Ibzx0tc44fYX/16lV6NDFLXH+YL32jwiACRBiEbf5KcXoTIsQSpzXx4N28Ja4BQoK7rgXiydbHjx/P25TaQAJEGAguWy0+2Q8PD6/Ki4R8EVl+bzBOnZY95fq9rj9zAkTI2SxdidBHqG9+skdw43borCXO/ZcJdraPWdv22uIEiLA4q7nvvCug8WTqzQveOH26fodo7g6uFe/a17W3+nFBAkRYENRdb1vkkz1CH9cPsVy/jrhr27PqMYvENYNlHAIesRiBYwRy0V+8iXP8+/fvX11Mr7L7ECueb/r48eMqm7FuI2BGWDEG8cm+7G3NEOfmdcTQw4h9/55lhm7DekRYKQPZF2ArbXTAyu4kDYB2YxUzwg0gi/41ztHnfQG26HbGel/crVrm7tNY+/1btkOEAZ2M05r4FB7r9GbAIdxaZYrHdOsgJ/wCEQY0J74TmOKnbxxT9n3FgGGWWsVdowHtjt9Nnvf7yQM2aZU/TIAIAxrw6dOnAWtZZcoEnBpNuTuObWMEiLAx1HY0ZQJEmHJ3HNvGCBBhY6jtaMoEiJB0Z29vL6ls58vxPcO8/zfrdo5qvKO+d3Fx8Wu8zf1dW4p/cPzLly/dtv9Ts/EbcvGAHhHyfBIhZ6NSiIBTo0LNNtScABFyNiqFCBChULMNNSdAhJyNSiECRCjUbEPNCRAhZ6NSiAARCjXbUHMCRMjZqBQiQIRCzTbUnAARcjYqhQgQoVCzDTUnQIScjUohAkQo1GxDzQkQIWejUogAEQo121BzAkTI2agUIkCEQs021JwAEXI2KoUIEKFQsw01J0CEnI1KIQJEKNRsQ80JECFno1KIABEKNdtQcwJEyNmoFCJAhELNNtScABFyNiqFCBChULMNNSdAhJyNSiECRCjUbEPNCRAhZ6NSiAARCjXbUHMCRMjZqBQiQIRCzTbUnAARcjYqhQgQoVCzDTUnQIScjUohAkQo1GxDzQkQIWejUogAEQo121BzAkTI2agUIkCEQs021JwAEXI2KoUIEKFQsw01J0CEnI1KIQJEKNRsQ80JECFno1KIABEKNdtQcwJEyNmoFCJAhELNNtScABFyNiqFCBChULMNNSdAhJyNSiECRCjUbEPNCRAhZ6NSiAARCjXbUHMCRMjZqBQiQIRCzTbUnAARcjYqhQgQoVCzDTUnQIScjUohAkQo1GxDzQkQIWejUogAEQo121BzAkTI2agUIkCEQs021JwAEXI2KoUIEKFQsw01J0CEnI1KIQJEKNRsQ80JECFno1KIABEKNdtQcwJEyNmoFCJAhELNNtScABFyNiqFCBChULMNNSdAhJyNSiEC/wGgKKC4YMA4TAAAAABJRU5ErkJggg==";
    }
}

export type LoginUserInfo = {
    userName?: string;
    password?: string;
    mfaCode?: string;
    backendServerUrl?: string;
};

export type AdminRuntimeResourceInfo = {
    admin_color_primary?: string;
    admin_compactMode?: boolean;
    admin_darkMode?: boolean;
    admin_static_resource_base_url?: string;
    admin_theme?: AdminTheme;
    appId?: string;
    articleRoute?: string;
    buildId?: string;
    copyrightTips?: string;
    currentVersion?: string;
    defaultLoginInfo?: LoginUserInfo;
    feature_personal_data_enabled?: boolean;
    feature_webhook_enabled?: boolean;
    homeUrl?: string;
    lang?: AdminLang;
    passkeyRegistrationEnabled?: boolean;
    passkeyLoginEnabled?: boolean;
    staticPage?: boolean;
    staticPlugin?: boolean;
    supportSse?: boolean;
    websiteTitle?: string;
};

export type AdminResourceInfo = AdminI18nResource & AdminRuntimeResourceInfo;

const toRuntimeResourceInfo = (res?: AdminRuntimeResourceInfo | null): AdminRuntimeResourceInfo => {
    if (res === undefined || res === null) {
        return {};
    }
    return {
        admin_color_primary: res.admin_color_primary,
        admin_compactMode: res.admin_compactMode,
        admin_darkMode: res.admin_darkMode,
        admin_static_resource_base_url: res.admin_static_resource_base_url,
        admin_theme: res.admin_theme,
        appId: res.appId,
        articleRoute: res.articleRoute,
        buildId: res.buildId,
        copyrightTips: res.copyrightTips,
        currentVersion: res.currentVersion,
        defaultLoginInfo: res.defaultLoginInfo,
        feature_personal_data_enabled: res.feature_personal_data_enabled,
        feature_webhook_enabled: res.feature_webhook_enabled,
        homeUrl: res.homeUrl,
        lang: res.lang,
        passkeyRegistrationEnabled: res.passkeyRegistrationEnabled,
        passkeyLoginEnabled: res.passkeyLoginEnabled,
        staticPage: res.staticPage,
        staticPlugin: res.staticPlugin,
        supportSse: res.supportSse,
        websiteTitle: res.websiteTitle,
    };
};

export const getRes = (): AdminResourceInfo => {
    const runtimeRes = toRuntimeResourceInfo(getSsDate().resourceInfo);
    if (Object.keys(runtimeRes).length === 0) {
        return getAdminI18n();
    }
    return {
        ...getAdminI18n(runtimeRes.lang),
        ...runtimeRes,
    };
};

export const getLabelValueSeparator = () => {
    return getRes().lang === "en_US" ? ": " : "：";
};

export const formatLabelValue = (label: string, value: string | number) => {
    return `${label}${getLabelValueSeparator()}${value}`;
};

export const getDefaultLoginInfo = (): LoginUserInfo => {
    const defaultLoginInfo: LoginUserInfo | null | undefined = getRes().defaultLoginInfo;
    if (defaultLoginInfo === undefined || defaultLoginInfo === null) {
        return {
            userName: "",
            password: "",
            backendServerUrl: getBackendServerUrl(),
        };
    }
    const serverUrl = (defaultLoginInfo as LoginUserInfo).backendServerUrl;
    if (serverUrl === undefined || serverUrl === null || serverUrl.length === 0) {
        return {
            ...(defaultLoginInfo as LoginUserInfo),
            backendServerUrl: getBackendServerUrl(),
        };
    }
    return defaultLoginInfo;
};

export const setRes = (r: AdminRuntimeResourceInfo) => {
    getSsDate().resourceInfo = toRuntimeResourceInfo(r);
};

export const cacheIgnoreReloadTime = "_t";

export const cacheIgnoreReloadKeys = cacheIgnoreReloadTime + ",v" + ",view";

export const isDev = () => {
    return process.env.NODE_ENV != "production";
};

export const isStaticPage = () => {
    if (isDev()) {
        return true;
    }
    if (window.location.pathname.endsWith(".html")) {
        return true;
    }
    return getRes().staticPage;
};

const backendServerUrlStorageKey = "_backend_server_url";

export const hasConfiguredBackendServerUrl = (): boolean => {
    const storedBackendServerUrl = window.localStorage.getItem(backendServerUrlStorageKey);
    if (storedBackendServerUrl?.trim()) {
        return true;
    }
    return Boolean(getRes().defaultLoginInfo?.backendServerUrl?.trim());
};

export const setBackendServerUrl = (url: string) => {
    window.localStorage.setItem(backendServerUrlStorageKey, url);
};

export const getBackendServerUrl = (): string => {
    const val = window.localStorage.getItem(backendServerUrlStorageKey);
    if (val) {
        if (val.endsWith("/")) {
            return val;
        }
        return val + "/";
    }
    return getContextPath();
};

export const tryAppendBackendServerUrl = (url: string): string => {
    if (url.startsWith("/")) {
        const backendServerUrl = window.localStorage.getItem(backendServerUrlStorageKey);
        if (!backendServerUrl) {
            return url;
        }
        const normalizedBackendServerUrl = backendServerUrl.endsWith("/") ? backendServerUrl : backendServerUrl + "/";
        const serverContextPath =
            normalizedBackendServerUrl.startsWith("http://") || normalizedBackendServerUrl.startsWith("https://")
                ? new URL(normalizedBackendServerUrl).pathname
                : normalizedBackendServerUrl;
        if (url.startsWith(serverContextPath)) {
            return normalizedBackendServerUrl + url.substring(serverContextPath.length, url.length);
        }
        return normalizedBackendServerUrl + url.substring(1);
    }
    return url;
};

export const getRealRouteUrl = (url: string) => {
    const buildId = getSsDate().pageBuildId;
    let query;
    const uriInfo = url.split("?");
    if (uriInfo.length > 1) {
        const search = uriInfo[1];
        query = new URLSearchParams(search);
    } else {
        query = new URLSearchParams("");
    }
    if (buildId && buildId.length > 0) {
        query.set("v", buildId);
    }
    let ext = isStaticPage() ? ".html" : "";
    const pathname = uriInfo[0];
    if (pathname.endsWith(".html")) {
        ext = "";
    }
    return pathname + ext + "?" + query.toString();
};

export const getPreset = () => {
    return getRes().common.preset;
};

export const getEnterFullscreen = () => {
    return getRes().fullscreen.enter;
};

export const getExitFullscreen = () => {
    return getRes().fullscreen.exit;
};

export const createUri = "/api/admin/article/create";
export const updateUri = "/api/admin/article/update";

//export const ARTICLE_URIS = [createUri,updateUri]

export default Constants;
