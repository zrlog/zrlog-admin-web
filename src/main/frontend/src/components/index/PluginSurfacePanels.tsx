import { App, Card, Empty } from "antd";
import { AxiosInstance } from "axios";
import { FunctionComponent, useMemo } from "react";
import { useNavigate } from "react-router-dom";
import { InteractiveSurfaceView } from "../../common/plugin-surface";
import { InteractiveSurface } from "../../common/plugin-surface/types";
import PluginFrame from "../../common/PluginFrame";
import { getBackendServerUrl, getRealRouteUrl, getRes } from "../../utils/constants";
import { AdminDashboardPluginPanelConfig } from "../../type";

const normalizePath = (path: string) => path.replace(/^\/+/, "");

const isAbsoluteHttpUrl = (url?: string) => !!url && /^https?:\/\//.test(url);

const pluginViewPath = (panel: AdminDashboardPluginPanelConfig, fallback?: string) => {
    if (!panel.pluginName) {
        return panel.viewUrl || fallback || "";
    }
    return normalizePath(`${panel.pluginName}/${panel.viewUrl || fallback || "index"}`);
};

const pluginViewUrl = (panel: AdminDashboardPluginPanelConfig) => {
    if (isAbsoluteHttpUrl(panel.viewUrl)) {
        return panel.viewUrl || "";
    }
    if (!panel.pluginName) {
        return panel.viewUrl || "";
    }
    return getBackendServerUrl() + "admin/plugins/" + pluginViewPath(panel);
};

const enablePanelRouteBridge = (panel: AdminDashboardPluginPanelConfig) => {
    return !!panel.pluginName && !isAbsoluteHttpUrl(panel.viewUrl);
};

const sortValue = (panel: AdminDashboardPluginPanelConfig) => panel.sort ?? panel.order ?? 0;

const surfaceLoadStatus = (panel: AdminDashboardPluginPanelConfig) => {
    if (panel.surfaceLoaded === true) {
        return "success" as const;
    }
    if (panel.surfaceLoaded === false) {
        return "error" as const;
    }
    return undefined;
};

const PluginSurfacePanels: FunctionComponent<{
    axiosInstance: AxiosInstance;
    panels: AdminDashboardPluginPanelConfig[];
}> = ({ axiosInstance, panels }) => {
    const { message } = App.useApp();
    const navigate = useNavigate();
    const res = getRes().index.pluginPanels;

    const enabledPanels = useMemo(
        () => panels.filter((panel) => panel.enabled !== false).sort((a, b) => sortValue(a) - sortValue(b)),
        [panels]
    );

    const openPluginView = (panel: AdminDashboardPluginPanelConfig, view: string, url?: string) => {
        if (isAbsoluteHttpUrl(panel.viewUrl)) {
            window.open(panel.viewUrl, "_blank", "noopener,noreferrer");
            return;
        }
        if (!panel.pluginName) {
            if (isAbsoluteHttpUrl(url)) {
                window.open(url, "_blank", "noopener,noreferrer");
            } else {
                message.warning(res.missingPluginName);
            }
            return;
        }
        const page = pluginViewPath(panel, url || view);
        const query = new URLSearchParams();
        query.set("page", page);
        navigate(getRealRouteUrl(`/plugin?${query.toString()}`));
    };

    if (enabledPanels.length === 0) {
        return null;
    }

    return (
        <>
            {enabledPanels.map((panel) => (
                <Card
                    key={panel.id || panel.surfaceUrl}
                    title={
                        panel.type === "view"
                            ? (panel.data as { title?: string } | undefined)?.title || panel.title
                            : undefined
                    }
                    bordered={false}
                    className="dashboard-card"
                    style={{
                        overflow: "hidden",
                    }}
                    styles={{ body: { padding: panel.type === "view" ? 0 : 20 } }}
                >
                    {panel.type === "view" ? (
                        panel.pluginName || panel.viewUrl ? (
                            <PluginFrame
                                title={
                                    panel.title ||
                                    panel.pluginName ||
                                    panel.viewUrl ||
                                    panel.id ||
                                    getRes().plugin.title
                                }
                                height={panel.height || 360}
                                src={pluginViewUrl(panel)}
                                enableAdminRouteBridge={enablePanelRouteBridge(panel)}
                            />
                        ) : (
                            <Empty description={res.empty} image={Empty.PRESENTED_IMAGE_SIMPLE} />
                        )
                    ) : panel.surfaceUrl && panel.actionUrl ? (
                        <InteractiveSurfaceView
                            surfaceUrl={panel.surfaceUrl}
                            actionUrl={panel.actionUrl}
                            axiosInstance={axiosInstance}
                            title={(panel.data as { title?: string } | undefined)?.title || panel.title}
                            maxItems={panel.maxItems}
                            initialSurface={panel.data as InteractiveSurface | undefined}
                            initialLoadStatus={surfaceLoadStatus(panel)}
                            initialError={panel.error}
                            loadOnMount={false}
                            notifyLoadError={false}
                            showRefresh={false}
                            onOpenView={(view, url) => openPluginView(panel, view, url)}
                        />
                    ) : (
                        <Empty description={res.empty} image={Empty.PRESENTED_IMAGE_SIMPLE} />
                    )}
                </Card>
            ))}
        </>
    );
};

export default PluginSurfacePanels;
