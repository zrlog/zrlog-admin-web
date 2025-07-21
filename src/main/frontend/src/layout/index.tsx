import { HomeOutlined } from "@ant-design/icons";
import { Alert, Col, FloatButton, Layout, Row, Tag, Typography } from "antd";

import { getRes } from "../utils/constants";
import { FunctionComponent, PropsWithChildren, useEffect, useState } from "react";
import UserInfo from "./user-info";
import SliderMenu from "./slider";
import { BasicUserInfo } from "../type";
import MyLoadingComponent from "../components/my-loading-component";
import PWAHandler from "../base/PWAHandler";
import StyledIndexLayout from "./styled-index-layout";
import useBreakpoint from "antd/es/grid/hooks/useBreakpoint";
import StaticSite from "../components/StaticSite";
import { useAxiosBaseInstance } from "../base/AppBase";
import { getAppState } from "../base/ConfigProviderApp";
import { useTheme } from "antd-style";
import SpotlightSearch from "./spotlight-search";
import { useLocation } from "react-router-dom";
import MessageCenter from "../components/message-center";
import NavigationTriggerButton from "./navigation-trigger-button";
import AdminNavigationDrawer from "./admin-navigation-drawer";
import { syncMessageCenterNotices } from "../utils/message-center-notice-sync";
import { ApiResponse, MessageCenterNotice } from "../type";

const { Header, Content, Sider } = Layout;
const { Text } = Typography;

type AdminManageLayoutProps = PropsWithChildren & {
    loading: boolean;
    fullScreen?: boolean;
    offline: boolean;
    basicUserInfo: BasicUserInfo;
    syncStaticSite: boolean;
    systemNotification: string;
};

