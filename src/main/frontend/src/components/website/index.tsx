import { message, Tabs } from "antd";
import Row from "antd/es/grid/row";
import Col from "antd/es/grid/col";
import Index, { TemplateEntry } from "../template";
import { getRealRouteUrl, getRes } from "../../utils/constants";
import BlogForm from "./BlogForm";
import BasicForm from "./BasicForm";
import OtherForm from "./OtherForm";
import UpgradeSettingForm from "./UpgradeSettingForm";
import { Link, useLocation } from "react-router-dom";
import AdminForm from "./AdminForm";
import { FunctionComponent, useState } from "react";
import { AdminCommonProps, AIProviderType } from "../../type";
import { getPageDataCacheKeyByPath } from "../../utils/cache";
import { useAxiosBaseInstance } from "../../base/AppBase";
import { getAppState } from "../../base/ConfigProviderApp";
import AIForm from "./AIForm";
import { postRefreshCacheSse } from "../../utils/sse-utils";

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
    allProviders: AIProvider[];
}

export interface AIProvider {
    name: AIProviderType;
    models: string[];
}

export interface Upgrade {
    autoUpgradeVersion: number;
    upgradePreview: boolean;
}

export type WebSiteEntry = Basic | Admin | Upgrade | Other | Blog | TemplateEntry[] | AI;

export type WebSiteProps = AdminCommonProps<WebSiteEntry> & {
    offline: boolean;
    offlineData: boolean;
    activeKey: "basic" | "other" | "upgrade" | "admin" | "template" | "blog" | "ai";
};

const WebSite: FunctionComponent<WebSiteProps> = ({ data, offline, offlineData, activeKey, updateCache }) => {
    const location = useLocation();

    const navItems: Array<{ key: WebSiteProps["activeKey"]; text: string; summary: string }> = [
        {
            key: "basic",
            text: getRes().website.title,
            summary: getRes().website.summary,
        },
        {
            key: "blog",
            text: getRes().websiteBlog.title,
            summary: getRes().websiteBlog.summary,
        },
        {
            key: "admin",
            text: getRes().websiteAdmin.title,
            summary: getRes().websiteAdmin.summary,
        },
        {
            key: "template",
            text: getRes().websiteTemplate.title,
            summary: getRes().websiteTemplate.summary,
        },
        {
            key: "other",
            text: getRes().websiteOther.title,
            summary: getRes().websiteOther.summary,
        },
        {
            key: "ai",
            text: getRes().websiteAi.title,
            summary: getRes().websiteAi.summary,
        },
        {
            key: "upgrade",
            text: getRes().websiteUpgrade.title,
            summary: getRes().websiteUpgrade.summary,
        },
    ];
    const activeMeta = navItems.find((item) => item.key === activeKey) || navItems[0];

    const buildLink = (key: string, text: string) => {
        const toUrl = key === "basic" ? "/website" : "/website/" + key;
        return (
            <Link
                to={getRealRouteUrl(toUrl)}
                replace={true}
                style={{ color: activeKey === key ? getAppState().colorPrimary : "inherit" }}
            >
                {text}
            </Link>
        );
    };

    const [loading, setLoading] = useState<boolean>(false);
    const [messageApi, contextHolder] = message.useMessage({ maxCount: 3 });

    const axiosInstance = useAxiosBaseInstance();

    const onChanged = (newData: WebSiteEntry) => {
        const url = new URL(window.location.href);
        const cacheKey = getPageDataCacheKeyByPath(location.pathname, "?" + url.searchParams.toString());
        if (updateCache) {
            updateCache(newData, cacheKey);
        }
    };

    const reloadPage = () => {
        window.location.search = getRealRouteUrl(location.pathname).split("?")[1];
        window.location.reload();
    };

    const onSubmit = async (form: WebSiteEntry): Promise<boolean> => {
        try {
            setLoading(true);
            const useRefreshCacheSse = ["basic", "blog", "admin", "other"].includes(activeKey);
            const data = useRefreshCacheSse
                ? await postRefreshCacheSse<any>("/api/admin/website/" + activeKey, {
                      body: { ...form },
                      messageApi,
                      messageKey: "websiteRefreshCache",
                      waitForComplete: true,
                      backgroundTaskTitle: getRes().backgroundTask.title + " · " + activeMeta.text,
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
                    <Row>
                        <Col xs={24} style={{ maxWidth: 600 }}>
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
                        </Col>
                    </Row>
                );
            } else if (activeKey === "blog") {
                return (
                    <Row>
                        <Col xs={24} style={{ maxWidth: 600 }}>
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
                        </Col>
                    </Row>
                );
            } else if (activeKey === "admin") {
                return (
                    <Row>
                        <Col xs={24} style={{ maxWidth: 600 }}>
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
                        </Col>
                    </Row>
                );
            } else if (activeKey === "template") {
                return <Index data={data as TemplateEntry[]} />;
            } else if (activeKey === "other") {
                return (
                    <Row>
                        <Col xs={24} style={{ maxWidth: 600 }}>
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
                        </Col>
                    </Row>
                );
            } else if (activeKey === "upgrade") {
                return (
                    <Row>
                        <Col xs={24} style={{ maxWidth: 600 }}>
                            <UpgradeSettingForm
                                loading={loading}
                                offlineData={offlineData}
                                onSubmit={(newData) => {
                                    onSubmit(newData).then(() => {
                                        //ignore
                                    });
                                }}
                                offline={offline}
                                data={data as Upgrade}
                            />
                        </Col>
                    </Row>
                );
            } else if (activeKey === "ai") {
                return (
                    <Row>
                        <Col xs={24} style={{ maxWidth: 600 }}>
                            <AIForm
                                loading={loading}
                                offlineData={offlineData}
                                onSubmit={(newData) => {
                                    onSubmit(newData).then(() => {
                                        //ignore
                                    });
                                }}
                                offline={offline}
                                data={data as AI}
                            />
                        </Col>
                    </Row>
                );
            }
            return <></>;
        })();

        return (
            <>
                <div
                    style={{
                        marginBottom: 20,
                        fontSize: 13,
                        lineHeight: 1.7,
                        color: getAppState().dark ? "rgba(255,255,255,0.58)" : "rgba(15,23,42,0.58)",
                    }}
                >
                    {activeMeta.summary}
                </div>
                {content}
            </>
        );
    };

    return (
        <>
            {contextHolder}
            <Tabs
                activeKey={activeKey}
                items={[
                    {
                        key: "basic",
                        label: buildLink("basic", getRes().website.title),
                        children: getItemBody(),
                    },
                    {
                        key: "blog",
                        label: buildLink("blog", getRes().websiteBlog.title),
                        children: getItemBody(),
                    },
                    {
                        key: "admin",
                        label: buildLink("admin", getRes().websiteAdmin.title),
                        children: getItemBody(),
                    },
                    {
                        key: "template",
                        label: buildLink("template", getRes().websiteTemplate.title),
                        children: getItemBody(),
                    },
                    {
                        key: "other",
                        label: buildLink("other", getRes().websiteOther.title),
                        children: getItemBody(),
                    },
                    {
                        key: "ai",
                        label: buildLink("ai", getRes().websiteAi.title),
                        children: getItemBody(),
                    },
                    {
                        key: "upgrade",
                        label: buildLink("upgrade", getRes().websiteUpgrade.title),
                        children: getItemBody(),
                    },
                ]}
            />
        </>
    );
};

export default WebSite;
