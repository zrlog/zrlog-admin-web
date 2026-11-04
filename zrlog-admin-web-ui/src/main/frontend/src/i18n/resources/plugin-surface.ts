import type { I18nShape } from "../types";

export const zhCN = {
    pluginSurface: {
        fallbackTitle: "插件视图",
        loading: "加载中...",
        empty: "暂无内容",
        loadFailed: "加载失败",
        actionFailed: "操作失败",
        chartEmpty: "暂无图表数据",
        submit: "提交",
        requiredMessage: "请输入{label}",
        status: {
            normal: "正常",
            processing: "进行中",
            warning: "提醒",
            error: "异常",
        },
    },
    plugin: {
        title: "插件",
    },
} as const;

export const enUS: I18nShape<typeof zhCN> = {
    pluginSurface: {
        fallbackTitle: "Plugin View",
        loading: "Loading...",
        empty: "No content",
        loadFailed: "Failed to load",
        actionFailed: "Action failed",
        chartEmpty: "No chart data",
        submit: "Submit",
        requiredMessage: "Enter {label}",
        status: {
            normal: "Normal",
            processing: "Processing",
            warning: "Warning",
            error: "Error",
        },
    },
    plugin: {
        title: "Plugins",
    },
};
