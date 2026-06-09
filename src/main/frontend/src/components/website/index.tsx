import { Card, Grid, Menu, message, Select, theme } from "antd";

import { getRealRouteUrl, getRes, setRes } from "../../utils/constants";
import BlogForm from "./BlogForm";
import BasicForm from "./BasicForm";
import OtherForm from "./OtherForm";
import UpgradeSettingForm from "./UpgradeSettingForm";
import { Link, useLocation, useNavigate } from "react-router-dom";
import AdminForm from "./AdminForm";
import { FunctionComponent, ReactNode, useState } from "react";
import { AdminCommonProps, AIProviderType } from "../../type";
import { getPageDataCacheKeyByPath } from "../../utils/cache";
import { useAxiosBaseInstance } from "../../base/AppBase";
import { getAppState } from "../../base/ConfigProviderApp";
import AIForm from "./AIForm";
import { postRefreshCacheSse } from "../../utils/sse-utils";
import ArticleEditForm from "./ArticleEditForm";
import FeatureLabForm from "./FeatureLabForm";
import { AdminDashboardRouteIconKey, renderAdminDashboardRouteIcon } from "../admin-dashboard-routes";
import SidebarNavItem from "../common/SidebarNavItem";
import ContentProtectorForm from "./ContentProtectorForm";
import WebhookForm from "./WebhookForm";
import PrivacyForm from "./PrivacyForm";

export interface Basic {
    second_title: string;
    title: string;
    keywords: string;
    description: string;
    favicon_ico_base64: string;
    author: string;
}

export interface Admin {
    session_timeout: number;
    disable_comment_status: boolean;
    article_thumbnail_status: boolean;
    admin_static_resource_base_url: string;
    language: string;
    admin_darkMode: boolean;
    admin_compactMode: boolean;
    admin_color_primary: string;
    favicon_png_pwa_192_base64: string;
    favicon_png_pwa_512_base64: string;
}

export interface ArticleEditSetting {
    article_auto_digest_length: number;
    article_edit_auto_save_interval: number;
    article_editor_link_preview_enabled: boolean;
    article_publish_check_enabled: boolean;
    article_cover_aspect_ratio: string;
}

export interface Blog {
    host: string;
    system_notification: string;
    article_thumbnail_status: boolean;
    disable_comment_status: boolean;
    generator_html_status: boolean;
}

export interface Other {
    icp: string;
    webCm: string;
    robotRuleContent: string;
}

export interface AI {
    ai_provider: string;
    ai_api_key: string;
    ai_model: string;
    ai_prompt: string;
    ai_max_completion_tokens?: number | null;
    ai_image_provider?: string;
    ai_image_api_key?: string;
    ai_image_model?: string;
    hasAiApiKey?: boolean;
    hasAiImageApiKey?: boolean;
    allProviders: AIProvider[];
    allImageProviders: AIProvider[];
}

export interface AIProvider {
    name: AIProviderType;
    models: string[];
    modelEntries?: AIModelEntry[];
}

export interface AIModelEntry {
    name: string;
    capabilities: Array<"TEXT" | "IMAGE_GENERATION">;
}

export interface Upgrade {
    autoUpgradeVersion: number;
    upgradePreview: boolean;
}

export interface FeatureLab {
    feature_resource_reference_enabled: boolean;
    feature_webhook_enabled: boolean;
    feature_personal_data_enabled: boolean;
}

export interface ContentProtector {
    content_protector_enabled: boolean;
    content_protector_license_type: string;
    content_protector_template: string;
}

export interface WebhookConfig {
    enabled: boolean;
    hasToken: boolean;
    tokenPreview?: string;
    tokenUpdatedAt?: number;
    endpoint: string;
    tokenHeader: string;
}

export interface PersonalDataPreview {
    query?: string;
    commentCount?: number;
    commentArticleCount?: number;
    latestCommentTime?: string;
    adminUserMatched?: boolean;
    adminEmailMatched?: boolean;
    pluginDataRequiresPlugin?: boolean;
}

export type WebSiteEntry =
    | Basic
    | Admin
    | Upgrade
    | Other
    | Blog
    | AI
    | ArticleEditSetting
    | FeatureLab
    | ContentProtector
    | WebhookConfig
    | PersonalDataPreview;

export type WebSiteProps = AdminCommonProps<WebSiteEntry> & {
    offline: boolean;
    offlineData: boolean;
    activeKey:
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
        | "privacy";
};

