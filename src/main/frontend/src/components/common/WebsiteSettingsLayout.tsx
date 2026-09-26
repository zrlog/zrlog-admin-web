import { ReactNode } from "react";
import { theme } from "antd";
import { getRes } from "../../utils/constants";
import { WEBSITE_ROUTES } from "../../utils/account-page-routes";
import { hasAction } from "../../utils/account-access";
import { AdminDashboardRouteIconKey, renderAdminDashboardRouteIcon } from "../admin-dashboard-routes";
import SettingsLayout from "./SettingsLayout";

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
    return navItems;
};

const WebsiteSettingsLayout = ({
    activeKey,
    children,
    extra,
}: {
    activeKey: WebsiteSettingsPage;
    children: ReactNode;
    extra?: ReactNode;
}) => {
    const { token } = theme.useToken();
    const navItems = getWebsiteSettingsItems();
    const activeMeta = navItems.find((item) => item.key === activeKey) || navItems[0];
    const visibleNavItems = navItems.filter((item) => {
        if (!hasAction(item.key === "members" ? "member.manage" : "site.configure")) return false;
        if (item.key === "webhook") {
            return activeKey === "webhook";
        }
        if (item.key === "privacy") {
            return getRes().feature_personal_data_enabled === true;
        }
        return true;
    });

    const navigationGroups = [
        getRes().website.nav.site,
        getRes().website.nav.feature,
        getRes().websiteLab.title,
        getRes().website.nav.system,
    ]
        .map((label) => ({
            label,
            items: visibleNavItems
                .filter((item) => item.group === label)
                .map((item) => ({
                    key: item.key,
                    label: item.text,
                    path:
                        item.key === "members"
                            ? WEBSITE_ROUTES.members
                            : item.key === "basic"
                            ? "/website"
                            : "/website/" + item.key,
                    icon: renderAdminDashboardRouteIcon(item.iconKey, item.key === activeKey, 16),
                })),
        }))
        .filter((group) => group.items.length > 0);

    return (
        <SettingsLayout
            activeKey={activeKey}
            title={activeMeta.text}
            summary={activeMeta.summary}
            groups={navigationGroups}
            extra={extra}
            replaceNavigation
        >
            <div style={{ maxWidth: 800, paddingBottom: activeKey === "members" ? token.padding : 0 }}>{children}</div>
        </SettingsLayout>
    );
};

export default WebsiteSettingsLayout;
