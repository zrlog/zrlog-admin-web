import { message } from "antd";

import { getRealRouteUrl, getRes, setRes } from "../../utils/constants";
import BlogForm from "./BlogForm";
import BasicForm from "./BasicForm";
import OtherForm from "./OtherForm";
import UpgradeSettingForm from "./UpgradeSettingForm";
import { useLocation } from "react-router-dom";
import AdminForm from "./AdminForm";
import { FunctionComponent, useState } from "react";
import { AdminCommonProps, AIProviderType } from "../../type";
import { getPageDataCacheKeyByPath } from "../../utils/cache";
import { useAxiosBaseInstance } from "../../base/AppBase";
import AIForm from "./AIForm";
import { postRefreshCacheSse } from "../../utils/sse-utils";
import ArticleEditForm from "./ArticleEditForm";
import FeatureLabForm from "./FeatureLabForm";
import WebsiteSettingsLayout, { getWebsiteSettingsItems, WebsiteSettingsPage } from "../common/WebsiteSettingsLayout";
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
    backend_server_url?: string;
    language: string;
    admin_theme?: import("../../utils/constants").AdminTheme;
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
    ai_base_url?: string;
    ai_prompt: string;
    ai_max_completion_tokens?: number | null;
    ai_reasoning_enabled?: boolean;
    ai_image_provider?: string;
    ai_image_api_key?: string;
    ai_image_model?: string;
    ai_image_base_url?: string;
    hasAiApiKey?: boolean;
    hasAiImageApiKey?: boolean;
    allProviders: AIProvider[];
    allImageProviders: AIProvider[];
}

export interface AIProvider {
    name: AIProviderType;
    baseUrl?: string;
    models: string[];
    modelEntries?: AIModelEntry[];
}

export interface AIModelEntry {
    name: string;
    capabilities: Array<"TEXT" | "IMAGE_GENERATION">;
    retired?: boolean;
    retirementSource?: string;
}

export interface Upgrade {
    autoUpgradeVersion: number;
    upgradePreview: boolean;
}

export interface FeatureLab {
    feature_resource_reference_enabled: boolean;
    feature_article_extension_filter_enabled: boolean;
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
    activeKey: Exclude<WebsiteSettingsPage, "members">;
};

const WebSite: FunctionComponent<WebSiteProps> = ({ data, offline, offlineData, activeKey, updateCache }) => {
    const location = useLocation();
    const activeMeta = getWebsiteSettingsItems().find((item) => item.key === activeKey)!;
    const layoutSurface = { formContainer: { maxWidth: 800 } };

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

        return content;
    };

    return (
        <>
            {contextHolder}
            <WebsiteSettingsLayout activeKey={activeKey}>{getItemBody()}</WebsiteSettingsLayout>
        </>
    );
};

export default WebSite;
