import * as H from "history";
import React from "react";
import { ExclamationCircleOutlined } from "@ant-design/icons";
import { HookAPI } from "antd/es/modal/useModal";
import { EditorUser } from "@editor/dist/type";
import { getCacheByKey } from "./cache";
import { BasicUserInfo } from "../type";
import { cacheIgnoreReloadTime, getRes, tryAppendBackendServerUrl } from "./constants";
import { isPWA } from "./env-utils";

declare global {
    interface Window {
        onbeforeunloadTips?: string | null;
    }
}

export const mapToQueryString = (map: Record<string, string[] | string | boolean | number | undefined>): string => {
    return Object.keys(map)
        .reduce<string[]>(function (a, k) {
            const value = map[k];
            a.push(value === undefined ? `${k}=` : k + "=" + encodeURIComponent(String(value)));
            return a;
        }, [])
        .join("&");
};

export const parseQueryParamsToMap = (queryString: string): Map<string, string> => {
    // 创建 URLSearchParams 对象
    const params = new URLSearchParams(queryString);
    // 将 URLSearchParams 转换为 Map
    const map = new Map<string, string>();

    // 使用 forEach 遍历每个键值对
    params.forEach((value, key) => {
        map.set(key, value);
    });
    return map;
};

function sortKeysWithSpecialHandling(obj: any): any {
    if (obj === null || typeof obj !== "object") {
        return obj;
    }

    if (obj instanceof Date) {
        return obj.toISOString(); // 转换 Date 为字符串
    }

    if (obj instanceof RegExp) {
        return obj.toString(); // 转换 RegExp 为字符串
    }

    if (Array.isArray(obj)) {
        return obj.map(sortKeysWithSpecialHandling);
    }

    const sortedObj: any = {};
    Object.keys(obj)
        .sort()
        .forEach((key) => {
            sortedObj[key] = sortKeysWithSpecialHandling(obj[key]);
        });

    return sortedObj;
}

export function deepEqualWithSpecialJSON(obj1: any, obj2: any): boolean {
    const sortedObj1 = sortKeysWithSpecialHandling(obj1);
    const sortedObj2 = sortKeysWithSpecialHandling(obj2);

    return JSON.stringify(sortedObj1) === JSON.stringify(sortedObj2);
}

export function removeQueryParam(search: string, key: string) {
    // 检查是否以 '?' 开头，并处理查询字符串
    const hasQuestionMark = search.startsWith("?");
    const params = new URLSearchParams(hasQuestionMark ? search.substring(1) : search);

    key.split(",").map((k: string) => {
        // 删除特定的查询参数
        params.delete(k);
    });
    // 构建新的查询字符串
    const newSearch = params.toString();

    // 返回结果，根据原始输入决定是否添加 '?'
    return newSearch ? (hasQuestionMark ? `?${newSearch}` : newSearch) : "";
}

export const getFullPath = (location: H.Location) => {
    if (location.search.length <= 0) {
        return location.pathname;
    }
    return location.pathname + location.search;
};

export const getExitTips = () => {
    return window.onbeforeunloadTips;
};

export const enableExitTips = (str: string) => {
    window.onbeforeunloadTips = str;
    window.onbeforeunload = function () {
        return str;
    };
};

export const disableExitTips = () => {
    window.onbeforeunload = null;
    window.onbeforeunloadTips = null;
};

export const tryBlock = (e: React.MouseEvent, modal: HookAPI) => {
    if (window.onbeforeunload !== null) {
        modal.warning({
            title: getRes().common.tips,
            icon: <ExclamationCircleOutlined />,
            content: getExitTips(),
        });
        e.preventDefault();
    }
};

export const colorPickerBgColors = [
    // 冷色区
    "rgb(22, 119, 255)", // 蓝
    "rgb(3, 169, 244)", // 天蓝 (新增)
    "rgb(47, 84, 235)", // 深蓝
    "rgb(63, 81, 181)", // 靛蓝 (新增)
    "rgb(114, 46, 209)", // 紫
    "rgb(156, 39, 176)", // 深紫 (新增)
    "rgb(171, 71, 188)", // 中紫
    "rgb(233, 30, 99)", // 洋红 (新增)
    "rgb(235, 47, 150)", // 品红

    // 青色区
    "rgb(19, 194, 194)", // 青
    "rgb(0, 188, 212)", // 冰蓝 (新增)
    "rgb(0, 150, 136)", // 深青

    // 绿色区
    "rgb(82, 196, 26)", // 绿色
    "rgb(139, 195, 74)", // 浅绿 (新增)
    "rgb(160, 217, 17)", // 黄绿
    "rgb(205, 220, 57)", // 酸橙 (新增)

    // 黄色区
    "rgb(250, 219, 20)", // 亮黄
    "rgb(255, 235, 59)", // 柠黄 (新增)
    "rgb(250, 173, 20)", // 金黄
    "rgb(255, 193, 7)", // 芥黄
    "rgb(250, 140, 22)", // 橙
    "rgb(255, 152, 0)", // 橙黄 (新增)
    "rgb(250, 84, 28)", // 橙红
    "rgb(255, 87, 34)", // 烧橙

    // 红色区
    "rgb(245, 34, 45)", // 红
    "rgb(244, 67, 54)", // 番茄红 (新增)

    // 中性色
    "rgb(121, 85, 72)", // 棕色
    "rgb(96, 125, 139)", // 蓝灰
    "rgb(33, 33, 33)", // 深灰
];

export const getContextPath = () => {
    return new URL(document.baseURI).pathname;
};

export const getEditorUser = (): EditorUser => {
    const basicUser = getCacheByKey("/user") as BasicUserInfo;
    if (!basicUser) {
        return {
            nickname: "admin",
            avatarUrl: "",
        };
    }
    return {
        nickname: basicUser.userName,
        avatarUrl: tryAppendBackendServerUrl(basicUser.header),
    };
};

export const updateDocumentTitle = (newDocumentTitle: string) => {
    const baseTitle = getRes().websiteTitle + " - " + getRes().common.management;
    if (newDocumentTitle) {
        if (isPWA()) {
            window.document.title = newDocumentTitle.replace(" - " + baseTitle, "");
        } else {
            window.document.title = newDocumentTitle;
        }
    } else {
        window.document.title = baseTitle;
    }
};

export const refreshLocationForce = () => {
    const url = new URL(window.location.href);
    url.searchParams.set(cacheIgnoreReloadTime, new Date().getTime() + "");
    window.location.replace(url.toString());
};
