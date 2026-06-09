import { Card, Col, Row } from "antd";
import { getRealRouteUrl, getRes } from "../../utils/constants";

import { FunctionComponent, ReactNode, useEffect, useRef, useState } from "react";
import {
    AdminCommonProps,
    AdminDashboardCardConfig,
    AdminDashboardConfig,
    AdminDashboardLayoutItem,
    AdminDashboardPluginPanelConfig,
    ApiResponse,
    IndexData,
} from "../../type";
import ActivityGraph, { generateCompleteData } from "./ActivityGraph";
import StatisticsInfo from "./StatisticsInfo";
import QuickActionCard from "./QuickAction";
import DataInsights from "./DataInsights";
import AuditTrail from "./AuditTrail";
import { BarChartOutlined, InfoCircleOutlined, RightOutlined, SmileOutlined } from "@ant-design/icons";
import { Link } from "react-router-dom";
import { useAxiosBaseInstance } from "../../base/AppBase";
import PluginSurfacePanels from "./PluginSurfacePanels";
import DashboardConfigDrawer from "./DashboardConfigDrawer";
import { useLocation } from "react-router";
import { getPageDataCacheKeyByPath } from "../../utils/cache";
import LocalDraftCard from "./LocalDraftCard";
import { getLocalArticleCaches } from "../../utils/article-cache";
import { useTheme } from "antd-style";

type IndexProps = AdminCommonProps<IndexData>;