const WebSite: FunctionComponent<WebSiteProps> = ({ data, offline, offlineData, activeKey, updateCache }) => {
    const location = useLocation();
    const navigate = useNavigate();
    const screens = Grid.useBreakpoint();
    const { token } = theme.useToken();
    const compactNavigation = screens.md !== true;
    const borderSecondary = `${token.lineWidth}px ${token.lineType} ${token.colorBorderSecondary}`;
    const headerHeight = getAppState().compactMode ? 54 : 64;
    const containerHeight = `calc(100vh - ${headerHeight + 60}px)`;
    const shellHeight = compactNavigation ? undefined : containerHeight;
    const settingSelectListHeight = screens.sm ? 360 : 288;

    const layoutSurface = {
        select: {
            width: "100%",
        },
        shell: {
            height: shellHeight,
            maxHeight: shellHeight,
            minHeight: 0,
            width: "100%",
            overflow: "hidden",
        },
        shellBody: {
            display: "flex",
            flexDirection: compactNavigation ? ("column" as const) : ("row" as const),
            height: compactNavigation ? undefined : "100%",
            minHeight: 0,
            padding: 0,
            overflow: compactNavigation ? ("visible" as const) : ("hidden" as const),
        },
        compactSelect: {
            padding: token.padding,
            paddingBottom: 0,
        },
        sidebar: {
            width: 248,
            flexShrink: 0,
            background: token.colorFillQuaternary,
            borderRight: borderSecondary,
            padding: token.padding,
            minHeight: 0,
            overflow: "auto",
        },
        content: {
            flex: 1,
            minWidth: 0,
            minHeight: 0,
            background: token.colorBgContainer,
            padding: token.padding,
            paddingBottom: 0,
            overflow: compactNavigation ? "visible" : "auto",
        },
        navLabel: {
            color: token.colorTextTertiary,
            fontSize: token.fontSizeSM,
            lineHeight: "20px",
            padding: `0 ${token.paddingSM}px`,
            marginBottom: token.marginXXS,
            textAlign: "left" as const,
        },
        menuItem: {
            height: token.controlHeight,
            marginInline: 0,
            paddingLeft: token.paddingSM,
            width: "100%",
        },
        menu: {
            borderInlineEnd: "none",
            background: "transparent",
        },
        link: {
            color: token.colorText,
            display: "inline-flex",
            alignItems: "center",
            justifyContent: "flex-start",
            width: "100%",
            height: "100%",
            textAlign: "left" as const,
        },
        summary: {
            marginBottom: token.margin,
            fontSize: token.fontSizeSM,
            lineHeight: 1.7,
            color: token.colorTextSecondary,
        },
        groupDivider: {
            borderTop: borderSecondary,
            margin: `${token.marginXS}px 0`,
        },
        formContainer: {
            maxWidth: 800,
        },
    };

    const navItems: Array<{
        key: WebSiteProps["activeKey"];
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
    const activeMeta = navItems.find((item) => item.key === activeKey) || navItems[0];
    const visibleNavItems = navItems.filter((item) => {
        if (item.key === "webhook") {
            return getRes().feature_webhook_enabled === true;
        }
        if (item.key === "privacy") {
            return getRes().feature_personal_data_enabled === true;
        }
        return true;
    });

    const buildUrl = (key: string) => {
        return key === "basic" ? "/website" : "/website/" + key;
    };

    const buildLink = (key: string, text: ReactNode) => {
        const toUrl = key === "basic" ? "/website" : "/website/" + key;
        return (
            <Link to={getRealRouteUrl(toUrl)} replace={true} style={layoutSurface.link}>
                {text}
            </Link>
        );
    };

    const groupedNavItems = [
        {
            label: getRes().website.nav.site,
            options: visibleNavItems
                .filter((item) => item.group === getRes().website.nav.site)
                .map((item) => ({
                    label: (
                        <SidebarNavItem
                            icon={renderAdminDashboardRouteIcon(item.iconKey, item.key === activeKey, 16)}
                            label={item.text}
                        />
                    ),
                    value: item.key,
                    text: item.text,
                })),
        },
        {
            label: getRes().website.nav.feature,
            options: visibleNavItems
                .filter((item) => item.group === getRes().website.nav.feature)
                .map((item) => ({
                    label: (
                        <SidebarNavItem
                            icon={renderAdminDashboardRouteIcon(item.iconKey, item.key === activeKey, 16)}
                            label={item.text}
                        />
                    ),
                    value: item.key,
                    text: item.text,
                })),
        },
        {
            label: getRes().websiteLab.title,
            options: visibleNavItems
                .filter((item) => item.group === getRes().websiteLab.title)
                .map((item) => ({
                    label: (
                        <SidebarNavItem
                            icon={renderAdminDashboardRouteIcon(item.iconKey, item.key === activeKey, 16)}
                            label={item.text}
                        />
                    ),
                    value: item.key,
                    text: item.text,
                })),
        },
        {
            label: getRes().website.nav.system,
            options: visibleNavItems
                .filter((item) => item.group === getRes().website.nav.system)
                .map((item) => ({
                    label: (
                        <SidebarNavItem
                            icon={renderAdminDashboardRouteIcon(item.iconKey, item.key === activeKey, 16)}
                            label={item.text}
                        />
                    ),
                    value: item.key,
                    text: item.text,
                })),
        },
    ].filter((group) => group.options.length > 0);

    const menuGroups = groupedNavItems.map((group) => ({
        label: group.label,
        items: group.options.map((item) => ({
            key: item.value,
            label: buildLink(item.value, item.label),
            style: layoutSurface.menuItem,
        })),
    }));

    const [loading, setLoading] = useState<boolean>(false);
    const [messageApi, contextHolder] = message.useMessage({ maxCount: 3 });

    const axiosInstance = useAxiosBaseInstance();

    const onChanged = (newData: WebSiteEntry) => {
        const url = new URL(window.location.href);
        const cacheKey = getPageDataCacheKeyByPath(location.pathname, "?" + url.searchParams.toString());
        if (updateCache) {
            updateCache(newData, cacheKey);
        }
        if (activeKey === "lab") {
            const featureLab = newData as FeatureLab;
            setRes({
                ...getRes(),
                feature_personal_data_enabled: featureLab.feature_personal_data_enabled,
                feature_webhook_enabled: featureLab.feature_webhook_enabled,
            });
        }
    };

    const reloadPage = () => {
        window.location.search = getRealRouteUrl(location.pathname).split("?")[1];
        window.location.reload();
    };

    const onSubmit = async (form: WebSiteEntry): Promise<boolean> => {
        try {
            setLoading(true);
            const useRefreshCacheSse = ["basic", "blog", "admin", "other", "content-protector"].includes(activeKey);
            const data = useRefreshCacheSse
                ? await postRefreshCacheSse<any>("/api/admin/website/" + activeKey, {
                      body: { ...form },
                      messageApi,
                      messageKey: "websiteRefreshCache",
                      waitForComplete: true,
                      backgroundTaskTitle: activeMeta.text,
                  })
                : (await axiosInstance.post("/api/admin/website/" + activeKey, { ...form })).data;
            setLoading(false);
            if (data.error) {
                await messageApi.error(data.message);
                return false;
            }
            await messageApi.success(data.message);
            onChanged(data.data);
            return true;
        } catch (e) {
            setLoading(false);
            await messageApi.error((e as Error).message);
            return false;
        } finally {
            setLoading(false);
        }
    };

    const getItemBody = () => {
        const content = (() => {
            if (activeKey === "basic") {
                return (
                    <div style={layoutSurface.formContainer}>
                        <BasicForm
                            loading={loading}
                            offlineData={offlineData}
                            onSubmit={(newData) => {
                                onSubmit(newData).then((ok) => {
                                    if (ok) {
                                        reloadPage();
                                    }
                                });
                            }}
                            offline={offline}
                            data={data as Basic}
                        />
                    </div>
                );
            } else if (activeKey === "blog") {
                return (
                    <div style={layoutSurface.formContainer}>
                        <BlogForm
                            loading={loading}
                            offlineData={offlineData}
                            onSubmit={(newData) => {
                                onSubmit(newData).then((ok) => {
                                    if (ok) {
                                        reloadPage();
                                    }
                                });
                            }}
                            offline={offline}
                            data={data as Blog}
                        />
                    </div>
                );
            } else if (activeKey === "admin") {
                return (
                    <div style={layoutSurface.formContainer}>
                        <AdminForm
                            loading={loading}
                            offlineData={offlineData}
                            onSubmit={(newData) => {
                                onSubmit(newData).then((ok) => {
                                    if (ok) {
                                        reloadPage();
                                    }
                                });
                            }}
                            offline={offline}
                            data={data as Admin}
                        />
                    </div>
                );
            } else if (activeKey === "article-edit") {
                return (
                    <div style={layoutSurface.formContainer}>
                        <ArticleEditForm
                            loading={loading}
                            offlineData={offlineData}
                            onSubmit={(newData) => {
                                void onSubmit(newData);
                            }}
                            offline={offline}
                            data={data as ArticleEditSetting}
                        />
                    </div>
                );
            } else if (activeKey === "other") {
                return (
                    <div style={layoutSurface.formContainer}>
                        <OtherForm
                            loading={loading}
                            onSubmit={(newData) => {
                                onSubmit(newData).then((ok) => {
                                    if (ok) {
                                        reloadPage();
                                    }
                                });
                            }}
                            offlineData={offlineData}
                            offline={offline}
                            data={data as Other}
                        />
                    </div>
                );
            } else if (activeKey === "content-protector") {
                return (
                    <div style={layoutSurface.formContainer}>
                        <ContentProtectorForm
                            loading={loading}
                            offlineData={offlineData}
                            onSubmit={(newData) => {
                                void onSubmit(newData);
                            }}
                            offline={offline}
                            data={data as ContentProtector}
                        />
                    </div>
                );
            } else if (activeKey === "upgrade") {
                return (
                    <div style={layoutSurface.formContainer}>
                        <UpgradeSettingForm
                            loading={loading}
                            offlineData={offlineData}
                            onSubmit={(newData) => {
                                void onSubmit(newData);
                            }}
                            offline={offline}
                            data={data as Upgrade}
                        />
                    </div>
                );
            } else if (activeKey === "lab") {
                return (
                    <div style={layoutSurface.formContainer}>
                        <FeatureLabForm
                            loading={loading}
                            offlineData={offlineData}
                            onSubmit={(newData) => {
                                void onSubmit(newData);
                            }}
                            offline={offline}
                            data={data as FeatureLab}
                        />
                    </div>
                );
            } else if (activeKey === "webhook") {
                return (
                    <div style={layoutSurface.formContainer}>
                        <WebhookForm
                            loading={loading}
                            offlineData={offlineData}
                            onSubmit={(newData) => {
                                void onSubmit(newData);
                            }}
                            onConfigChange={onChanged}
                            offline={offline}
                            data={data as WebhookConfig}
                        />
                    </div>
                );
            } else if (activeKey === "privacy") {
                return (
                    <div style={layoutSurface.formContainer}>
                        <PrivacyForm offlineData={offlineData} offline={offline} data={data as PersonalDataPreview} />
                    </div>
                );
            } else if (activeKey === "ai") {
                return (
                    <div style={layoutSurface.formContainer}>
                        <AIForm
                            loading={loading}
                            offlineData={offlineData}
                            onSubmit={(newData) => {
                                void onSubmit(newData);
                            }}
                            offline={offline}
                            data={data as AI}
                        />
                    </div>
                );
            }
            return <></>;
        })();

        return (
            <>
                <div style={{ marginBottom: token.marginLG }}>
                    {!compactNavigation && (
                        <div
                            style={{
                                fontSize: token.fontSizeHeading5,
                                fontWeight: 600,
                                color: token.colorText,
                                marginBottom: token.marginXS,
                            }}
                        >
                            {activeMeta.text}
                        </div>
                    )}
                    <div style={{ ...layoutSurface.summary }}>{activeMeta.summary}</div>
                </div>
                {content}
            </>
        );
    };

    return (
        <>
            {contextHolder}
            <Card style={layoutSurface.shell} styles={{ body: layoutSurface.shellBody }}>
                {compactNavigation ? (
                    <>
                        <div style={layoutSurface.compactSelect}>
                            <Select
                                value={activeKey}
                                options={groupedNavItems}
                                listHeight={settingSelectListHeight}
                                virtual={false}
                                getPopupContainer={(triggerNode) => triggerNode.parentElement || document.body}
                                classNames={{
                                    popup: {
                                        root: "website-setting-select-popup",
                                        list: "website-setting-select-popup-list",
                                    },
                                }}
                                onChange={(key) => {
                                    navigate(getRealRouteUrl(buildUrl(key)), { replace: true });
                                }}
                                style={layoutSurface.select}
                            />
                        </div>
                        <div key={activeKey} style={layoutSurface.content}>
                            {getItemBody()}
                        </div>
                    </>
                ) : (
                    <>
                        <div style={layoutSurface.sidebar}>
                            {menuGroups.map((group, index) => (
                                <div key={group.label}>
                                    {index > 0 ? <div style={layoutSurface.groupDivider} /> : null}
                                    <div style={layoutSurface.navLabel}>{group.label}</div>
                                    <Menu
                                        selectedKeys={[activeKey]}
                                        mode="inline"
                                        inlineIndent={0}
                                        items={group.items}
                                        style={layoutSurface.menu}
                                    />
                                </div>
                            ))}
                        </div>
                        <div key={activeKey} style={layoutSurface.content}>
                            {getItemBody()}
                        </div>
                    </>
                )}
            </Card>
        </>
    );
};

export default WebSite;
