import { isPWA } from "./env-utils";
import { removeQueryParam } from "./helpers";
import { cacheIgnoreReloadKeys } from "./constants";
import * as H from "history";
import { getSsDate, ssKeyStorageKey } from "../base/SsData";

const getCacheKey = () => {
    return window.location.host + "_cache_page_data";
};

export const removePageCacheByLocation = (location: H.Location) => {
    removeCacheDataByKey(getPageDataCacheKey(location));
};

export const getPageDataCacheKey = (location: H.Location) => {
    return getPageDataCacheKeyByPath(location.pathname, location.search);
};

export const getPageBuildId = () => {
    return getSsDate().pageBuildId !== undefined ? (getSsDate().pageBuildId as string as never) : "";
};

export const getPageDataCacheKeyByPath = (pathname: string, search: string) => {
    let realApiKey = pathname.replace(".html", "");
    // / = /index page
    if (realApiKey === "/") {
        realApiKey = "/index";
    }
    return realApiKey + removeQueryParam(search, cacheIgnoreReloadKeys);
};

export const getCachedData = (): Record<string, any> => {
    const tempData = localStorage.getItem(getCacheKey());
    try {
        if (tempData && tempData.length > 0) {
            return JSON.parse(tempData);
        }
    } catch (e) {
        console.error(e);
    }
    return {};
};

export const removeAllCaches = () => {
    try {
        localStorage.removeItem(getCacheKey());
        localStorage.removeItem(ssKeyStorageKey);
    } catch (e) {
        console.error(e);
    }
};

export const putCache = (cache: Record<string, any>) => {
    try {
        //console.info(cache);
        localStorage.setItem(getCacheKey(), JSON.stringify(cache));
    } catch (e) {
        console.error(e);
    }
};

export const addToCache = (key: string, obj: any) => {
    const record = getCachedData();
    record[key] = obj;
    putCache(record);
};

export const getCacheByKey = (key: string) => {
    const record = getCachedData();
    return record[key];
};

export const removeCacheDataByKey = (key: string) => {
    const data: Record<string, any> = getCachedData();
    //console.info("deleted -> " + key + ":" + JSON.stringify(data[key]));
    delete data[key];
    putCache(data);
};

const buildPageFullStateKey = (key: string) => {
    if (isPWA()) {
        return key + "_page_fullScreen_pwa";
    }
    return key + "_page_fullScreen_normal";
};

export const savePageFullState = (key: string, full: boolean) => {
    const record = getCachedData();
    record[buildPageFullStateKey(key)] = full;
    putCache(record);
};

export const getPageFullState = (key: string): boolean => {
    const record = getCachedData();
    return record[buildPageFullStateKey(key)] === true;
};

// Function to save the last opened page to cache
export const saveLastOpenedPage = (url: string): void => {
    const record = getCachedData();
    record["lastOpenedPage"] = url;
    putCache(record);
};

// Function to get the last opened page from cache
export const getLastOpenedPage = (): string | null => {
    return getCachedData()["lastOpenedPage"];
};