const AdminManageLayout: FunctionComponent<AdminManageLayoutProps> = ({
    offline,
    children,
    loading,
    fullScreen,
    basicUserInfo,
    syncStaticSite,
    systemNotification,
}) => {
    const screens = useBreakpoint();
    const theme = useTheme();
    const location = useLocation();
    const axiosInstance = useAxiosBaseInstance();
    const [navigationOpen, setNavigationOpen] = useState(false);
    const [messageCenterLoading, setMessageCenterLoading] = useState(true);
    const mobileMode = screens.xs === true;

    useEffect(() => {
        setNavigationOpen(false);
    }, [location.pathname, location.search, mobileMode]);

    useEffect(() => {
        let active = true;
        axiosInstance
            .get("/api/admin/message-center")
            .then(({ data }: { data: ApiResponse<MessageCenterNotice[]> }) => {
                if (active) {
                    syncMessageCenterNotices(data.data || []);
                }
            })
            .catch(() => undefined)
            .finally(() => {
                if (active) {
                    setMessageCenterLoading(false);
                }
            });
        return () => {
            active = false;
        };
    }, []);

    if (screens.xs === undefined) {
        return <></>;
    }

    const getMainHeight = () => {
        return `calc(100vh - ${getHeaderHeight()}px)`;
    };

    const getHeaderHeight = () => {
        return getAppState().compactMode ? 54 : 64;
    };

    const getSiderWidth = () => {
        return getAppState().compactMode ? 72 : 80;
    };

    const getPanelWidth = () => {
        return getAppState().compactMode ? 228 : 248;
    };

    const toggleNavigation = () => {
        setNavigationOpen((open) => !open);
    };

    const getHeaderMeta = () => {
        const pathname = location.pathname.split(".")[0];

        if (pathname === "" || pathname === "/" || pathname === "/index") {
            return { title: getRes().index.title, subtitle: getRes().common.management };
        }
        if (pathname.startsWith("/article-edit")) {
            return { title: getRes().articleEdit.title, subtitle: getRes().article.title };
        }
        if (pathname.startsWith("/article")) {
            return {
                title: getRes().article.title,
                subtitle: getRes().common.management,
            };
        }
        if (pathname.startsWith("/article-type")) {
            return { title: getRes().articleType.title, subtitle: getRes().article.title };
        }
        if (pathname.startsWith("/nav")) {
            return { title: getRes().nav.title, subtitle: getRes().common.management };
        }
        if (pathname.startsWith("/link")) {
            return { title: getRes().link.title, subtitle: getRes().common.management };
        }
        if (pathname.startsWith("/comment")) {
            return { title: getRes().comment.title, subtitle: getRes().common.management };
        }
        if (pathname.startsWith("/plugin")) {
            return { title: getRes().plugin.title, subtitle: getRes().common.settings };
        }
        if (pathname.startsWith("/template-center")) {
            return { title: getRes().templateCenter.title, subtitle: getRes().common.settings };
        }
        if (pathname.startsWith("/system")) {
            return { title: getRes().system.info, subtitle: getRes().common.settings };
        }
        if (pathname.startsWith("/account-security") || pathname.startsWith("/user-update-password")) {
            return { title: getRes().accountSecurity.title, subtitle: getRes().user.title };
        }
        if (pathname.startsWith("/user")) {
            return { title: getRes().user.title, subtitle: getRes().common.settings };
        }
        if (pathname.startsWith("/website/version")) {
            return { title: getRes().websiteVersion.title, subtitle: getRes().common.settings };
        }
        if (pathname.startsWith("/upgrade")) {
            return { title: getRes().upgrade.wizard, subtitle: getRes().common.settings };
        }
        if (pathname.startsWith("/website/template")) {
            return { title: getRes().websiteTemplate.title, subtitle: getRes().common.settings };
        }
        if (pathname.startsWith("/website/upgrade")) {
            return { title: getRes().websiteUpgrade.title, subtitle: getRes().common.settings };
        }
        if (pathname.startsWith("/website/ai")) {
            return { title: getRes().websiteAi.title, subtitle: getRes().common.settings };
        }
        if (pathname.startsWith("/website/other")) {
            return { title: getRes().websiteOther.title, subtitle: getRes().common.settings };
        }
        if (pathname.startsWith("/website/blog")) {
            return { title: getRes().websiteBlog.title, subtitle: getRes().common.settings };
        }
        if (pathname.startsWith("/website/admin")) {
            return { title: getRes().websiteAdmin.title, subtitle: getRes().common.settings };
        }
        if (pathname.startsWith("/website")) {
            return { title: getRes().common.settings, subtitle: getRes().common.management };
        }
        return { title: getRes().common.management, subtitle: getRes().websiteTitle };
    };

    const headerMeta = getHeaderMeta();

    const getSidebarBrand = (showLabel: boolean, extraClassName?: string) => {
        return (
            <a
                href={getRes().homeUrl + "?spm=admin&buildId=" + getRes().buildId}
                className={`sidebar-brand ${showLabel ? "sidebar-brand-expanded" : "sidebar-brand-collapsed"}${
                    extraClassName ? ` ${extraClassName}` : ""
                }`}
                target="_blank"
                title={getRes().websiteTitle}
                rel="noopener noreferrer"
            >
                <span className="sidebar-brand-mark">
                    <HomeOutlined />
                </span>
                {showLabel && (
                    <span className="sidebar-brand-copy">
                        <span className="sidebar-brand-title">{getRes().websiteTitle}</span>
                    </span>
                )}
            </a>
        );
    };

    const getMainButton = () => {
        return (
            <div style={{ display: "flex", alignItems: "center", justifyContent: "flex-start", minWidth: 0 }}>
                <NavigationTriggerButton
                    active={drawerOpen}
                    dark={getAppState().dark}
                    siderWidth={getSiderWidth()}
                    onClick={toggleNavigation}
                />
                <div className="header-title-block">
                    {headerMeta.subtitle ? (
                        <Text type="secondary" className="header-title-eyebrow">
                            {headerMeta.subtitle}
                        </Text>
                    ) : null}
                    <Text strong className="header-title-main" ellipsis>
                        {headerMeta.title}
                    </Text>
                </div>
            </div>
        );
    };

    const drawerOpen = !fullScreen && navigationOpen;

    const closeDrawer = () => {
        setNavigationOpen(false);
    };

    return (
        <PWAHandler>
            <StyledIndexLayout
                compactMode={getAppState().compactMode}
                colorPrimary={getAppState().colorPrimary}
                borderRadius={theme.borderRadius}
                borderRadiusLG={theme.borderRadiusLG}
                textColor={theme.colorText}
                textSecondaryColor={theme.colorTextSecondary}
                textTertiaryColor={theme.colorTextTertiary}
            >
                {systemNotification && systemNotification.length > 0 && (
                    <Alert
                        showIcon={true}
                        banner={true}
                        type={"info"}
                        title={systemNotification}
                        style={{
                            position: "fixed",
                            zIndex: theme.zIndexPopupBase,
                            top: 38,
                            borderRadius: theme.borderRadius,
                            left: "50%",
                            transform: "translateX(-50%)",
                            width: "fit-content",
                        }}
                    />
                )}
                <Header
                    style={{
                        height: getHeaderHeight(),
                        display: fullScreen ? "none" : "flex",
                        justifyContent: "space-between",
                        alignItems: "center",
                        backgroundColor: getAppState().dark ? "rgba(18, 18, 18, 0.82)" : "rgba(255, 255, 255, 0.82)",
                        backdropFilter: "blur(20px) saturate(180%)",
                        WebkitBackdropFilter: "blur(20px) saturate(180%)",
                        paddingLeft: 0,
                        boxSizing: "border-box",
                        borderBottom: getAppState().dark
                            ? "1px solid rgba(255,255,255,0.06)"
                            : "1px solid rgba(15,23,42,0.06)",
                    }}
                >
                    {getMainButton()}
                    <div style={{ display: "flex", alignItems: "center", gap: 12, minWidth: 0 }}>
                        <SpotlightSearch compact />
                        <MessageCenter compact loading={messageCenterLoading} />
                        {offline && (
                            <Tag
                                bordered={false}
                                style={{
                                    marginInlineEnd: 0,
                                    borderRadius: theme.borderRadiusLG,
                                    paddingInline: 10,
                                    height: 30,
                                    lineHeight: "30px",
                                    fontSize: 12,
                                    fontWeight: 600,
                                    backgroundColor: getAppState().dark
                                        ? "rgba(255, 120, 117, 0.16)"
                                        : "rgba(255, 77, 79, 0.10)",
                                    color: getAppState().dark ? "#ffb3b0" : "#cf1322",
                                }}
                            >
                                {getRes().offline.short}
                            </Tag>
                        )}
                        <UserInfo offline={offline} data={basicUserInfo} />
                    </div>
                </Header>

                <Row
                    style={{
                        minHeight: getMainHeight(),
                    }}
                >
                    {!mobileMode && !fullScreen && (
                        <Sider
                            width={getSiderWidth()}
                            style={{
                                position: "absolute",
                                zIndex: 1000,
                                top: getHeaderHeight(),
                                left: 0,
                                height: getMainHeight(),
                                backgroundColor: getAppState().dark
                                    ? "rgba(26, 26, 26, 0.86)"
                                    : "rgba(255, 255, 255, 0.88)",
                                backdropFilter: "blur(18px) saturate(160%)",
                                WebkitBackdropFilter: "blur(18px) saturate(160%)",
                                borderRight: getAppState().dark
                                    ? "1px solid rgba(255,255,255,0.08)"
                                    : "1px solid rgba(15,23,42,0.08)",
                                overflow: "hidden",
                            }}
                        >
                            <div className="sidebar-shell">
                                {getSidebarBrand(false)}
                                <SliderMenu expanded={false} />
                            </div>
                        </Sider>
                    )}

                    <Col
                        style={{
                            flex: 1,
                            width: "100%",
                            minHeight: fullScreen ? 0 : 1,
                            marginLeft: fullScreen ? 0 : mobileMode ? 0 : getSiderWidth(),
                            transition: "margin-left 0.2s cubic-bezier(0.2, 0, 0, 1)",
                        }}
                    >
                        <Layout style={{ minHeight: getMainHeight(), overflow: fullScreen ? "hidden" : "auto" }}>
                            <Content
                                style={{
                                    position: "relative",
                                    paddingTop: fullScreen ? 0 : getAppState().compactMode ? 16 : 20,
                                    paddingRight: fullScreen ? 0 : 12,
                                    paddingLeft: fullScreen ? 0 : 12,
                                    paddingBottom: fullScreen ? 0 : 12,
                                }}
                            >
                                {loading && <MyLoadingComponent />}
                                {children}
                            </Content>
                        </Layout>
                    </Col>
                </Row>
                <AdminNavigationDrawer
                    open={drawerOpen}
                    width={getPanelWidth()}
                    dark={getAppState().dark}
                    closeLabel={getRes().common.close}
                    brand={getSidebarBrand(true, "sidebar-brand-drawer")}
                    onClose={closeDrawer}
                >
                    <SliderMenu expanded={true} />
                </AdminNavigationDrawer>

                <FloatButton.Group>
                    {syncStaticSite && <StaticSite data={{ synced: false }} offlineData={false} offline={offline} />}
                    <FloatButton.BackTop />
                </FloatButton.Group>
            </StyledIndexLayout>
        </PWAHandler>
    );
};

export default AdminManageLayout;
