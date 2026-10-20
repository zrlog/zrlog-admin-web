import {getRes, isServerDevelopmentMode} from "./constants";
import {WEBSITE_ROUTES} from "./account-page-routes";
import {actionForPath, hasAction} from "./account-access";
import {isFeaturePathEnabled} from "./module-capabilities";
import type {AdminDashboardRouteIconKey} from "../components/admin-dashboard-routes";

export type WebsiteSettingsPage =
    | "basic"
    | "other"
    | "upgrade"
    | "admin"
    | "blog"
    | "ai"
    | "article-edit"
    | "content-protector"
    | "lab"
    | "webhook"
    | "privacy"
    | "members";

export type AdminSettingsPage = WebsiteSettingsPage | "system" | "proxy" | "dev";

export const getWebsiteSettingsItems = () => {
    const navItems: Array<{
        key: WebsiteSettingsPage;
        text: string;
        summary: string;
        group: string;
        iconKey: AdminDashboardRouteIconKey;
    }> = [
        {
            key: "basic",
            text: getRes().website.title,
            summary: getRes().website.summary,
            group: getRes().website.nav.site,
            iconKey: "home",
        },
        {
            key: "members",
            text: getRes().members.title,
            summary: getRes().members.description,
            group: getRes().website.nav.site,
            iconKey: "user",
        },
        {
            key: "blog",
            text: getRes().websiteBlog.title,
            summary: getRes().websiteBlog.summary,
            group: getRes().website.nav.site,
            iconKey: "read",
        },
        {
            key: "admin",
            text: getRes().websiteAdmin.title,
            summary: getRes().websiteAdmin.summary,
            group: getRes().website.nav.system,
            iconKey: "sliders",
        },
        {
            key: "webhook",
            text: getRes().websiteWebhook.title,
            summary: getRes().websiteWebhook.summary,
            group: getRes().websiteLab.title,
            iconKey: "webhook",
        },
        {
            key: "privacy",
            text: getRes().websitePrivacy.title,
            summary: getRes().websitePrivacy.summary,
            group: getRes().websiteLab.title,
            iconKey: "safety-certificate",
        },
        {
            key: "other",
            text: getRes().websiteOther.title,
            summary: getRes().websiteOther.summary,
            group: getRes().website.nav.site,
            iconKey: "file-text",
        },
        {
            key: "article-edit",
            text: getRes().websiteArticleEdit.title,
            summary: getRes().websiteArticleEdit.summary,
            group: getRes().website.nav.feature,
            iconKey: "edit",
        },
        {
            key: "content-protector",
            text: getRes().websiteContentProtector.title,
            summary: getRes().websiteContentProtector.summary,
            group: getRes().website.nav.feature,
            iconKey: "copyright",
        },
        {
            key: "ai",
            text: getRes().websiteAi.title,
            summary: getRes().websiteAi.summary,
            group: getRes().website.nav.feature,
            iconKey: "robot",
        },
        {
            key: "lab",
            text: getRes().websiteLab.title,
            summary: getRes().websiteLab.summary,
            group: getRes().website.nav.feature,
            iconKey: "experiment",
        },
        {
            key: "upgrade",
            text: getRes().websiteUpgrade.title,
            summary: getRes().websiteUpgrade.summary,
            group: getRes().website.nav.system,
            iconKey: "sync",
        },
    ];
    return navItems.map((item) => ({
        ...item,
        path:
            item.key === "members"
                ? WEBSITE_ROUTES.members
                : item.key === "basic"
                    ? "/website"
                    : item.key === "upgrade"
                        ? "/system/upgrade"
                        : "/website/" + item.key,
    }));
};

type SettingsItem = {
    key: AdminSettingsPage;
    text: string;
    summary: string;
    group: string;
    iconKey: AdminDashboardRouteIconKey;
    path: string;
};

export const getAdminSettingsItems = (): SettingsItem[] => {
    const res = getRes();
    return [
        ...getWebsiteSettingsItems(),
        {
            key: "proxy",
            text: res.system.proxy.title,
            summary: res.system.proxy.summary,
            group: res.website.nav.system,
            iconKey: "link",
            path: "/system/proxy",
        },
        {
            key: "system",
            text: res.system.title,
            summary: res.system.summary,
            group: res.website.nav.system,
            iconKey: "info",
            path: "/system",
        },
        {
            key: "dev",
            text: res.dev.title,
            summary: res.dev.summary,
            group: res.website.nav.system,
            iconKey: "experiment",
            path: "/dev",
        },
    ];
};

export const getVisibleSettingsItems = (activeKey?: AdminSettingsPage) =>
    getAdminSettingsItems().filter((item) => {
        if (!isFeaturePathEnabled(item.path) || !hasAction(actionForPath(item.path))) return false;
        if (item.key === "dev") return isServerDevelopmentMode();
        if (item.key === "webhook") return activeKey === "webhook";
        if (item.key === "privacy") return getRes().feature_personal_data_enabled === true;
        return true;
    });

export const getSettingsEntryPath = () => {
    const items = getVisibleSettingsItems();
    return (items.find((item) => item.key === "basic") || items.find((item) => item.key === "system") || items[0])
        ?.path;
};
