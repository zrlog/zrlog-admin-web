import type { I18nShape } from "./types";
import * as access from "./resources/access";
import * as accessDescriptions from "./resources/access-descriptions";
import * as account from "./resources/account";
import * as aiSettings from "./resources/ai-settings";
import * as appearance from "./resources/appearance";
import * as articleAssistant from "./resources/article-assistant";
import * as articleEdit from "./resources/article-edit";
import * as articleHistory from "./resources/article-history";
import * as articles from "./resources/articles";
import * as automations from "./resources/automations";
import * as common from "./resources/common";
import * as dashboard from "./resources/dashboard";
import * as fileManager from "./resources/file-manager";
import * as operations from "./resources/operations";
import * as pluginSurface from "./resources/plugin-surface";
import * as settings from "./resources/settings";
import * as templates from "./resources/templates";
import * as upgrade from "./resources/upgrade";

export type AdminLang = "en_US" | "zh_CN";

const zhCN = {
    ...common.zhCN,
    ...account.zhCN,
    oauth: access.zhCN.oauth,
    ...dashboard.zhCN,
    ...articles.zhCN,
    ...automations.zhCN,
    ...appearance.zhCN,
    ...settings.zhCN,
    ...aiSettings.zhCN,
    ...templates.zhCN,
    ...operations.zhCN,
    ...upgrade.zhCN,
    ...fileManager.zhCN,
    ...pluginSurface.zhCN,
    access: { ...access.zhCN.access, ...accessDescriptions.zhCN },
    articleEdit: { ...articleEdit.zhCN, ...articleAssistant.zhCN, ...articleHistory.zhCN },
};

export type AdminI18nResource = I18nShape<typeof zhCN>;

const enUS: AdminI18nResource = {
    ...common.enUS,
    ...account.enUS,
    oauth: access.enUS.oauth,
    ...dashboard.enUS,
    ...articles.enUS,
    ...automations.enUS,
    ...appearance.enUS,
    ...settings.enUS,
    ...aiSettings.enUS,
    ...templates.enUS,
    ...operations.enUS,
    ...upgrade.enUS,
    ...fileManager.enUS,
    ...pluginSurface.enUS,
    access: { ...access.enUS.access, ...accessDescriptions.enUS },
    articleEdit: { ...articleEdit.enUS, ...articleAssistant.enUS, ...articleHistory.enUS },
};

const adminI18nResources: Record<AdminLang, AdminI18nResource> = {
    zh_CN: zhCN,
    en_US: enUS,
};

const normalizeLang = (lang?: string): AdminLang => {
    return lang === "en_US" ? "en_US" : "zh_CN";
};

export const getAdminI18n = (lang?: string): AdminI18nResource => {
    return adminI18nResources[normalizeLang(lang)];
};
