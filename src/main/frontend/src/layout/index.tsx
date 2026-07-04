import { HomeOutlined } from "@ant-design/icons";
import { Alert, Col, FloatButton, Layout, Row, Tag, Typography } from "antd";

import { getRes } from "../utils/constants";
import { FunctionComponent, PropsWithChildren, useCallback, useEffect, useRef, useState } from "react";
import UserInfo from "./user-info";
import SliderMenu from "./slider";
import { ApiResponse, BasicUserInfo, MessageCenterNotice } from "../type";
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
import { getAdminNavigationGroup, getAdminNavigationGroupLabel } from "./admin-navigation-model";
import {
    getMessageCenterStatus,
    MessageCenterStatus,
    subscribeMessageCenterStatus,
} from "../utils/message-center-status";

const { Header, Content, Sider } = Layout;
const { Text } = Typography;
const MESSAGE_CENTER_REFRESH_INTERVAL = 30 * 1000;

type AdminManageLayoutProps = PropsWithChildren & {
    loading: boolean;
    fullScreen?: boolean;
    offline: boolean;
    basicUserInfo: BasicUserInfo;
    syncStaticSite: boolean;
    systemNotification: string;
    messageCenter?: MessageCenterStatus;
};

const AdminManageLayout: FunctionComponent<AdminManageLayoutProps> = ({
    offline,
    children,
    loading,
    fullScreen,
    basicUserInfo,
    syncStaticSite,
    systemNotification,
    messageCenter,
}) => {
    const screens = useBreakpoint();
    const theme = useTheme();
    const borderSecondary = `${theme.lineWidth}px ${theme.lineType} ${theme.colorBorderSecondary}`;
    const location = useLocation();
    const axiosInstance = useAxiosBaseInstance();
    const [navigationOpen, setNavigationOpen] = useState(false);
    const [messageCenterLoading, setMessageCenterLoading] = useState(false);
    const [messageCenterStatus, setMessageCenterStatus] = useState(messageCenter || getMessageCenterStatus());
    const messageCenterRefreshPendingRef = useRef(false);
    const messageCenterRequestingRef = useRef(false);
    const messageCenterRequestSeqRef = useRef(0);
    const messageCenterRevisionRef = useRef(messageCenterStatus.revision);
    const messageCenterSyncAtRef = useRef(0);
    const mobileMode = screens.xs === true;

    useEffect(() => {
        setNavigationOpen(false);
    }, [location.pathname, location.search, mobileMode]);

    const refreshMessageCenter = useCallback(
        (force = false) => {
            if (offline) {
                setMessageCenterLoading(false);
                return;
            }
            const now = Date.now();
            if (messageCenterRequestingRef.current) {
                if (force) {
                    messageCenterRefreshPendingRef.current = true;
                }
                return;
            }
            if (!force && now - messageCenterSyncAtRef.current < MESSAGE_CENTER_REFRESH_INTERVAL) {
                return;
            }
            const firstSync = messageCenterSyncAtRef.current === 0;
            const requestSeq = messageCenterRequestSeqRef.current + 1;
            messageCenterRequestSeqRef.current = requestSeq;
            messageCenterRequestingRef.current = true;
            if (firstSync) {
                setMessageCenterLoading(true);
            }
            axiosInstance
                .get<ApiResponse<MessageCenterNotice[]>>("/api/admin/message-center", { showError: false } as any)
                .then(({ data }) => {
                    if (requestSeq === messageCenterRequestSeqRef.current) {
                        syncMessageCenterNotices(data.data || []);
                        messageCenterSyncAtRef.current = Date.now();
                    }
                })
                .catch(() => undefined)
                .finally(() => {
                    if (requestSeq === messageCenterRequestSeqRef.current) {
                        messageCenterRequestingRef.current = false;
                        setMessageCenterLoading(false);
                        if (messageCenterRefreshPendingRef.current) {
                            messageCenterRefreshPendingRef.current = false;
                            refreshMessageCenter(true);
                        }
                    }
                });
        },
        [axiosInstance, offline]
    );

    useEffect(() => {
        return subscribeMessageCenterStatus((nextStatus) => {
            const revisionChanged = nextStatus.revision !== messageCenterRevisionRef.current;
            messageCenterRevisionRef.current = nextStatus.revision;
            setMessageCenterStatus(nextStatus);
            if (revisionChanged) {
                refreshMessageCenter(true);
            }
        });
    }, [refreshMessageCenter]);

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
        const navigationGroup = getAdminNavigationGroup(pathname);
        const navigationSubtitle = navigationGroup ? getAdminNavigationGroupLabel(navigationGroup) : undefined;
        if (pathname.startsWith("/article-edit")) {
            return { title: getRes().articleEdit.title, subtitle: navigationSubtitle };
        }
        if (pathname.startsWith("/article-type")) {
            return { title: getRes().articleType.title, subtitle: navigationSubtitle };
        }
        if (pathname.startsWith("/article")) {
            return { title: getRes().article.title, subtitle: navigationSubtitle };
        }
        if (pathname.startsWith("/tag")) {
            return { title: getRes().tagManage.title, subtitle: navigationSubtitle };
        }
        if (pathname.startsWith("/nav")) {
            return { title: getRes().nav.title, subtitle: navigationSubtitle };
        }
        if (pathname.startsWith("/link")) {
            return { title: getRes().link.title, subtitle: navigationSubtitle };
        }
        if (pathname.startsWith("/comment")) {
            return { title: getRes().comment.title, subtitle: navigationSubtitle };
        }
        if (pathname.startsWith("/plugin")) {
            return { title: getRes().plugin.title, subtitle: navigationSubtitle };
        }
        if (pathname.startsWith("/template-center")) {
            return { title: getRes().templateCenter.title, subtitle: navigationSubtitle };
        }
        if (pathname.startsWith("/template-config")) {
            return { title: getRes().templateConfig.title, subtitle: navigationSubtitle };
        }
        if (pathname.startsWith("/template")) {
            return { title: getRes().websiteTemplate.title, subtitle: navigationSubtitle };
        }
        if (pathname.startsWith("/system")) {
            return { title: getRes().system.info, subtitle: navigationSubtitle };
        }
        if (pathname.startsWith("/account-security") || pathname.startsWith("/user-update-password")) {
            return { title: getRes().accountSecurity.title, subtitle: navigationSubtitle };
        }
        if (pathname.startsWith("/user")) {
            return { title: getRes().user.title, subtitle: navigationSubtitle };
        }
        if (pathname.startsWith("/website/template")) {
            return { title: getRes().websiteTemplate.title, subtitle: navigationSubtitle };
        }
        if (pathname.startsWith("/website/version")) {
            return { title: getRes().websiteVersion.title, subtitle: navigationSubtitle };
        }
        if (pathname.startsWith("/upgrade")) {
            return { title: getRes().upgrade.wizard, subtitle: navigationSubtitle };
        }
        if (pathname.startsWith("/website/upgrade")) {
            return { title: getRes().websiteUpgrade.title, subtitle: navigationSubtitle };
        }
        if (pathname.startsWith("/website/ai")) {
            return { title: getRes().websiteAi.title, subtitle: navigationSubtitle };
        }
        if (pathname.startsWith("/website/webhook")) {
            return { title: getRes().websiteWebhook.title, subtitle: navigationSubtitle };
        }
        if (pathname.startsWith("/website/article-edit")) {
            return { title: getRes().websiteArticleEdit.title, subtitle: navigationSubtitle };
        }
        if (pathname.startsWith("/website/content-protector")) {
            return { title: getRes().websiteContentProtector.title, subtitle: navigationSubtitle };
        }
        if (pathname.startsWith("/website/lab")) {
            return { title: getRes().websiteLab.title, subtitle: navigationSubtitle };
        }
        if (pathname.startsWith("/website/privacy")) {
            return { title: getRes().websitePrivacy.title, subtitle: navigationSubtitle };
        }
        if (pathname.startsWith("/website/other")) {
            return { title: getRes().websiteOther.title, subtitle: navigationSubtitle };
        }
        if (pathname.startsWith("/website/blog")) {
            return { title: getRes().websiteBlog.title, subtitle: navigationSubtitle };
        }
        if (pathname.startsWith("/website/admin")) {
            return { title: getRes().websiteAdmin.title, subtitle: navigationSubtitle };
        }
        if (pathname.startsWith("/file-manager")) {
            return { title: getRes().fileManager.title, subtitle: navigationSubtitle };
        }
        if (pathname.startsWith("/website")) {
            return { title: getRes().website.title, subtitle: navigationSubtitle };
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

    const isPlugin = location.pathname.endsWith("/plugin") || location.pathname.endsWith("/plugin.html");
    const isTemplateCenter =
        location.pathname.endsWith("/template-center") || location.pathname.endsWith("/template-center.html");

    const contentPadding = (): number => {
        if (isPlugin || isTemplateCenter || fullScreen) {
            return 0;
        }
        return 12;
    };

    return (
        <PWAHandler>
            <StyledIndexLayout
                compactMode={getAppState().compactMode}
                colorPrimary={theme.colorPrimary}
                borderRadius={theme.borderRadius}
                borderRadiusLG={theme.borderRadiusLG}
                lineWidth={theme.lineWidth}
                lineType={theme.lineType}
                colorBgContainer={theme.colorBgContainer}
                colorBgElevated={theme.colorBgElevated}
                colorBorderSecondary={theme.colorBorderSecondary}
                colorError={theme.colorError}
                colorFillQuaternary={theme.colorFillQuaternary}
                colorFillSecondary={theme.colorFillSecondary}
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
                        backgroundColor: theme.colorBgElevated,
                        backdropFilter: "blur(20px) saturate(180%)",
                        WebkitBackdropFilter: "blur(20px) saturate(180%)",
                        paddingLeft: 0,
                        boxSizing: "border-box",
                        borderBottom: borderSecondary,
                    }}
                >
                    {getMainButton()}
                    <div style={{ display: "flex", alignItems: "center", gap: 12, minWidth: 0 }}>
                        <SpotlightSearch compact />
                        <MessageCenter
                            compact
                            loading={messageCenterLoading}
                            hasUnread={messageCenterStatus.hasUnread}
                            onRefresh={refreshMessageCenter}
                        />
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
                                    backgroundColor: theme.colorErrorBg,
                                    color: theme.colorError,
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
                                height: `calc(100% - ${getHeaderHeight()}px)`,
                                backgroundColor: theme.colorBgElevated,
                                backdropFilter: "blur(18px) saturate(160%)",
                                WebkitBackdropFilter: "blur(18px) saturate(160%)",
                                borderRight: borderSecondary,
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
                            width: fullScreen || mobileMode ? "100%" : `calc(100% - ${getSiderWidth()}px)`,
                            minHeight: fullScreen ? 0 : 1,
                            marginLeft: fullScreen ? 0 : mobileMode ? 0 : getSiderWidth(),
                            transition: "margin-left 0.2s cubic-bezier(0.2, 0, 0, 1)",
                        }}
                    >
                        <Layout style={{ minHeight: getMainHeight(), overflow: fullScreen ? "hidden" : "auto" }}>
                            <Content
                                style={{
                                    position: "relative",
                                    padding: contentPadding(),
                                }}
                            >
                                {loading && <MyLoadingComponent mode="delayed" />}
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