const Index: FunctionComponent<IndexProps> = ({ data, updateCache }) => {
    const sectionGap = 20;
    const axiosInstance = useAxiosBaseInstance();
    const location = useLocation();
    const theme = useTheme();

    const defaultCards: AdminDashboardCardConfig[] = [
        { id: "welcome", enabled: true, sort: 0 },
        { id: "localDraft", enabled: true, sort: 10 },
        { id: "quickAction", enabled: true, sort: 20 },
        { id: "statistics", enabled: true, sort: 30 },
        { id: "activity", enabled: true, sort: 40 },
        { id: "auditTrail", enabled: true, sort: 50 },
        { id: "dataInsights", enabled: true, sort: 60 },
    ];
    const defaultConfig: AdminDashboardConfig = {
        autoRefreshEnabled: false,
        autoRefreshIntervalSeconds: 60,
        cards: defaultCards.map((card) => ({ ...card, kind: "card" as const })),
    };
    const [pageData, setPageData] = useState<IndexData>(data);
    const [dashboardConfig, setDashboardConfig] = useState<AdminDashboardConfig>(data.dashboardConfig || defaultConfig);
    const [localDrafts, setLocalDrafts] = useState(() => getLocalArticleCaches());
    const refreshRunningRef = useRef(false);

    useEffect(() => {
        setPageData(data);
        setDashboardConfig(data.dashboardConfig || defaultConfig);
    }, [data]);

    useEffect(() => {
        const refreshLocalDraft = () => {
            setLocalDrafts(getLocalArticleCaches());
        };
        const handleVisibilityChange = () => {
            if (document.visibilityState === "visible") {
                refreshLocalDraft();
            }
        };
        window.addEventListener("focus", refreshLocalDraft);
        document.addEventListener("visibilitychange", handleVisibilityChange);
        return () => {
            window.removeEventListener("focus", refreshLocalDraft);
            document.removeEventListener("visibilitychange", handleVisibilityChange);
        };
    }, []);

    const cards = (dashboardConfig.cards || [])
        .filter((item): item is Extract<AdminDashboardLayoutItem, { kind: "card" }> => item.kind === "card")
        .map((item) => ({ id: item.id, enabled: item.enabled, sort: item.sort } as AdminDashboardCardConfig))
        .filter((card) => card.id !== "pluginPanels" && card.id !== "welcome" && card.enabled !== false)
        .sort((a, b) => (a.sort || 0) - (b.sort || 0));
    const pluginPanels = (dashboardConfig.cards || [])
        .filter((item): item is Extract<AdminDashboardLayoutItem, { kind: "plugin" }> => item.kind === "plugin")
        .map(
            (item) =>
                ({
                    id: item.id,
                    enabled: item.enabled,
                    type: item.type,
                    pluginName: item.pluginName,
                    title: item.title,
                    surfaceUrl: item.surfaceUrl,
                    actionUrl: item.actionUrl,
                    viewUrl: item.viewUrl,
                    maxItems: item.maxItems,
                    height: item.height,
                    sort: item.sort,
                    order: item.order,
                    data: item.data,
                    error: item.error,
                    surfaceLoaded: item.surfaceLoaded,
                } as AdminDashboardPluginPanelConfig)
        )
        .filter((panel) => panel.enabled !== false)
        .sort((a, b) => (a.sort ?? a.order ?? 0) - (b.sort ?? b.order ?? 0));

    const updateIndexData = (nextData: IndexData) => {
        setPageData(nextData);
        setDashboardConfig(nextData.dashboardConfig || defaultConfig);
        updateCache?.(nextData, getPageDataCacheKeyByPath(location.pathname, location.search));
    };

    useEffect(() => {
        if (dashboardConfig.autoRefreshEnabled !== true) {
            return;
        }
        let timer: number | undefined;
        let stopped = false;
        const interval = Math.max(dashboardConfig.autoRefreshIntervalSeconds || 60, 10) * 1000;
        const clearTimer = () => {
            if (timer) {
                window.clearTimeout(timer);
                timer = undefined;
            }
        };
        const refresh = async () => {
            if (stopped || document.visibilityState !== "visible" || refreshRunningRef.current) {
                return;
            }
            refreshRunningRef.current = true;
            try {
                const { data } = await axiosInstance.get<ApiResponse<IndexData>>("/api/admin/index");
                updateIndexData(data.data);
            } catch {
                // Keep the refresh loop alive; visible error surfaces should stay with explicit user actions.
            } finally {
                refreshRunningRef.current = false;
                if (!stopped && document.visibilityState === "visible") {
                    timer = window.setTimeout(refresh, interval);
                }
            }
        };
        const schedule = () => {
            clearTimer();
            if (document.visibilityState === "visible") {
                timer = window.setTimeout(refresh, interval);
            }
        };
        const handleVisibilityChange = () => {
            clearTimer();
            if (document.visibilityState === "visible") {
                refresh();
            }
        };
        document.addEventListener("visibilitychange", handleVisibilityChange);
        schedule();
        return () => {
            stopped = true;
            clearTimer();
            document.removeEventListener("visibilitychange", handleVisibilityChange);
        };
    }, [
        axiosInstance,
        dashboardConfig.autoRefreshEnabled,
        dashboardConfig.autoRefreshIntervalSeconds,
        location.pathname,
        location.search,
        updateCache,
    ]);

    if (pageData.statisticsInfo === null) {
        return <></>;
    }

    const cardRenderers: Record<string, ReactNode> = {
        welcome: (
            <Card
                className="dashboard-card"
                style={{
                    overflow: "hidden",
                    borderRadius: theme.borderRadiusLG,
                    border: `${theme.lineWidth}px ${theme.lineType} ${theme.colorBorder}`,
                }}
                title={
                    <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
                        <SmileOutlined style={{ fontSize: 18 }} />
                        <span>{pageData.welcomeTip}</span>
                    </div>
                }
                extra={
                    <div
                        style={{
                            display: "flex",
                            alignItems: "center",
                            gap: 8,
                            color: theme.colorTextSecondary,
                        }}
                    >
                        <DashboardConfigDrawer
                            axiosInstance={axiosInstance}
                            config={dashboardConfig}
                            subtle
                            onSaved={updateIndexData}
                        />
                        <Link
                            to={getRealRouteUrl("/system")}
                            style={{
                                display: "inline-flex",
                                alignItems: "center",
                                gap: 6,
                                color: theme.colorText,
                                whiteSpace: "nowrap",
                            }}
                        >
                            <span>{getRes().index.welcome.viewSystem}</span>
                            <RightOutlined />
                        </Link>
                    </div>
                }
                styles={{
                    header: {
                        background: theme.colorBgContainer,
                        color: theme.colorText,
                        borderBottom: `${theme.lineWidth}px ${theme.lineType} ${theme.colorBorder}`,
                    },
                    body: {
                        padding: "16px 20px 20px",
                        color: theme.colorTextSecondary,
                    },
                }}
            >
                <div
                    style={{
                        maxWidth: 720,
                    }}
                >
                    <div
                        style={{
                            fontSize: 15,
                            lineHeight: 1.7,
                            display: "grid",
                            gap: 6,
                            color: theme.colorText,
                        }}
                    >
                        {pageData.tips.map((e, idx) => (
                            <span key={idx} style={{ display: "block" }}>
                                {e}
                            </span>
                        ))}
                    </div>
                    <div
                        style={{
                            marginTop: 18,
                            display: "flex",
                            alignItems: "center",
                            gap: 8,
                            flexWrap: "wrap",
                            color: theme.colorTextSecondary,
                        }}
                    >
                        <InfoCircleOutlined style={{ fontSize: 14, opacity: 0.82 }} />
                        <span style={{ fontSize: 12 }}>{getRes().index.welcome.currentVersion}</span>
                        <span
                            style={{
                                fontSize: 14,
                                lineHeight: 1.25,
                                wordBreak: "break-word",
                            }}
                        >
                            {pageData.versionInfo}
                        </span>
                    </div>
                </div>
            </Card>
        ),
        localDraft: (
            <LocalDraftCard localDrafts={localDrafts} onClear={() => setLocalDrafts(getLocalArticleCaches())} />
        ),
        quickAction: <QuickActionCard draftCount={pageData.statisticsInfo.draftCount} />,
        statistics: <StatisticsInfo data={pageData.statisticsInfo} />,
        activity: (
            <Card
                title={
                    <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
                        <BarChartOutlined />
                        <span>{getRes().index.activity}</span>
                    </div>
                }
                bordered={false}
                className="dashboard-card"
                styles={{ body: { padding: 0 } }}
            >
                <div style={{ overflow: "hidden", padding: "8px 12px 10px" }}>
                    <ActivityGraph data={generateCompleteData(pageData.activityData)} />
                </div>
            </Card>
        ),
        auditTrail: <AuditTrail logs={pageData.statisticsInfo.auditLogs} />,
        dataInsights: (
            <DataInsights typeData={pageData.statisticsInfo.typeData} tagData={pageData.statisticsInfo.tagData} />
        ),
    };
    const layoutItems: (
        | { type: "card"; item: AdminDashboardCardConfig }
        | { type: "plugin"; item: AdminDashboardPluginPanelConfig }
    )[] = [
        ...cards.map((item) => ({ type: "card" as const, item })),
        ...pluginPanels.map((item) => ({ type: "plugin" as const, item })),
    ].sort((a, b) => (a.item.sort ?? 0) - (b.item.sort ?? 0));
    const sortedNodes = layoutItems
        .map((layoutItem) => {
            if (layoutItem.type === "plugin") {
                return <PluginSurfacePanels axiosInstance={axiosInstance} panels={[layoutItem.item]} />;
            }
            return cardRenderers[layoutItem.item.id];
        })
        .filter(Boolean);
    const leftNodes = sortedNodes.filter((_, index) => index % 2 === 1);
    const rightNodes = sortedNodes.filter((_, index) => index % 2 === 0);

    return (
        <>
            <Row gutter={[20, 20]} className="admin-dashboard-grid">
                <Col xs={24} lg={12}>
                    <div
                        className="admin-dashboard-column"
                        style={{ display: "flex", flexDirection: "column", gap: sectionGap }}
                    >
                        {cardRenderers.welcome}
                        {leftNodes.map((node, index) => (
                            <div
                                className="admin-dashboard-item"
                                key={index}
                                style={{ animationDelay: `${index * 35}ms` }}
                            >
                                {node}
                            </div>
                        ))}
                    </div>
                </Col>
                <Col xs={24} lg={12}>
                    <div
                        className="admin-dashboard-column"
                        style={{ display: "flex", flexDirection: "column", gap: sectionGap }}
                    >
                        {rightNodes.map((node, index) => (
                            <div
                                className="admin-dashboard-item"
                                key={index}
                                style={{ animationDelay: `${index * 35}ms` }}
                            >
                                {node}
                            </div>
                        ))}
                    </div>
                </Col>
            </Row>
        </>
    );
};

export default Index;
